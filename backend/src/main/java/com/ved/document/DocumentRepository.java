package com.ved.document;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentRepository extends JpaRepository<Document, UUID> {

    Optional<Document> findByIdAndOwnerId(UUID id, UUID ownerId);

    Page<Document> findByOwnerIdOrderByCreatedAtDesc(UUID ownerId, Pageable pageable);

    List<Document> findByStatusIn(Collection<DocumentStatus> statuses);
}
