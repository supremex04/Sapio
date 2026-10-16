package com.sapio.study.question;

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
            select new com.sapio.study.question.TopicScore(q.topicId, a.score)
            from QuestionAttempt a join StudyQuestion q on q.id = a.questionId
            where a.userId = :userId and q.materialId = :materialId
            order by a.createdAt desc""")
    List<TopicScore> findTopicScores(UUID userId, UUID materialId);

    /**
     * Like {@link #findTopicScores}, but only answers to questions a teacher may see: lesson checks, and questions
     * of tests a teacher assigned. Questions of the learner's own tests (answered there or later in recall) stay
     * the learner's.
     */
    @Query("""
            select new com.sapio.study.question.TopicScore(q.topicId, a.score)
            from QuestionAttempt a join StudyQuestion q on q.id = a.questionId
            where a.userId = :userId and q.materialId = :materialId
              and (q.origin = com.sapio.study.question.QuestionOrigin.LESSON_CHECK
                   or exists (select 1 from AssessmentItem i, Assessment s
                              where i.questionId = q.id and s.id = i.assessmentId and s.assignedBy is not null))
            order by a.createdAt desc""")
    List<TopicScore> findTeacherVisibleTopicScores(UUID userId, UUID materialId);
}
