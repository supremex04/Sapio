package com.ved.study.ai;

import java.util.List;

import com.ved.knowledge.retrieval.ContextAssembler.Figure;
import com.ved.knowledge.retrieval.ContextAssembler.Section;
import com.ved.study.material.MaterialKind;
import com.ved.study.material.TopicDifficulty;
import com.ved.study.question.QuestionType;
import com.ved.study.question.Verdict;

/**
 * Port to the AI that writes study content. Implemented by {@link InferenceStudyAi} (Python service + LLM).
 * Context is always structured and retrieved from the knowledge store ({@link Section}s with citation ids,
 * {@link Figure}s with ids); the AI never receives the whole document. Records mirror the wire contract in
 * docs/specs/inference-api.md. All methods throw {@link StudyAiException}.
 */
public interface StudyAi {

    MaterialDraft buildMaterial(MaterialSpec spec);

    LessonDraft generateLesson(LessonSpec spec);

    QuestionsDraft generateQuestions(QuestionSpec spec);

    GradeResult grade(GradeSpec spec);

    ReportNarrative assessmentFeedback(FeedbackSpec spec);

    AnswerDraft answer(AnswerSpec spec);

    NotesDraft notes(NotesSpec spec);

    // ---- shared

    record TopicBrief(String title, String summary, List<String> keyConcepts, TopicDifficulty difficulty) {
    }

    // ---- learning path from the outline

    record OutlineNode(String key, String label, String title, int level, String pages, String summary,
                       List<String> keyTerms) {
    }

    record MaterialSpec(String filename, String sample, List<OutlineNode> nodes) {
    }

    record TopicDraft(int position, String title, String summary, List<String> keyConcepts,
                      TopicDifficulty difficulty, List<String> nodeKeys) {
    }

    record MaterialDraft(String title, String summary, String language, MaterialKind kind, List<TopicDraft> topics,
                         String model) {
    }

    // ---- lesson

    record LessonSpec(String materialTitle, String language, MaterialKind kind, TopicBrief topic,
                      List<String> previousTopics, List<String> nextTopics, List<Section> sections,
                      List<Figure> figures) {
    }

    record SectionDraft(String kind, String heading, String body) {
    }

    record CheckDraft(int afterSection, QuestionType type, String prompt, List<String> options,
                      Integer correctOptionIndex, String referenceAnswer, String explanation) {
    }

    record LessonDraft(List<SectionDraft> sections, List<String> keyTakeaways, List<CheckDraft> checks,
                       List<String> figureIds, String model) {
    }

    // ---- questions

    record QuestionTopic(String key, String title, String summary, List<String> keyConcepts,
                         TopicDifficulty difficulty, List<Section> sections) {
    }

    record QuestionSpec(String materialTitle, String language, MaterialKind kind, int count, String difficulty,
                        List<QuestionTopic> topics, List<String> avoidPrompts) {
    }

    record QuestionDraft(String topicKey, QuestionType type, String prompt, List<String> options,
                         Integer correctOptionIndex, String referenceAnswer, String explanation,
                         List<String> sourceSectionIds) {
    }

    record QuestionsDraft(List<QuestionDraft> questions, String model) {
    }

    // ---- grading

    record QuestionToGrade(String prompt, String referenceAnswer, String explanation) {
    }

    record GradeSpec(String language, QuestionToGrade question, List<Section> sections, String answer) {
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

    // ---- long form: answers (tutor + "ask your document") and notes

    /** @param role "user" or "tutor" */
    record ChatTurn(String role, String text) {
    }

    /** @param style "tutor" (short, conversational) or "answer" (long-form) */
    record AnswerSpec(String materialTitle, String language, String question, List<ChatTurn> history,
                      List<Section> sections, List<Figure> figures, String style) {
    }

    record AnswerDraft(String answer, boolean grounded, List<String> citedSectionIds, List<String> figureIds,
                       String model) {
    }

    /** @param scope "section" (full notes) or "overview" (chapter notes from its subsections) */
    record NotesSpec(String materialTitle, String language, MaterialKind kind, String title, String path, String scope,
                     List<Section> sections, List<Figure> figures) {
    }

    record NotesDraft(String markdown, List<String> figureIds, List<String> citedSectionIds, String model) {
    }
}
