package com.ved.study.assess;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface AssessmentItemRepository extends JpaRepository<AssessmentItem, UUID> {

    List<AssessmentItem> findByAssessmentIdOrderByPosition(UUID assessmentId);

    Optional<AssessmentItem> findByAssessmentIdAndPosition(UUID assessmentId, int position);

    long countByAssessmentIdAndAttemptIdIsNotNull(UUID assessmentId);
}
