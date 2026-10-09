package com.ved.study.material;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface StudyTopicRepository extends JpaRepository<StudyTopic, UUID> {

    List<StudyTopic> findByMaterialIdOrderByPosition(UUID materialId);

    List<StudyTopic> findByMaterialIdInOrderByPosition(Collection<UUID> materialIds);
}
