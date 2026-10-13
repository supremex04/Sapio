package com.sapio.study.overview;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.sapio.study.assess.AssessmentService;
import com.sapio.study.learn.TopicProgress;
import com.sapio.study.learn.TopicProgressRepository;
import com.sapio.study.learn.TopicStatus;
import com.sapio.study.material.Mastery;
import com.sapio.study.material.MaterialAccess;
import com.sapio.study.material.MaterialResponse;
import com.sapio.study.material.StudyMaterial;
import com.sapio.study.material.StudyMaterialRepository;
import com.sapio.study.material.StudyTopic;
import com.sapio.study.material.StudyTopicRepository;
import com.sapio.study.material.TopicRecommender;
import com.sapio.study.question.QuestionAttemptRepository;
import com.sapio.study.question.ReviewCardRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read models that combine content, learner state and recommendations: the study hub for one material,
 * and the single "next best step" across everything the learner studies.
 */
@Service
public class StudyOverviewService {

    private final MaterialAccess access;
    private final StudyMaterialRepository materials;
    private final StudyTopicRepository topics;
    private final TopicProgressRepository progress;
    private final QuestionAttemptRepository attempts;
    private final ReviewCardRepository cards;
    private final AssessmentService assessments;
    private final Clock clock;

    StudyOverviewService(MaterialAccess access, StudyMaterialRepository materials, StudyTopicRepository topics,
                         TopicProgressRepository progress, QuestionAttemptRepository attempts,
                         ReviewCardRepository cards, AssessmentService assessments, Clock clock) {
        this.access = access;
        this.materials = materials;
        this.topics = topics;
        this.progress = progress;
        this.attempts = attempts;
        this.cards = cards;
        this.assessments = assessments;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public MaterialResponse material(UUID userId, UUID documentId) {
        StudyMaterial material = access.requireByDocument(userId, documentId);
        List<StudyTopic> path = topics.findByMaterialIdOrderByPosition(material.getId());
        Map<UUID, TopicProgress> state = progressOf(userId, path);
        Map<UUID, TopicStatus> status = new HashMap<>();
        state.forEach((id, p) -> status.put(id, p.getStatus()));
        Map<UUID, Double> mastery = Mastery.byTopic(attempts.findTopicScores(userId, material.getId()));
        Optional<UUID> recommended = TopicRecommender.recommend(path, status, mastery);

        List<MaterialResponse.TopicView> views = path.stream().map(t -> new MaterialResponse.TopicView(
                t.getId(), t.getPosition(), t.getTitle(), t.getSummary(), t.getKeyConcepts(), t.getDifficulty(),
                status.getOrDefault(t.getId(), TopicStatus.NOT_STARTED),
                state.containsKey(t.getId()) ? state.get(t.getId()).getCompletedAt() : null,
                mastery.get(t.getId()), recommended.map(t.getId()::equals).orElse(false))).toList();
        AssessmentService.Stats stats = assessments.stats(userId, material.getId());
        int completed = (int) status.values().stream().filter(s -> s == TopicStatus.COMPLETED).count();
        return new MaterialResponse(material.getDocumentId(), material.getTitle(), material.getSummary(),
                material.getLanguage(), material.getKind(), views, new MaterialResponse.Stats(path.size(), completed,
                stats.completed(), stats.bestScore(),
                cards.countDueForMaterial(userId, clock.instant(), material.getId())));
    }

    /**
     * One recommended action, ordered by learning value: due reviews (spacing), an unfinished topic
     * (Zeigarnik), the next topic on the most recently studied path, a test once a path is finished
     * (retrieval practice), else upload.
     */
    @Transactional(readOnly = true)
    public NextStep nextStep(UUID userId) {
        Instant now = clock.instant();
        long due = cards.countByUserIdAndDueAtLessThanEqual(userId, now);
        if (due > 0) {
            return new NextStep(NextStep.Kind.REVIEW, null, null,
                    "Recall " + due + (due == 1 ? " card" : " cards"),
                    "A quick recall keeps what you learned from fading",
                    (int) Math.clamp((due + 1) / 2, 1, 15));
        }
        List<StudyMaterial> owned = materials.findByOwnerId(userId);
        if (owned.isEmpty()) {
            return new NextStep(NextStep.Kind.UPLOAD, null, null, "Bring your first document",
                    "A syllabus, textbook chapter or lecture notes. Sapio turns it into lessons and practice in a minute or two.",
                    1);
        }
        Map<UUID, StudyMaterial> byId = owned.stream()
                .collect(Collectors.toMap(StudyMaterial::getId, Function.identity()));
        List<StudyTopic> allTopics = topics.findByMaterialIdInOrderByPosition(byId.keySet());
        Map<UUID, StudyTopic> topicById = allTopics.stream()
                .collect(Collectors.toMap(StudyTopic::getId, Function.identity()));
        List<TopicProgress> recent = progress.findByUserIdOrderByUpdatedAtDesc(userId).stream()
                .filter(p -> topicById.containsKey(p.getTopicId())).toList();

        Optional<TopicProgress> unfinished = recent.stream()
                .filter(p -> p.getStatus() == TopicStatus.IN_PROGRESS).findFirst();
        if (unfinished.isPresent()) {
            StudyTopic t = topicById.get(unfinished.get().getTopicId());
            return new NextStep(NextStep.Kind.CONTINUE_TOPIC, byId.get(t.getMaterialId()).getDocumentId(), t.getId(),
                    "Continue: " + t.getTitle(), "You're partway through. Finishing it helps it stick", 5);
        }

        Map<UUID, TopicStatus> status = recent.stream()
                .collect(Collectors.toMap(TopicProgress::getTopicId, TopicProgress::getStatus, (a, b) -> a));
        Map<UUID, Instant> lastActive = new HashMap<>();
        recent.forEach(p -> lastActive.merge(topicById.get(p.getTopicId()).getMaterialId(), p.getUpdatedAt(),
                (a, b) -> a.isAfter(b) ? a : b));
        List<StudyMaterial> byRecency = owned.stream().sorted(Comparator.comparing(
                (StudyMaterial m) -> lastActive.getOrDefault(m.getId(), m.getCreatedAt())).reversed()).toList();

        for (StudyMaterial m : byRecency) {
            Optional<StudyTopic> next = allTopics.stream()
                    .filter(t -> t.getMaterialId().equals(m.getId()))
                    .filter(t -> status.getOrDefault(t.getId(), TopicStatus.NOT_STARTED) == TopicStatus.NOT_STARTED)
                    .findFirst();
            if (next.isPresent()) {
                return new NextStep(NextStep.Kind.START_TOPIC, m.getDocumentId(), next.get().getId(),
                        "Learn: " + next.get().getTitle(), "The next step on your path through " + m.getTitle(), 8);
            }
            if (assessments.stats(userId, m.getId()).completed() == 0) {
                return testStep(m, "You've covered every topic. Testing yourself is the best way to lock it in");
            }
        }
        return testStep(byRecency.getFirst(), "A fresh set of questions strengthens your weakest topics");
    }

    private static NextStep testStep(StudyMaterial m, String reason) {
        return new NextStep(NextStep.Kind.TAKE_TEST, m.getDocumentId(), null, "Test yourself: " + m.getTitle(),
                reason, 10);
    }

    private Map<UUID, TopicProgress> progressOf(UUID userId, List<StudyTopic> path) {
        return progress.findByUserIdAndTopicIdIn(userId, path.stream().map(StudyTopic::getId).toList()).stream()
                .collect(Collectors.toMap(TopicProgress::getTopicId, Function.identity()));
    }
}
