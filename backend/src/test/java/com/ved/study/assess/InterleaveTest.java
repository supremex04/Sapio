package com.ved.study.assess;

import java.util.List;
import java.util.UUID;

import com.ved.study.ai.StudyAi;
import com.ved.study.material.StudyTopic;
import com.ved.study.material.TopicDifficulty;
import com.ved.study.question.QuestionType;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class InterleaveTest {

    @Test
    void roundRobinsAcrossTopicsInPathOrder() {
        StudyTopic a = topic(1);
        StudyTopic b = topic(2);
        List<StudyAi.QuestionDraft> drafts = List.of(q(a), q(a), q(a), q(b));

        List<String> order = AssessmentService.interleave(drafts, List.of(a, b)).stream()
                .map(StudyAi.QuestionDraft::topicKey).toList();

        String ka = a.getId().toString();
        String kb = b.getId().toString();
        assertThat(order).containsExactly(ka, kb, ka, ka);
    }

    private static StudyAi.QuestionDraft q(StudyTopic t) {
        return new StudyAi.QuestionDraft(t.getId().toString(), QuestionType.TRUE_FALSE, "p", List.of("T", "F"), 0,
                "T", "e", List.of());
    }

    private static StudyTopic topic(int position) {
        StudyTopic t = new StudyTopic(UUID.randomUUID(), position, "T", "s", List.of(), TopicDifficulty.CORE,
                List.of(1));
        ReflectionTestUtils.setField(t, "id", UUID.randomUUID());
        return t;
    }
}
