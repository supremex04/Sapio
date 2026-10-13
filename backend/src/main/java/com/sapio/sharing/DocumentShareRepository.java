package com.sapio.sharing;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface DocumentShareRepository extends JpaRepository<DocumentShare, UUID> {

    Optional<DocumentShare> findByDocumentId(UUID documentId);

    List<DocumentShare> findByOwnerIdOrderByCreatedAtDesc(UUID ownerId);

    @Modifying
    @Query("update DocumentShare s set s.importCount = s.importCount + 1 where s.id = :id")
    int countImport(@Param("id") UUID id);
}
