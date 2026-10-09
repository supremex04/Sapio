package com.ved.study.ai;

import java.util.List;

import com.ved.study.material.MaterialKind;
import com.ved.study.material.TopicDifficulty;
import com.ved.study.question.QuestionType;
import com.ved.study.question.Verdict;

/**
 * Port to the AI that writes study content. Implemented by {@link InferenceStudyAi} (Python service + LLM).
 * Records mirror the wire contract in docs/specs/inference-api.md. All methods throw {@link StudyAiException}.
 */
public interface StudyAi {

    MaterialDraft buildMaterial(MaterialSpec spec);

    LessonDraft generateLesson(LessonSpec spec);

    QuestionsDraft generateQuestions(QuestionSpec spec);

    GradeResult grade(GradeSpec spec);

    ReportNarrative assessmentFeedback(FeedbackSpec spec);

    TutorReply ask(AskSpec spec);

    // ---- shared

    record SourceChunk(int position, String heading, String content) {
    }

    record TopicBrief(String title, String summary, List<String> keyConcepts, TopicDifficulty difficulty) {
    }

    // ---- material

    record PageText(int pageFrom, int pageTo, String text) {
    }

    record MaterialSpec(String filename, List<PageText> pages) {
    }

    record ChunkDraft(int position, String heading, String content, int pageFrom, int pageTo) {
    }

    record TopicDraft(int position, String title, String summary, List<String> keyConcepts,
                      TopicDifficulty difficulty, List<Integer> chunkPositions) {
    }

    record MaterialDraft(String title, String summary, String language, MaterialKind kind, List<ChunkDraft> chunks,
                         List<TopicDraft> topics, String model) {
    }

    // ---- lesson

    record LessonSpec(String materialTitle, String language, MaterialKind kind, TopicBrief topic,
                      List<String> previousTopics, List<String> nextTopics, List<SourceChunk> sources) {
    }

    record SectionDraft(String kind, String heading, String body) {
    }

    record CheckDraft(int afterSection, QuestionType type, String prompt, List<String> options,
                      Integer correctOptionIndex, String referenceAnswer, String explanation) {
    }

    record LessonDraft(List<SectionDraft> sections, List<String> keyTakeaways, List<CheckDraft> checks,
                       String model) {
    }

    // ---- questions

    record QuestionTopic(String key, String title, String summary, List<String> keyConcepts,
                         TopicDifficulty difficulty, List<SourceChunk> sources) {
    }

    record QuestionSpec(String materialTitle, String language, MaterialKind kind, int count, String difficulty,
                        List<QuestionTopic> topics, List<String> avoidPrompts) {
    }

    record QuestionDraft(String topicKey, QuestionType type, String prompt, List<String> options,
                         Integer correctOptionIndex, String referenceAnswer, String explanation,
                         List<Integer> sourceChunkPositions) {
    }

    record QuestionsDraft(List<QuestionDraft> questions, String model) {
    }

    // ---- grading

    record QuestionToGrade(String prompt, String referenceAnswer, String explanation) {
    }

    record GradeSpec(String language, QuestionToGrade question, List<SourceChunk> sources, String answer) {
    }

    record GradeResult(Verdict verdict, double score, String feedback, String model) {
    }

    // ---- assessment report

    record TopicResult(String title, int questionCount, double score) {
    }

    record MissedQuestion(String topicTitle, String prompt, String learnerAnswer, String correctAnswer) {
    }

    record FeedbackSpec(String materialTitle, String language, double score, List<TopicResult> topics,
                        List<MissedQuestion> missed) {
    }

    record WeakArea(String topicTitle, String advice) {
    }

    record ReportNarrative(String summary, List<String> strengths, List<WeakArea> weakAreas, List<String> nextSteps,
                           String model) {
    }

    // ---- tutor

    /** @param role "user" or "tutor" */
    record ChatTurn(String role, String text) {
    }

    record AskSpec(String materialTitle, String language, String topicTitle, List<SourceChunk> sources,
                   String question, List<ChatTurn> history) {
    }

    record TutorReply(String answer, boolean grounded, String model) {
    }
}
