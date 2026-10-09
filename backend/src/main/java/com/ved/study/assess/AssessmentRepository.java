package com.ved.study.assess;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

interface AssessmentRepository extends JpaRepository<Assessment, UUID> {

    List<Assessment> findByUserIdAndMaterialIdOrderByCreatedAtDesc(UUID userId, UUID materialId);

    /** Row lock serialising answers/completion of one assessment. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Assessment a where a.id = :id")
    Optional<Assessment> findForUpdate(UUID id);

    long countByUserIdAndMaterialIdAndStatus(UUID userId, UUID materialId, AssessmentStatus status);
}
