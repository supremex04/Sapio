package com.ved.study.assess;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import com.ved.study.ai.StudyAi;
import com.ved.study.ai.StudyAiException;
import com.ved.study.assess.AssessmentReport.Calibration;
import com.ved.study.assess.AssessmentReport.Level;
import com.ved.study.assess.AssessmentReport.TopicReport;
import com.ved.study.assess.AssessmentReport.WeakArea;
import com.ved.study.material.StudyMaterial;
import com.ved.study.material.StudyTopic;
import com.ved.study.question.Confidence;
import com.ved.study.question.QuestionAttempt;
import com.ved.study.question.StudyQuestion;
import com.ved.study.question.Verdict;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Computes the deterministic part of a report (scores, per-topic levels, calibration) and asks the AI
 * for the narrative, falling back to a rule-based narrative if the AI is unavailable.
 */
@Component
class ReportBuilder {

    private static final Logger log = LoggerFactory.getLogger(ReportBuilder.class);

    private final StudyAi ai;

    ReportBuilder(StudyAi ai) {
        this.ai = ai;
    }

    /** One assessment item with its (possibly missing) attempt. */
    record Answered(StudyQuestion question, StudyTopic topic, QuestionAttempt attempt) {
        double score() {
            return attempt == null ? 0 : attempt.getScore();
        }
    }

    AssessmentReport build(StudyMaterial material, List<Answered> items) {
        int correct = 0;
        int partial = 0;
        int incorrect = 0;
        int skipped = 0;
        int[] calibration = new int[4];
        Map<UUID, List<Answered>> byTopic = new LinkedHashMap<>();
        for (Answered a : items) {
            byTopic.computeIfAbsent(a.topic().getId(), k -> new ArrayList<>()).add(a);
            if (a.attempt() == null) {
                skipped++;
                continue;
            }
            Verdict v = a.attempt().getVerdict();
            switch (v) {
                case CORRECT -> correct++;
                case PARTIAL -> partial++;
                case INCORRECT -> incorrect++;
            }
            Confidence c = a.attempt().getConfidence();
            if (c != null) {
                boolean confident = c != Confidence.LOW;
                boolean right = v == Verdict.CORRECT;
                calibration[(confident ? 0 : 2) + (right ? 0 : 1)]++;
            }
        }
        double score = round(items.stream().mapToDouble(Answered::score).average().orElse(0));
        List<TopicReport> topics = byTopic.values().stream().map(list -> {
            double s = round(list.stream().mapToDouble(Answered::score).average().orElse(0));
            StudyTopic t = list.getFirst().topic();
            return new TopicReport(t.getId(), t.getTitle(), list.size(), s, Level.of(s));
        }).toList();

        Narrative narrative = narrative(material, score, topics, items);
        return new AssessmentReport(score, correct, partial, incorrect, skipped, topics, narrative.summary(),
                narrative.strengths(), narrative.weakAreas(), narrative.nextSteps(),
                new Calibration(calibration[0], calibration[1], calibration[2], calibration[3]));
    }

    private record Narrative(String summary, List<String> strengths, List<WeakArea> weakAreas,
                             List<String> nextSteps) {
    }

    private Narrative narrative(StudyMaterial material, double score, List<TopicReport> topics,
                                List<Answered> items) {
        List<StudyAi.MissedQuestion> missed = items.stream()
                .filter(a -> a.attempt() == null || a.attempt().getVerdict() != Verdict.CORRECT)
                .map(a -> new StudyAi.MissedQuestion(a.topic().getTitle(), a.question().getPrompt(),
                        learnerAnswer(a), a.question().correctAnswerText()))
                .toList();
        try {
            StudyAi.ReportNarrative n = ai.assessmentFeedback(new StudyAi.FeedbackSpec(material.getTitle(),
                    material.getLanguage(), score,
                    topics.stream().map(t -> new StudyAi.TopicResult(t.title(), t.questionCount(), t.score()))
                            .toList(),
                    missed));
            Map<String, UUID> ids = new LinkedHashMap<>();
            topics.forEach(t -> ids.put(t.title().toLowerCase(Locale.ROOT), t.topicId()));
            List<WeakArea> weak = n.weakAreas().stream().map(w -> new WeakArea(
                    ids.get(w.topicTitle().toLowerCase(Locale.ROOT)), w.topicTitle(), w.advice())).toList();
            return new Narrative(n.summary(), n.strengths(), weak, n.nextSteps());
        } catch (StudyAiException e) {
            log.warn("Report narrative unavailable, using fallback: {}", e.getMessage());
            return fallback(score, topics);
        }
    }

    static Narrative fallback(double score, List<TopicReport> topics) {
        List<String> strengths = topics.stream().filter(t -> t.level() == Level.STRONG)
                .map(t -> "You have a solid grasp of " + t.title() + ".").toList();
        List<WeakArea> weak = topics.stream().filter(t -> t.level() == Level.WEAK)
                .map(t -> new WeakArea(t.topicId(), t.title(),
                        "Revisit this lesson, then try explaining the main idea in your own words."))
                .toList();
        String summary = "You scored " + Math.round(score * 100) + "%. "
                + (weak.isEmpty() ? "Keep the momentum going with spaced review."
                : "Focusing on your weaker topics next will give you the biggest gains.");
        return new Narrative(summary, strengths, weak, List.of(
                "Do your spaced reviews when they are due.", "Retake a test in a few days to lock it in."));
    }

    private static String learnerAnswer(Answered a) {
        if (a.attempt() == null) {
            return "";
        }
        Integer option = a.attempt().getSelectedOption();
        return option != null ? a.question().getOptions().get(option) : a.attempt().getAnswerText();
    }

    private static double round(double value) {
        return Math.round(value * 1000) / 1000.0;
    }
}
