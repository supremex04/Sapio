package com.sapio.progress;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

interface LearningEventRepository extends JpaRepository<LearningEventRecord, UUID> {

    List<LearningEventRecord> findByUserIdAndTypeNotInOrderByOccurredAtDesc(UUID userId, List<String> excluded,
                                                                             Limit limit);

    long countByUserIdAndType(UUID userId, String type);
}
