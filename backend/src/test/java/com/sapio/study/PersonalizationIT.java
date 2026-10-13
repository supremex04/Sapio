package com.sapio.study;

import java.util.UUID;

import com.sapio.FakeAi;
import com.sapio.IntegrationTest;
import com.sapio.TestUsers;
import com.sapio.TestUsers.Registered;
import com.sapio.document.DocumentFlowIT;
import com.sapio.document.extraction.ExtractionGateway;
import com.sapio.knowledge.ai.KnowledgeAi;
import com.sapio.study.ai.StudyAi;
import com.sapio.user.personalization.LearnerProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** "Personalize": the profile is editable, and lessons, notes, tests and the tutor are written for it. */
@IntegrationTest
class PersonalizationIT {

    private static final String ENGINEER = """
            {"academicLevel":"UNDERGRADUATE","occupation":"STUDENT","fieldOfStudy":"Computer engineering",
             "depth":"IN_DEPTH","preferences":["CODE","EXAMPLES"],"goal":"EXAM"}""";

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

    Registered user;
    UUID documentId;

    @BeforeEach
    void readyDocument() throws Exception {
        when(extraction.extract(any())).thenReturn(FakeAi.extraction());
        FakeAi.stub(knowledgeAi, ai);
        when(ai.generateLesson(any())).thenAnswer(inv -> {
            StudyAi.LessonSpec spec = inv.getArgument(0);
            return spec == null ? null : FakeAi.lesson(spec.figures().isEmpty() ? "none" : spec.figures().getFirst().id());
        });
        when(ai.notes(any())).thenAnswer(inv -> FakeAi.notes("## Notes"));
        user = TestUsers.register(mvc, json);
        documentId = DocumentFlowIT.upload(mvc, json, user, "bio.png", DocumentFlowIT.PNG_BYTES);
        DocumentFlowIT.awaitTerminal(mvc, json, user, documentId);
    }

    @Test
    void theProfileIsEditableAndValidated() throws Exception {
        get("/api/me/personalization").andExpect(jsonPath("$.configured").value(false))
                .andExpect(jsonPath("$.preferences").isEmpty());
        save(ENGINEER).andExpect(status().isOk())
                .andExpect(jsonPath("$.configured").value(true))
                .andExpect(jsonPath("$.academicLevel").value("UNDERGRADUATE"))
                .andExpect(jsonPath("$.fieldOfStudy").value("Computer engineering"))
                .andExpect(jsonPath("$.preferences.length()").value(2));
        // Editing replaces the profile; clearing everything makes it "not configured" again.
        save("{\"depth\":\"CONCISE\"}").andExpect(jsonPath("$.academicLevel").doesNotExist())
                .andExpect(jsonPath("$.depth").value("CONCISE"));
        save("{}").andExpect(jsonPath("$.configured").value(false));
        save("{\"fieldOfStudy\":\"" + "x".repeat(101) + "\"}").andExpect(status().isBadRequest());
        save("{\"academicLevel\":\"WIZARD\"}").andExpect(status().isBadRequest());
    }

    @Test
    void lessonsAndNotesAreWrittenForTheLearnersProfile() throws Exception {
        String sectionId = json.readTree(get("/api/documents/{id}/tree", documentId).andReturn().getResponse()
                .getContentAsString()).get("nodes").get(1).get("id").asString();
        String notesUrl = "/api/documents/" + documentId + "/nodes/" + sectionId + "/notes?style=EXPAND";

        // Before personalising: written for a general audience.
        get(notesUrl).andExpect(status().isOk());
        ArgumentCaptor<StudyAi.NotesSpec> notes = ArgumentCaptor.forClass(StudyAi.NotesSpec.class);
        verify(ai, times(1)).notes(notes.capture());
        assertThat(notes.getValue().learner()).isNull();

        save(ENGINEER).andExpect(status().isOk());

        // The cached notes were for another profile: rewritten once, then cached again.
        get(notesUrl).andExpect(status().isOk());
        get(notesUrl).andExpect(status().isOk());
        verify(ai, times(2)).notes(notes.capture());
        StudyAi.Learner learner = notes.getValue().learner();
        assertThat(learner.academicLevel()).isEqualTo(LearnerProfile.AcademicLevel.UNDERGRADUATE);
        assertThat(learner.depth()).isEqualTo(LearnerProfile.Depth.IN_DEPTH);
        assertThat(learner.preferences()).containsExactly(LearnerProfile.Preference.CODE,
                LearnerProfile.Preference.EXAMPLES);
        assertThat(learner.field()).isEqualTo("Computer engineering");

        // A lesson opened now is written for the profile too.
        String topicId = json.readTree(get("/api/documents/{id}/material", documentId).andReturn().getResponse()
                .getContentAsString()).at("/topics/0/id").asString();
        get("/api/topics/" + topicId + "/lesson").andExpect(status().isOk());
        ArgumentCaptor<StudyAi.LessonSpec> lesson = ArgumentCaptor.forClass(StudyAi.LessonSpec.class);
        verify(ai).generateLesson(lesson.capture());
        assertThat(lesson.getValue().learner().goal()).isEqualTo(LearnerProfile.Goal.EXAM);
    }

    private ResultActions get(String path, Object... args) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.get(path, args)
                .header("Authorization", user.bearer()));
    }

    private ResultActions save(String body) throws Exception {
        return mvc.perform(put("/api/me/personalization").header("Authorization", user.bearer())
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }
}
