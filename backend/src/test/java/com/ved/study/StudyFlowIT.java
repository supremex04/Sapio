package com.ved.study;

import java.time.Duration;
import java.util.UUID;

import com.ved.FakeAi;
import com.ved.IntegrationTest;
import com.ved.MutableClock;
import com.ved.TestUsers;
import com.ved.TestUsers.Registered;
import com.ved.document.DocumentFlowIT;
import com.ved.document.extraction.ExtractionGateway;
import com.ved.knowledge.ai.KnowledgeAi;
import com.ved.study.ai.StudyAi;
import com.ved.study.ai.StudyAiException;
import com.ved.study.question.Verdict;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Learn → review → test → report → progress, against a real database with the AI mocked. */
@IntegrationTest
class StudyFlowIT {

    @Autowired
    MockMvc mvc;

    @Autowired
    JsonMapper json;

    @Autowired
    MutableClock clock;

    @MockitoBean
    ExtractionGateway extraction;

    @MockitoBean
    StudyAi ai;

    @MockitoBean
    KnowledgeAi knowledgeAi;

    Registered user;
    UUID documentId;
    UUID demandTopic;
    UUID supplyTopic;

    @BeforeEach
    void readyDocument() throws Exception {
        when(extraction.extract(any())).thenReturn(FakeAi.extraction());
        FakeAi.stub(knowledgeAi, ai);
        when(ai.generateLesson(any())).thenAnswer(inv -> {
            StudyAi.LessonSpec spec = inv.getArgument(0);
            if (spec == null) {
                return null; // Mockito re-stubbing replays answers with null arguments
            }
            return FakeAi.lesson(spec.figures().isEmpty() ? "none" : spec.figures().getFirst().id());
        });
        when(ai.assessmentFeedback(any())).thenReturn(FakeAi.narrative());
        user = TestUsers.register(mvc, json);
        documentId = DocumentFlowIT.upload(mvc, json, user, "econ.png", DocumentFlowIT.PNG_BYTES);
        assertThat(DocumentFlowIT.awaitTerminal(mvc, json, user, documentId).get("status").asString())
                .isEqualTo("READY");
        JsonNode material = getJson("/api/documents/{id}/material", documentId);
        demandTopic = UUID.fromString(material.at("/topics/0/id").asString());
        supplyTopic = UUID.fromString(material.at("/topics/1/id").asString());
    }

    @Test
    void materialRecommendsTheFirstTopicAndHidesNothingSensitive() throws Exception {
        JsonNode material = getJson("/api/documents/{id}/material", documentId);
        assertThat(material.get("title").asString()).isEqualTo("Intro Biology");
        assertThat(material.at("/topics/0/status").asString()).isEqualTo("NOT_STARTED");
        assertThat(material.at("/topics/0/recommended").asBoolean()).isTrue();
        assertThat(material.at("/topics/1/recommended").asBoolean()).isFalse();
        assertThat(material.at("/topics/0/mastery").isNull()).isTrue();
    }

    @Test
    void learnFlowGradesChecksAwardsXpAndSchedulesReviews() throws Exception {
        JsonNode lesson = getJson("/api/topics/{id}/lesson", demandTopic);
        assertThat(lesson.get("status").asString()).isEqualTo("IN_PROGRESS");
        assertThat(lesson.get("sections").size()).isEqualTo(2);
        JsonNode checks = lesson.get("checks");
        assertThat(checks.get(0).get("afterSection").asInt()).isEqualTo(-1); // warm-up first
        assertThat(checks.get(2).get("type").asString()).isEqualTo("SHORT_ANSWER");
        assertThat(lesson.toString()).doesNotContain("correctOption").doesNotContain("referenceAnswer");
        // The lesson embeds the document's own figure, resolved to a signed URL.
        assertThat(lesson.get("figures").size()).isEqualTo(1);
        String figureId = lesson.get("figures").propertyNames().iterator().next();
        assertThat(lesson.at("/sections/0/body").asString()).contains("figure:" + figureId);
        assertThat(lesson.at("/figures/" + figureId + "/url").asString()).contains("/api/figures/" + figureId + "/image?exp=");

        // Cached: a second open does not regenerate.
        getJson("/api/topics/{id}/lesson", demandTopic);
        verify(ai, times(1)).generateLesson(any());

        UUID warmup = UUID.fromString(checks.get(0).get("id").asString());
        UUID trueFalse = UUID.fromString(checks.get(1).get("id").asString());
        UUID explain = UUID.fromString(checks.get(2).get("id").asString());

        answer("/api/questions/{id}/answer", warmup, "{\"optionIndex\":1,\"confidence\":\"HIGH\"}")
                .andExpect(jsonPath("$.verdict").value("INCORRECT"))
                .andExpect(jsonPath("$.correctOptionIndex").value(0))
                .andExpect(jsonPath("$.confidence").value("HIGH"));
        answer("/api/questions/{id}/answer", trueFalse, "{\"optionIndex\":1}")
                .andExpect(jsonPath("$.verdict").value("CORRECT"))
                .andExpect(jsonPath("$.nextReviewAt").isNotEmpty());
        answer("/api/questions/{id}/answer", trueFalse, "{\"optionIndex\":1}"); // retry: no extra XP

        when(ai.grade(any())).thenReturn(FakeAi.grade(Verdict.PARTIAL, 0.5));
        answer("/api/questions/{id}/answer", explain, "{\"text\":\"price up, demand down\"}")
                .andExpect(jsonPath("$.verdict").value("PARTIAL"))
                .andExpect(jsonPath("$.referenceAnswer").value("Water diffuses across a membrane."));
        answer("/api/questions/{id}/answer", trueFalse, "{\"optionIndex\":7}").andExpect(status().isBadRequest());

        mvc.perform(post("/api/topics/{id}/complete", demandTopic).header("Authorization", user.bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("COMPLETED"));
        mvc.perform(post("/api/topics/{id}/complete", demandTopic).header("Authorization", user.bearer()))
                .andExpect(status().isOk()); // idempotent, no double XP

        JsonNode summary = getJson("/api/progress/summary");
        assertThat(summary.at("/xp/total").asLong()).isEqualTo(10 + 50);
        assertThat(summary.at("/today/questionsAnswered").asInt()).isEqualTo(4);
        assertThat(summary.at("/today/lessonsCompleted").asInt()).isEqualTo(1);
        assertThat(summary.at("/streak/current").asInt()).isEqualTo(1);

        JsonNode material = getJson("/api/documents/{id}/material", documentId);
        assertThat(material.at("/topics/0/status").asString()).isEqualTo("COMPLETED");
        assertThat(material.at("/topics/0/mastery").asDouble()).isBetween(0.0, 1.0);
        assertThat(material.at("/topics/1/recommended").asBoolean()).isTrue();

        // The missed warm-up is due again in 10 minutes; the others tomorrow.
        assertThat(getJson("/api/reviews/summary").get("dueNow").asLong()).isZero();
        clock.advance(Duration.ofMinutes(11));
        assertThat(getJson("/api/reviews/summary").get("dueNow").asLong()).isEqualTo(1);
        assertThat(getJson("/api/study/next-step").get("kind").asString()).isEqualTo("REVIEW");
        JsonNode due = getJson("/api/reviews/due");
        assertThat(due.at("/items/0/question/id").asString()).isEqualTo(warmup.toString());
        assertThat(due.toString()).doesNotContain("correctOption");

        answer("/api/reviews/{id}/answer", warmup, "{\"optionIndex\":0}")
                .andExpect(jsonPath("$.verdict").value("CORRECT"));
        answer("/api/reviews/{id}/answer", warmup, "{\"optionIndex\":0}")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("NOT_DUE"));
        assertThat(getJson("/api/progress/summary").at("/xp/total").asLong()).isEqualTo(10 + 50 + 5);
    }

    @Test
    void testFlowInterleavesGradesOnceAndReports() throws Exception {
        when(ai.generateQuestions(any())).thenReturn(FakeAi.questions(demandTopic.toString(), supplyTopic.toString()));
        when(ai.grade(any())).thenReturn(FakeAi.grade(Verdict.CORRECT, 0.9));

        String created = mvc.perform(post("/api/documents/{id}/assessments", documentId)
                        .header("Authorization", user.bearer()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"questionCount\":5,\"difficulty\":\"MIXED\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode assessment = json.readTree(created);
        UUID id = UUID.fromString(assessment.get("id").asString());
        assertThat(assessment.get("questionCount").asInt()).isEqualTo(3); // unknown-topic question dropped
        assertThat(created).doesNotContain("correctOption").doesNotContain("referenceAnswer");
        // Interleaved: the first two questions come from different topics.
        assertThat(assessment.at("/items/0/question/topicId").asString())
                .isNotEqualTo(assessment.at("/items/1/question/topicId").asString());

        int shortAnswer = -1;
        for (JsonNode item : assessment.get("items")) {
            String type = item.at("/question/type").asString();
            int position = item.get("position").asInt();
            String body = switch (type) {
                case "MULTIPLE_CHOICE" -> "{\"optionIndex\":0,\"confidence\":\"HIGH\"}"; // wrong (correct is "b")
                case "TRUE_FALSE" -> "{\"optionIndex\":0,\"confidence\":\"LOW\"}";
                default -> {
                    shortAnswer = position;
                    yield null;
                }
            };
            if (body != null) {
                answer("/api/assessments/" + id + "/items/{p}/answer", position, body).andExpect(status().isOk());
            }
        }
        answer("/api/assessments/" + id + "/items/{p}/answer", 1, "{\"optionIndex\":0}")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ALREADY_ANSWERED"));
        // The short-answer item is skipped on purpose.
        assertThat(shortAnswer).isPositive();

        JsonNode done = json.readTree(mvc.perform(post("/api/assessments/{id}/complete", id)
                        .header("Authorization", user.bearer()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(done.get("status").asString()).isEqualTo("COMPLETED");
        JsonNode report = done.get("report");
        assertThat(report.get("correctCount").asInt()).isEqualTo(1);
        assertThat(report.get("incorrectCount").asInt()).isEqualTo(1);
        assertThat(report.get("skippedCount").asInt()).isEqualTo(1);
        assertThat(report.get("score").asDouble()).isEqualTo(0.333);
        assertThat(report.at("/calibration/confidentIncorrect").asInt()).isEqualTo(1);
        assertThat(report.at("/calibration/unsureCorrect").asInt()).isEqualTo(1);
        assertThat(report.at("/weakAreas/0/topicId").asString()).isEqualTo(supplyTopic.toString());
        assertThat(done.at("/items/0/answer/correctOptionIndex").isMissingNode()).isFalse();

        answer("/api/assessments/" + id + "/items/{p}/answer", shortAnswer, "{\"text\":\"late\"}")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ASSESSMENT_CLOSED"));
        mvc.perform(get("/api/documents/{id}/assessments", documentId).header("Authorization", user.bearer()))
                .andExpect(jsonPath("$[0].score").value(0.333))
                .andExpect(jsonPath("$[0].answeredCount").value(2));
        assertThat(getJson("/api/progress/summary").at("/totals/assessmentsCompleted").asInt()).isEqualTo(1);
        // All three questions, including the skipped one, are now in spaced review.
        assertThat(getJson("/api/reviews/summary").get("total").asLong()).isEqualTo(3);
    }

    @Test
    void tutorKnowsWhatTheLearnerIsLookingAt() throws Exception {
        when(ai.answer(any())).thenReturn(FakeAi.answer("none"));
        JsonNode checks = getJson("/api/topics/{id}/lesson", demandTopic).get("checks");
        UUID trueFalse = UUID.fromString(checks.get(1).get("id").asString());
        String focusOnCheck = "{\"question\":\"why?\",\"focus\":{\"questionId\":\"" + trueFalse + "\"}}";

        // Before answering: the question is in focus, but the correct answer is withheld (hints, not spoilers).
        answer("/api/topics/{id}/ask", demandTopic, focusOnCheck).andExpect(status().isOk());
        assertThat(lastAnswerSpec().focus()).contains("Osmosis needs ATP.", "has not answered")
                .doesNotContain("Correct answer");

        // After a wrong answer: "why?" is about their answer versus the correct one.
        answer("/api/questions/{id}/answer", trueFalse, "{\"optionIndex\":0}").andExpect(status().isOk());
        answer("/api/topics/{id}/ask", demandTopic, focusOnCheck).andExpect(status().isOk());
        assertThat(lastAnswerSpec().focus()).contains("Learner's answer: A) True (INCORRECT)",
                "Correct answer: B) False", "It is passive");

        answer("/api/topics/{id}/ask", demandTopic, "{\"question\":\"why?\",\"focus\":{\"sectionIndex\":0}}")
                .andExpect(status().isOk());
        assertThat(lastAnswerSpec().focus()).startsWith("Lesson section \"What is osmosis?\"");

        // A question from another topic is ignored rather than leaked into this conversation.
        answer("/api/topics/{id}/ask", supplyTopic, focusOnCheck).andExpect(status().isOk());
        assertThat(lastAnswerSpec().focus()).isEmpty();
    }

    private StudyAi.AnswerSpec lastAnswerSpec() {
        ArgumentCaptor<StudyAi.AnswerSpec> spec = ArgumentCaptor.forClass(StudyAi.AnswerSpec.class);
        verify(ai, atLeastOnce()).answer(spec.capture());
        return spec.getValue();
    }

    @Test
    void aiOutageIsA503AndNothingIsRecorded() throws Exception {
        when(ai.generateLesson(any()))
                .thenThrow(new StudyAiException("The AI service is busy", false, true, Duration.ofSeconds(90)))
                .thenThrow(new StudyAiException("This section has too little text to teach", true));
        String body = mvc.perform(get("/api/topics/{id}/lesson", supplyTopic).header("Authorization", user.bearer())
                        .header("X-Request-Id", "lesson-req-1"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Retry-After", "90"))
                .andExpect(header().string("X-Request-Id", "lesson-req-1"))
                .andExpect(jsonPath("$.code").value("AI_UNAVAILABLE"))
                .andExpect(jsonPath("$.requestId").value("lesson-req-1"))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).contains("busy");
        // A refused input is the learner-visible 422, not an outage.
        mvc.perform(get("/api/topics/{id}/lesson", supplyTopic).header("Authorization", user.bearer()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("AI_INPUT_REJECTED"))
                .andExpect(header().exists("X-Request-Id"));
        assertThat(getJson("/api/documents/{id}/material", documentId).at("/topics/1/status").asString())
                .isEqualTo("NOT_STARTED");
    }

    @Test
    void otherUsersCannotReachTopicsQuestionsOrAssessments() throws Exception {
        JsonNode lesson = getJson("/api/topics/{id}/lesson", demandTopic);
        Registered other = TestUsers.register(mvc, json);
        mvc.perform(get("/api/topics/{id}/lesson", demandTopic).header("Authorization", other.bearer()))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/questions/{id}/answer", lesson.at("/checks/0/id").asString())
                        .header("Authorization", other.bearer()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"optionIndex\":0}"))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/documents/{id}/assessments", documentId).header("Authorization", other.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"questionCount\":5,\"difficulty\":\"EASY\"}"))
                .andExpect(status().isNotFound());
    }

    private JsonNode getJson(String path, Object... vars) throws Exception {
        return json.readTree(mvc.perform(get(path, vars).header("Authorization", user.bearer()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private ResultActions answer(String path, Object var, String body) throws Exception {
        return mvc.perform(post(path, var).header("Authorization", user.bearer())
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }
}
