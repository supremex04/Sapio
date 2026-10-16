package com.sapio.study.ai;

import java.util.List;

import com.sapio.knowledge.retrieval.ContextAssembler.Figure;
import com.sapio.knowledge.retrieval.ContextAssembler.Section;
import com.sapio.study.material.MaterialKind;
import com.sapio.study.material.TopicDifficulty;
import com.sapio.study.question.QuestionType;
import com.sapio.study.question.Verdict;
import com.sapio.user.personalization.LearnerProfile;

/**
 * Port to the AI that writes study content. Implemented by {@link InferenceStudyAi} (Python service + LLM).
 * Context is always structured and retrieved from the knowledge store ({@link Source}s with citation ids,
 * {@link SourceFigure}s with ids); the AI never receives the whole document, nor any internal id. Records mirror
 * the wire contract (contracts/inference-openapi.json, checked by InferenceContractTest). All methods throw
 * {@link StudyAiException}.
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

    /**
     * Who the content is written for ("Personalize"): shapes depth, vocabulary, examples and emphasis, never facts or
     * grading. {@code null} on a spec means a general audience. {@code field} is the learner's own words.
     */
    record Learner(LearnerProfile.AcademicLevel academicLevel, LearnerProfile.Occupation occupation, String field,
                   LearnerProfile.Depth depth, List<LearnerProfile.Preference> preferences,
                   LearnerProfile.Goal goal) {
    }

    /** A retrieved section as the AI sees it; {@code id} is the citation id it must use ("§2.3"). */
    record Source(String id, String path, String pages, String summary, List<String> passages) {
    }

    /** A figure the AI may embed as {@code ![caption](figure:id)}. */
    record SourceFigure(String id, String sectionId, String caption, String description) {
    }

    static List<Source> sources(List<Section> sections) {
        return sections.stream().map(s -> new Source(s.id(), s.path(), s.pages(), s.summary(), s.passages())).toList();
    }

    static List<SourceFigure> figures(List<Figure> figures) {
        return figures.stream().map(f -> new SourceFigure(f.id(), f.sectionId(), f.caption(), f.description()))
                .toList();
    }

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
                      List<String> previousTopics, List<String> nextTopics, List<Source> sections,
                      List<SourceFigure> figures, Learner learner) {
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
                         TopicDifficulty difficulty, List<Source> sections) {
    }

    /** @param format question types: MIXED (by difficulty), OBJECTIVE (choice only) or SUBJECTIVE (short answer only) */
    record QuestionSpec(String materialTitle, String language, MaterialKind kind, int count, String difficulty,
                        String format, List<QuestionTopic> topics, List<String> avoidPrompts, Learner learner) {
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

    record GradeSpec(String language, QuestionToGrade question, List<Source> sections, String answer) {
    }

    record GradeResult(Verdict verdict, double score, String feedback, String model) {
    }

    // ---- assessment report

    record TopicResult(String title, int questionCount, double score) {
    }

    record MissedQuestion(String topicTitle, String prompt, String learnerAnswer, String correctAnswer) {
    }

    record FeedbackSpec(String materialTitle, String language, double score, List<TopicResult> topics,
                        List<MissedQuestion> missed, Learner learner) {
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

    /**
     * @param style "tutor" (short, conversational) or "answer" (long-form)
     * @param focus what the learner is looking at (a lesson section, or a check question and their answer); "" if none
     */
    record AnswerSpec(String materialTitle, String language, String question, List<ChatTurn> history,
                      List<Source> sections, List<SourceFigure> figures, String style, String focus,
                      Learner learner) {
    }

    record AnswerDraft(String answer, boolean grounded, List<String> citedSectionIds, List<String> figureIds,
                       String model) {
    }

    /** @param scope "section" (full notes) or "overview" (chapter notes from its subsections) */
    /** @param style "expand" (in-depth explanation) or "revision" (condensed revision notes) */
    record NotesSpec(String materialTitle, String language, MaterialKind kind, String title, String path, String scope,
                     String style, List<Source> sections, List<SourceFigure> figures, Learner learner) {
    }

    record NotesDraft(String markdown, List<String> figureIds, List<String> citedSectionIds, String model) {
    }
}
