package com.sapio.classroom;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.sapio.FakeAi;
import com.sapio.IntegrationTest;
import com.sapio.TestUsers;
import com.sapio.TestUsers.Registered;
import com.sapio.document.DocumentFlowIT;
import com.sapio.document.extraction.ExtractionGateway;
import com.sapio.knowledge.ai.KnowledgeAi;
import com.sapio.study.ai.StudyAi;
import com.sapio.study.question.QuestionType;
import com.sapio.study.question.Verdict;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The teacher pipeline end to end: roster, sharing, class tests, results, insights, notices and the calendar. */
@IntegrationTest
class ClassroomFlowIT {

    @Autowired
    MockMvc mvc;

    @Autowired
    JsonMapper json;

    @MockitoBean
    ExtractionGateway extraction;

    @MockitoBean
    StudyAi ai;

    @MockitoBean
    KnowledgeAi knowledgeAi;

    Registered teacher;
    Registered student;
    UUID documentId;

    @BeforeEach
    void classWithADocument() throws Exception {
        when(extraction.extract(any())).thenReturn(FakeAi.extraction());
        FakeAi.stub(knowledgeAi, ai);
        when(ai.assessmentFeedback(any())).thenReturn(FakeAi.narrative());
        when(ai.grade(any())).thenReturn(FakeAi.grade(Verdict.INCORRECT, 0.0));
        when(ai.generateQuestions(any())).thenAnswer(inv -> {
            StudyAi.QuestionSpec spec = inv.getArgument(0);
            if (spec == null) {
                return null;
            }
            String first = spec.topics().getFirst().key();
            String last = spec.topics().getLast().key();
            return new StudyAi.QuestionsDraft(List.of(
                    new StudyAi.QuestionDraft(first, QuestionType.MULTIPLE_CHOICE, "Which way does water move?",
                            List.of("to concentrated", "to dilute", "nowhere", "both"), 0, "to concentrated", "Osmosis",
                            List.of()),
                    new StudyAi.QuestionDraft(last, QuestionType.TRUE_FALSE, "Respiration makes ATP.",
                            List.of("True", "False"), 0, "True", "It does", List.of()),
                    new StudyAi.QuestionDraft(last, QuestionType.SHORT_ANSWER, "Why do cells need ATP?", List.of(),
                            null, "To power reactions", "Energy currency", List.of())), "fake-model");
        });
        teacher = TestUsers.register(mvc, json, "TEACHER", "Ms Rivera");
        student = TestUsers.register(mvc, json, "STUDENT", "Asha Student");
        documentId = DocumentFlowIT.upload(mvc, json, teacher, "bio.png", DocumentFlowIT.PNG_BYTES);
        assertThat(DocumentFlowIT.awaitTerminal(mvc, json, teacher, documentId).get("status").asString()).isEqualTo("READY");
    }

    @Test
    void invitationsAreAnsweredByTheStudent() throws Exception {
        JsonNode results = call(teacher, post("/api/classroom/students/invite"),
                Map.of("emails", List.of(student.email(), "not-an-email", teacher.email()))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString().transform(json::readTree);
        assertThat(results.findValuesAsString("outcome")).containsExactly("INVITED", "INVALID_EMAIL", "YOURSELF");

        // Only teachers manage a class.
        call(student, post("/api/classroom/students/invite"), Map.of("emails", List.of("x@example.com")))
                .andExpect(status().isForbidden());
        // Before accepting, the teacher cannot share with or look at the student.
        assertThat(getJson(teacher, "/api/classroom/students").at("/0/status").asString()).isEqualTo("PENDING");

        JsonNode inbox = getJson(student, "/api/notifications");
        assertThat(inbox.get("unread").asInt()).isEqualTo(1);
        assertThat(inbox.at("/items/0/type").asString()).isEqualTo("CLASS_INVITE");
        String invitation = getJson(student, "/api/classroom/invitations").at("/0/id").asString();
        call(student, post("/api/classroom/invitations/" + invitation + "/accept"), null).andExpect(status().isNoContent());

        assertThat(getJson(teacher, "/api/classroom/students").at("/0/status").asString()).isEqualTo("ACCEPTED");
        assertThat(getJson(teacher, "/api/classroom/students").at("/0/name").asString()).isEqualTo("Asha Student");
        assertThat(getJson(teacher, "/api/notifications").at("/items/0/type").asString()).isEqualTo("INVITE_ANSWERED");
        assertThat(getJson(student, "/api/classroom/teachers").at("/0/name").asString()).isEqualTo("Ms Rivera");
    }

    @Test
    void sharedDocumentsLandInTheStudentsLibraryAndReportBack() throws Exception {
        UUID studentId = join();
        JsonNode shared = call(teacher, post("/api/classroom/documents/" + documentId + "/share"), Map.of())
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString().transform(json::readTree);
        assertThat(shared.get("shared").asInt()).isEqualTo(1);
        // Sharing again changes nothing.
        call(teacher, post("/api/classroom/documents/" + documentId + "/share"), Map.of())
                .andExpect(jsonPath("$.alreadyHad").value(1));

        JsonNode library = getJson(student, "/api/documents");
        String copy = library.findValuesAsString("id").getFirst();
        assertThat(library.toString()).contains("\"kind\":\"CLASS\"").contains("Ms Rivera");
        assertThat(getJson(teacher, "/api/classroom/documents/stats").at("/0/opened").asInt()).isZero();

        call(student, post("/api/classroom/opened/" + copy), null).andExpect(status().isNoContent());
        JsonNode stats = getJson(teacher, "/api/classroom/documents/stats").get(0);
        assertThat(stats.get("sharedWith").asInt()).isEqualTo(1);
        assertThat(stats.get("opened").asInt()).isEqualTo(1);

        JsonNode progress = getJson(teacher, "/api/classroom/documents/" + documentId + "/students").get(0);
        assertThat(progress.get("studentId").asString()).isEqualTo(studentId.toString());
        assertThat(progress.get("topicCount").asInt()).isEqualTo(2);
        assertThat(progress.get("firstOpenedAt").isNull()).isFalse();
    }

    @Test
    void classTestsAreReviewedSentTakenAndReported() throws Exception {
        UUID studentId = join();
        JsonNode test = call(teacher, post("/api/classroom/documents/" + documentId + "/tests"),
                Map.of("title", "Unit 1 quiz", "format", "MIXED", "difficulty", "MIXED", "questionCount", 5))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString().transform(json::readTree);
        String testId = test.at("/summary/id").asString();
        assertThat(test.at("/summary/status").asString()).isEqualTo("DRAFT");
        assertThat(test.get("questions").size()).isEqualTo(3);

        // The teacher fixes a question, removes one and writes their own.
        String first = test.at("/questions/0/id").asString();
        call(teacher, put("/api/classroom/tests/" + testId + "/questions/" + first), Map.of("topicPosition", 1,
                "type", "MULTIPLE_CHOICE", "prompt", "Where does water move in osmosis?",
                "options", List.of("to the concentrated side", "to the dilute side"), "correctOption", 0))
                .andExpect(jsonPath("$.questions[0].prompt").value("Where does water move in osmosis?"));
        call(teacher, put("/api/classroom/tests/" + testId + "/questions/" + first), Map.of("topicPosition", 1,
                "type", "MULTIPLE_CHOICE", "prompt", "x", "options", List.of("a", "b"), "correctOption", 5))
                .andExpect(status().isBadRequest());
        String third = test.at("/questions/2/id").asString();
        call(teacher, delete("/api/classroom/tests/" + testId + "/questions/" + third), null)
                .andExpect(jsonPath("$.questions.length()").value(2));
        call(teacher, post("/api/classroom/tests/" + testId + "/questions"), Map.of("topicPosition", 2,
                "type", "SHORT_ANSWER", "prompt", "Name the energy currency of the cell.", "referenceAnswer", "ATP"))
                .andExpect(jsonPath("$.questions[2].position").value(3));

        Instant due = Instant.now().plus(3, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        call(teacher, post("/api/classroom/tests/" + testId + "/send"), Map.of("dueAt", due.toString()))
                .andExpect(jsonPath("$.sent").value(1));
        // Sent: its questions are fixed now.
        call(teacher, delete("/api/classroom/tests/" + testId + "/questions/" + first), null)
                .andExpect(status().isConflict());

        // The student got the document, a notification and the test, which they take like any other.
        JsonNode inbox = getJson(student, "/api/notifications");
        assertThat(inbox.findValuesAsString("type")).contains("DOCUMENT_SHARED", "TEST_ASSIGNED");
        // The due date reads naturally and points to the calendar.
        assertThat(inbox.toString()).contains("3 questions, due ").contains("It is in your calendar.");
        String assessmentId = inbox.get("items").findValues("link").stream().map(JsonNode::asString)
                .filter(l -> l.startsWith("/assessments/")).findFirst().orElseThrow().substring("/assessments/".length());
        JsonNode assessment = getJson(student, "/api/assessments/" + assessmentId);
        assertThat(assessment.at("/assignment/assignedBy").asString()).isEqualTo("Ms Rivera");
        assertThat(assessment.at("/assignment/title").asString()).isEqualTo("Unit 1 quiz");
        assertThat(assessment.at("/items/0/question/prompt").asString()).isEqualTo("Where does water move in osmosis?");
        assertThat(getJson(student, "/api/classroom/calendar?from=" + Instant.now() + "&to="
                + Instant.now().plus(7, ChronoUnit.DAYS)).at("/0/kind").asString()).isEqualTo("TEST_DUE");

        assertThat(getJson(teacher, "/api/classroom/tests/" + testId + "/results").at("/students/0/status").asString())
                .isEqualTo("NOT_STARTED");
        // Every answer wrong (the AI drafts in shuffled order, so answer by type): option 1 is never the right one.
        for (JsonNode item : assessment.get("items")) {
            Map<String, Object> answer = item.at("/question/type").asString().equals("SHORT_ANSWER")
                    ? Map.of("text", "No idea") : Map.of("optionIndex", 1);
            call(student, post("/api/assessments/" + assessmentId + "/items/" + item.get("position").asInt() + "/answer"),
                    answer).andExpect(status().isOk());
        }
        call(student, post("/api/assessments/" + assessmentId + "/complete"), null).andExpect(status().isOk());

        JsonNode results = getJson(teacher, "/api/classroom/tests/" + testId + "/results");
        assertThat(results.at("/students/0/status").asString()).isEqualTo("COMPLETED");
        assertThat(results.at("/students/0/score").asDouble()).isZero();
        assertThat(results.get("topics").size()).isEqualTo(2);
        // The teacher reads the student's report; another teacher cannot.
        assertThat(getJson(teacher, "/api/classroom/assessments/" + assessmentId).at("/report/topics").size())
                .isEqualTo(2);
        Registered other = TestUsers.register(mvc, json, "TEACHER", "Mr Other");
        mvc.perform(get("/api/classroom/assessments/" + assessmentId).header("Authorization", other.bearer()))
                .andExpect(status().isNotFound());

        // Insights: the profile names what to work on; home shows the class figures.
        JsonNode profile = getJson(teacher, "/api/classroom/students/" + studentId);
        assertThat(profile.get("testsCompleted").asInt()).isEqualTo(1);
        assertThat(profile.get("needsWork").toString()).contains("Energy");
        JsonNode overview = getJson(teacher, "/api/classroom/overview");
        assertThat(overview.get("students").asInt()).isEqualTo(1);
        assertThat(overview.get("testsSent").asInt()).isEqualTo(1);
        assertThat(overview.get("testsCompleted").asInt()).isEqualTo(1);
        assertThat(overview.at("/needsAttention/0/name").asString()).isEqualTo("Asha Student");
    }

    @Test
    void aStudentsOwnWorkStaysPrivate() throws Exception {
        UUID studentId = join();
        // The student's own upload, with a test on it: not the teacher's business.
        UUID own = DocumentFlowIT.upload(mvc, json, student, "mine.png", DocumentFlowIT.PNG_BYTES);
        assertThat(DocumentFlowIT.awaitTerminal(mvc, json, student, own).get("status").asString()).isEqualTo("READY");
        String privateTest = createTest(own.toString());

        // On the document the teacher shared, the student also tests themselves (and gets everything wrong): still
        // theirs. Neither the test, nor its result, nor its answers' effect on mastery reach the teacher.
        call(teacher, post("/api/classroom/documents/" + documentId + "/share"), Map.of()).andExpect(status().isOk());
        String copy = getJson(student, "/api/documents").findValuesAsString("id").stream()
                .filter(id -> !id.equals(own.toString())).findFirst().orElseThrow();
        String selfTest = createTest(copy);
        for (JsonNode item : getJson(student, "/api/assessments/" + selfTest).get("items")) {
            Map<String, Object> answer = item.at("/question/type").asString().equals("SHORT_ANSWER")
                    ? Map.of("text", "No idea") : Map.of("optionIndex", 1);
            call(student, post("/api/assessments/" + selfTest + "/items/" + item.get("position").asInt() + "/answer"),
                    answer).andExpect(status().isOk());
        }
        call(student, post("/api/assessments/" + selfTest + "/complete"), null).andExpect(status().isOk());

        JsonNode profile = getJson(teacher, "/api/classroom/students/" + studentId);
        assertThat(profile.get("documents").size()).isEqualTo(1);
        assertThat(profile.at("/documents/0/tests").size()).isZero();
        assertThat(profile.at("/documents/0/averageMastery").isNull()).isTrue();
        assertThat(profile.get("testsCompleted").asInt()).isZero();
        assertThat(profile.get("needsWork").size()).isZero();
        assertThat(profile.toString()).doesNotContain(own.toString()).doesNotContain(privateTest).doesNotContain(selfTest);
        for (String test : List.of(privateTest, selfTest)) {
            mvc.perform(get("/api/classroom/assessments/" + test).header("Authorization", teacher.bearer()))
                    .andExpect(status().isNotFound());
        }
        JsonNode stats = getJson(teacher, "/api/classroom/documents/stats");
        assertThat(stats.size()).isEqualTo(1);
        assertThat(stats.at("/0/testsCompleted").asInt()).isZero();
        assertThat(getJson(teacher, "/api/classroom/overview").get("testsCompleted").asInt()).isZero();
        assertThat(getJson(teacher, "/api/classroom/documents/" + documentId + "/students").at("/0/tests").size()).isZero();
    }

    private String createTest(String documentId) throws Exception {
        return call(student, post("/api/documents/" + documentId + "/assessments"),
                Map.of("questionCount", 3, "difficulty", "MIXED")).andExpect(status().isCreated()).andReturn()
                .getResponse().getContentAsString().transform(json::readTree).get("id").asString();
    }

    @Test
    void noticesAndEventsReachTheClass() throws Exception {
        join();
        call(teacher, post("/api/classroom/notices"), Map.of("title", "Lab moved", "body", "Room 4 on Friday."))
                .andExpect(status().isOk());
        assertThat(getJson(student, "/api/classroom/notices").at("/0/title").asString()).isEqualTo("Lab moved");

        Instant start = Instant.now().plus(2, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        call(teacher, post("/api/classroom/events"), Map.of("title", "Field trip", "startsAt", start.toString(),
                "allDay", true)).andExpect(jsonPath("$.kind").value("EVENT"));
        JsonNode calendar = getJson(student, "/api/classroom/calendar?from=" + Instant.now() + "&to="
                + Instant.now().plus(30, ChronoUnit.DAYS));
        assertThat(calendar.at("/0/title").asString()).isEqualTo("Field trip");
        assertThat(calendar.at("/0/teacherName").asString()).isEqualTo("Ms Rivera");
        assertThat(getJson(student, "/api/notifications").findValuesAsString("type")).contains("NOTICE", "EVENT");
    }

    /** The student accepts the teacher's invitation; returns the student's id. */
    private UUID join() throws Exception {
        call(teacher, post("/api/classroom/students/invite"), Map.of("emails", List.of(student.email())))
                .andExpect(status().isOk());
        String invitation = getJson(student, "/api/classroom/invitations").at("/0/id").asString();
        call(student, post("/api/classroom/invitations/" + invitation + "/accept"), null).andExpect(status().isNoContent());
        return UUID.fromString(getJson(teacher, "/api/classroom/students").at("/0/studentId").asString());
    }

    private ResultActions call(Registered user, MockHttpServletRequestBuilder request, Object body) throws Exception {
        request.header("Authorization", user.bearer());
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        }
        return mvc.perform(request);
    }

    private JsonNode getJson(Registered user, String path) throws Exception {
        return json.readTree(mvc.perform(get(path).header("Authorization", user.bearer()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
}
