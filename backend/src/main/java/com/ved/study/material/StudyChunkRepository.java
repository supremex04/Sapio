package com.ved.study.material;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface StudyChunkRepository extends JpaRepository<StudyChunk, UUID> {

    List<StudyChunk> findByMaterialIdAndPositionInOrderByPosition(UUID materialId, Collection<Integer> positions);
}
