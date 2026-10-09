package com.ved.document;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface DocumentSegmentRepository extends JpaRepository<DocumentSegment, UUID> {

    List<DocumentSegment> findByDocumentIdOrderByPosition(UUID documentId);

    @Modifying
    @Query("delete from DocumentSegment s where s.documentId = :documentId")
    void deleteByDocumentId(UUID documentId);
}
