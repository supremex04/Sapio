package com.ved.study.question;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ReviewCardRepository extends JpaRepository<ReviewCard, UUID> {

    Optional<ReviewCard> findByUserIdAndQuestionId(UUID userId, UUID questionId);

    long countByUserIdAndDueAtLessThanEqual(UUID userId, Instant at);

    long countByUserId(UUID userId);

    List<ReviewCard> findByUserIdAndDueAtLessThanEqualOrderByDueAt(UUID userId, Instant at, Limit limit);

    @Query("""
            select c from ReviewCard c join StudyQuestion q on q.id = c.questionId
            join StudyMaterial m on m.id = q.materialId
            where c.userId = :userId and c.dueAt <= :at and m.documentId = :documentId
            order by c.dueAt""")
    List<ReviewCard> findDueForDocument(UUID userId, Instant at, UUID documentId, Limit limit);

    @Query("""
            select count(c) from ReviewCard c join StudyQuestion q on q.id = c.questionId
            where c.userId = :userId and c.dueAt <= :at and q.materialId = :materialId""")
    long countDueForMaterial(UUID userId, Instant at, UUID materialId);
}
