package com.sapio.study.material;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface StudyMaterialRepository extends JpaRepository<StudyMaterial, UUID> {

    Optional<StudyMaterial> findByDocumentId(UUID documentId);

    List<StudyMaterial> findByOwnerId(UUID ownerId);

    @Modifying
    @Query("delete from StudyMaterial m where m.documentId = :documentId")
    void deleteByDocumentId(UUID documentId);
}
