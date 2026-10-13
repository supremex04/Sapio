package com.sapio.study.ai;

import com.sapio.common.inference.InferenceClient;
import com.sapio.common.inference.InferenceException;
import org.springframework.stereotype.Component;

/** Calls {@code /v1/study/*} on the inference service. */
@Component
class InferenceStudyAi implements StudyAi {

    private static final String UNAVAILABLE = "The AI tutor is unavailable right now, please retry in a moment";

    private final InferenceClient inference;

    InferenceStudyAi(InferenceClient inference) {
        this.inference = inference;
    }

    @Override
    public MaterialDraft buildMaterial(MaterialSpec spec) {
        return post("/v1/study/material", spec, MaterialDraft.class);
    }

    @Override
    public LessonDraft generateLesson(LessonSpec spec) {
        return post("/v1/study/lesson", spec, LessonDraft.class);
    }

    @Override
    public QuestionsDraft generateQuestions(QuestionSpec spec) {
        return post("/v1/study/questions", spec, QuestionsDraft.class);
    }

    @Override
    public GradeResult grade(GradeSpec spec) {
        return post("/v1/study/grade", spec, GradeResult.class);
    }

    @Override
    public ReportNarrative assessmentFeedback(FeedbackSpec spec) {
        return post("/v1/study/assessment-feedback", spec, ReportNarrative.class);
    }

    @Override
    public AnswerDraft answer(AnswerSpec spec) {
        return post("/v1/study/answer", spec, AnswerDraft.class);
    }

    @Override
    public NotesDraft notes(NotesSpec spec) {
        return post("/v1/study/notes", spec, NotesDraft.class);
    }

    private <T> T post(String path, Object body, Class<T> type) {
        try {
            return inference.postJson(path, body, type);
        } catch (InferenceException e) {
            throw new StudyAiException(e.userMessage(UNAVAILABLE), e.isInputRejected(), e.isRetryable(),
                    e.retryAfter().orElse(null));
        }
    }
}
