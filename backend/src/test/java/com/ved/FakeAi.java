package com.ved;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

import com.ved.document.extraction.ExtractionResult;
import com.ved.knowledge.ai.KnowledgeAi;
import com.ved.study.ai.StudyAi;
import com.ved.study.material.MaterialKind;
import com.ved.study.material.TopicDifficulty;
import com.ved.study.question.QuestionType;
import com.ved.study.question.Verdict;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/** Canned AI / extraction outputs for integration tests. */
public final class FakeAi {

    /** A real 1x1 PNG. */
    public static final byte[] PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==");

    private FakeAi() {
    }

    /** Two chapters; chapter 1 has section 1.1 with a figure; chapter 2 has its own text. */
    public static ExtractionResult extraction() {
        return new ExtractionResult("# The Cell\n\n...", "pymupdf", "en", 3, 3, 0, List.of(
                new ExtractionResult.Segment(1, 1, "text", "# Chapter 1 The Cell"),
                new ExtractionResult.Segment(2, 2, "text", "Osmosis..."),
                new ExtractionResult.Segment(3, 3, "text", "# Chapter 2 Energy")),
                new ExtractionResult.Tree(
                        List.of(new ExtractionResult.Node("n1", null, 1, "1", "Chapter 1", "The Cell", 1, 1, 2),
                                new ExtractionResult.Node("n2", "n1", 2, "1.1", "1.1", "Osmosis", 2, 2, 2),
                                new ExtractionResult.Node("n3", null, 1, "2", "Chapter 2", "Energy", 3, 3, 3)),
                        List.of(new ExtractionResult.Chunk("c1", "n1", 1, "All living things are made of cells.", 1, 1),
                                new ExtractionResult.Chunk("c2", "n2", 2,
                                        "Osmosis is the diffusion of water across a partially permeable membrane.", 2, 2),
                                new ExtractionResult.Chunk("c3", "n3", 3,
                                        "Mitochondria convert glucose into ATP during respiration.", 3, 3)),
                        List.of(new ExtractionResult.Figure("f1", "n2", "c2", 1, 2, List.of(10f, 20f, 300f, 200f),
                                        "Figure 1.1: Osmosis in a plant cell", "image/png", 1, 1, PNG),
                                new ExtractionResult.Figure("f2", "n3", "c3", 2, 3, null, "", "image/png", 1, 1, PNG))));
    }

    // ---------------------------------------------------------------- knowledge (indexing) AI

    public static List<KnowledgeAi.FigureDescription> describe(List<KnowledgeAi.FigureInput> inputs) {
        return inputs.stream().map(f -> f.caption().isBlank()
                ? new KnowledgeAi.FigureDescription(f.key(), "DECORATIVE", false, "A logo.")
                : new KnowledgeAi.FigureDescription(f.key(), "DIAGRAM", true,
                        "Diagram of water moving through a membrane into a plant cell.")).toList();
    }

    public static List<KnowledgeAi.NodeSummary> summarize(List<KnowledgeAi.SummaryInput> inputs) {
        return inputs.stream().map(n -> new KnowledgeAi.NodeSummary(n.key(), "Summary of " + n.path(),
                n.path().contains("Osmosis") ? List.of("osmosis", "membrane") : List.of("cell"))).toList();
    }

    /** Deterministic hashed bag-of-words vectors, so semantic search behaves sensibly in tests. */
    public static List<float[]> embed(List<KnowledgeAi.EmbedInput> items) {
        return items.stream().map(i -> {
            float[] v = new float[768];
            String text = i.text() == null ? "" : i.text().toLowerCase(Locale.ROOT);
            for (String word : text.split("\\W+")) {
                if (word.length() > 3) {
                    v[Math.floorMod(word.hashCode(), 768)] += 1f;
                }
            }
            v[767] += 0.01f; // never a zero vector
            return v;
        }).toList();
    }

    // ---------------------------------------------------------------- study AI

    /** Topic 1 = chapter 1 (and its section), topic 2 = chapter 2: built from the outline the backend sends. */
    public static StudyAi.MaterialDraft material(StudyAi.MaterialSpec spec) {
        List<String> chapter1 = spec.nodes().stream().filter(n -> !n.title().equals("Energy"))
                .map(StudyAi.OutlineNode::key).toList();
        List<String> chapter2 = spec.nodes().stream().filter(n -> n.title().equals("Energy"))
                .map(StudyAi.OutlineNode::key).toList();
        return new StudyAi.MaterialDraft("Intro Biology", "Cells and energy.", "en", MaterialKind.TEXTBOOK, List.of(
                new StudyAi.TopicDraft(1, "Cells and osmosis", "What cells are", List.of("cell", "osmosis"),
                        TopicDifficulty.INTRO, chapter1),
                new StudyAi.TopicDraft(2, "Energy", "Respiration", List.of("ATP"), TopicDifficulty.CORE, chapter2)),
                "fake-model");
    }

    /** Wires the mocked AIs with the canned behaviour above (call from @BeforeEach). */
    public static void stub(KnowledgeAi knowledge, StudyAi study) {
        // Null-safe: Mockito replays existing answers with null arguments when a test re-stubs with when(...).
        when(knowledge.describeFigures(any(), any()))
                .thenAnswer(inv -> inv.getArgument(1) == null ? null : describe(inv.getArgument(1)));
        when(knowledge.summarize(any(), any()))
                .thenAnswer(inv -> inv.getArgument(1) == null ? null : summarize(inv.getArgument(1)));
        when(knowledge.embed(any(), any()))
                .thenAnswer(inv -> inv.getArgument(1) == null ? null : embed(inv.getArgument(1)));
        when(study.buildMaterial(any()))
                .thenAnswer(inv -> inv.getArgument(0) == null ? null : material(inv.getArgument(0)));
    }

    public static StudyAi.LessonDraft lesson(String figureId) {
        return new StudyAi.LessonDraft(
                List.of(new StudyAi.SectionDraft("CONCEPT", "What is osmosis?",
                                "Water moves [§1.1].\n\n![Osmosis](figure:" + figureId + ")"),
                        new StudyAi.SectionDraft("SUMMARY", "Recap", "In short...")),
                List.of("Water moves to the concentrated side"),
                List.of(new StudyAi.CheckDraft(-1, QuestionType.MULTIPLE_CHOICE, "Guess: water moves?",
                                List.of("to concentrated", "to dilute", "nowhere"), 0, "to concentrated", "Osmosis"),
                        new StudyAi.CheckDraft(0, QuestionType.TRUE_FALSE, "Osmosis needs ATP.",
                                List.of("True", "False"), 1, "False", "It is passive"),
                        new StudyAi.CheckDraft(1, QuestionType.SHORT_ANSWER, "Explain osmosis.",
                                List.of(), null, "Water diffuses across a membrane.", "Model answer")),
                List.of(figureId), "fake-model");
    }

    public static StudyAi.QuestionsDraft questions(String topic1, String topic2) {
        return new StudyAi.QuestionsDraft(List.of(
                new StudyAi.QuestionDraft(topic1, QuestionType.MULTIPLE_CHOICE, "D1?",
                        List.of("a", "b", "c", "d"), 1, "b", "because", List.of("§1.1")),
                new StudyAi.QuestionDraft(topic1, QuestionType.TRUE_FALSE, "D2?", List.of("True", "False"),
                        0, "True", "because", List.of("§1")),
                new StudyAi.QuestionDraft(topic2, QuestionType.SHORT_ANSWER, "S1?", List.of(), null,
                        "Mitochondria make ATP", "because", List.of("§2")),
                new StudyAi.QuestionDraft("unknown-topic", QuestionType.TRUE_FALSE, "X?", List.of("True", "False"),
                        0, "True", "dropped", List.of())),
                "fake-model");
    }

    public static StudyAi.GradeResult grade(Verdict verdict, double score) {
        return new StudyAi.GradeResult(verdict, score, "Good thinking", "fake-model");
    }

    public static StudyAi.ReportNarrative narrative() {
        return new StudyAi.ReportNarrative("Well done.", List.of("Cells"),
                List.of(new StudyAi.WeakArea("Energy", "Revisit respiration")), List.of("Review energy"), "fake-model");
    }

    public static StudyAi.AnswerDraft answer(String figureId) {
        return new StudyAi.AnswerDraft("Osmosis moves water [§1.1].\n\n![Osmosis](figure:" + figureId + ")", true,
                List.of("§1.1"), List.of(figureId), "fake-model");
    }

    public static StudyAi.NotesDraft notes(String text) {
        return new StudyAi.NotesDraft(text + " [§1.1]", List.of(), List.of("§1.1"), "fake-model");
    }

    public static byte[] utf8(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }
}
