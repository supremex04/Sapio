package com.ved.document;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DocumentRepository extends JpaRepository<Document, UUID> {

    Optional<Document> findByIdAndOwnerId(UUID id, UUID ownerId);

    Page<Document> findByOwnerIdOrderByCreatedAtDesc(UUID ownerId, Pageable pageable);

    List<Document> findByStatusAndRetryAtLessThanEqual(DocumentStatus status, Instant at);

    /** Row-locks the document for the rest of the transaction, so only one instance can claim it. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from Document d where d.id = :id")
    Optional<Document> lockById(@Param("id") UUID id);

    /** Documents in these states whose owner has shown no sign of life since {@code cutoff}. */
    @Query("select d from Document d where d.status in :statuses and coalesce(d.heartbeatAt, d.updatedAt) < :cutoff")
    List<Document> findAbandoned(@Param("statuses") Collection<DocumentStatus> statuses,
                                 @Param("cutoff") Instant cutoff);

    @Modifying
    @Query("update Document d set d.heartbeatAt = :at where d.id in :ids")
    int heartbeat(@Param("ids") Collection<UUID> ids, @Param("at") Instant at);
}
