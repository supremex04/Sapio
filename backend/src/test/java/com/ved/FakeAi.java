package com.ved;

import java.util.List;

import com.ved.document.extraction.ExtractionResult;
import com.ved.study.ai.StudyAi;
import com.ved.study.material.MaterialKind;
import com.ved.study.material.TopicDifficulty;
import com.ved.study.question.QuestionType;
import com.ved.study.question.Verdict;

/** Canned AI / extraction outputs for integration tests. */
public final class FakeAi {

    private FakeAi() {
    }

    public static ExtractionResult extraction() {
        return new ExtractionResult("# Demand\n\nDemand falls as price rises.", "pymupdf", 2, 2, 0, List.of(
                new ExtractionResult.Segment(1, 1, "text", "# Demand\n\nDemand falls as price rises."),
                new ExtractionResult.Segment(2, 2, "text", "# Supply\n\nSupply rises with price.")));
    }

    public static StudyAi.MaterialDraft material() {
        return new StudyAi.MaterialDraft("Intro Economics", "Supply and demand basics.", "en",
                MaterialKind.LECTURE_NOTES,
                List.of(new StudyAi.ChunkDraft(1, "Demand", "Demand falls as price rises.", 1, 1),
                        new StudyAi.ChunkDraft(2, "Supply", "Supply rises with price.", 2, 2)),
                List.of(new StudyAi.TopicDraft(1, "Demand", "What demand is", List.of("law of demand"),
                                TopicDifficulty.INTRO, List.of(1)),
                        new StudyAi.TopicDraft(2, "Supply", "What supply is", List.of("law of supply"),
                                TopicDifficulty.CORE, List.of(2))),
                "fake-model");
    }

    public static StudyAi.LessonDraft lesson() {
        return new StudyAi.LessonDraft(
                List.of(new StudyAi.SectionDraft("CONCEPT", "What is demand?", "Demand is..."),
                        new StudyAi.SectionDraft("SUMMARY", "Recap", "In short...")),
                List.of("Price up, quantity demanded down"),
                List.of(new StudyAi.CheckDraft(-1, QuestionType.MULTIPLE_CHOICE, "Guess: price up means?",
                                List.of("buy less", "buy more", "no change"), 0, "buy less", "Law of demand"),
                        new StudyAi.CheckDraft(0, QuestionType.TRUE_FALSE, "Demand curves slope down.",
                                List.of("True", "False"), 0, "True", "Higher price, lower quantity"),
                        new StudyAi.CheckDraft(1, QuestionType.SHORT_ANSWER, "Explain the law of demand.",
                                List.of(), null, "As price rises, quantity demanded falls.", "Model answer")),
                "fake-model");
    }

    /** Questions whose topicKey is filled in by the caller (topic ids are only known at runtime). */
    public static StudyAi.QuestionsDraft questions(String demandTopicId, String supplyTopicId) {
        return new StudyAi.QuestionsDraft(List.of(
                new StudyAi.QuestionDraft(demandTopicId, QuestionType.MULTIPLE_CHOICE, "D1?",
                        List.of("a", "b", "c", "d"), 1, "b", "because", List.of(1)),
                new StudyAi.QuestionDraft(demandTopicId, QuestionType.TRUE_FALSE, "D2?", List.of("True", "False"),
                        0, "True", "because", List.of(1)),
                new StudyAi.QuestionDraft(supplyTopicId, QuestionType.SHORT_ANSWER, "S1?", List.of(), null,
                        "Supply rises with price", "because", List.of(2)),
                new StudyAi.QuestionDraft("unknown-topic", QuestionType.TRUE_FALSE, "X?", List.of("True", "False"),
                        0, "True", "dropped", List.of())),
                "fake-model");
    }

    public static StudyAi.GradeResult grade(Verdict verdict, double score) {
        return new StudyAi.GradeResult(verdict, score, "Good thinking", "fake-model");
    }

    public static StudyAi.ReportNarrative narrative() {
        return new StudyAi.ReportNarrative("Well done.", List.of("Demand"),
                List.of(new StudyAi.WeakArea("Supply", "Revisit supply")), List.of("Review supply"), "fake-model");
    }
}
