package com.ved.study.question;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface QuestionAttemptRepository extends JpaRepository<QuestionAttempt, UUID> {

    Optional<QuestionAttempt> findFirstByUserIdAndQuestionIdOrderByCreatedAtDesc(UUID userId, UUID questionId);

    List<QuestionAttempt> findByUserIdAndQuestionIdInOrderByCreatedAtDesc(UUID userId, Collection<UUID> questionIds);

    boolean existsByUserIdAndQuestionIdAndVerdict(UUID userId, UUID questionId, Verdict verdict);

    /** (topicId, score) of the learner's attempts on a material, newest first. Feeds mastery. */
    @Query("""
            select new com.ved.study.question.TopicScore(q.topicId, a.score)
            from QuestionAttempt a join StudyQuestion q on q.id = a.questionId
            where a.userId = :userId and q.materialId = :materialId
            order by a.createdAt desc""")
    List<TopicScore> findTopicScores(UUID userId, UUID materialId);
}
