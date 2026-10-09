package com.ved.progress;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

interface UserPresenceRepository extends JpaRepository<UserPresence, UUID> {

    /** @return 1 if a row was created (first heartbeat ever), 0 if it already existed */
    @Modifying
    @Query(nativeQuery = true, value = """
            INSERT INTO user_presence (user_id, last_heartbeat_at) VALUES (:userId, :now)
            ON CONFLICT (user_id) DO NOTHING""")
    int insertIfAbsent(UUID userId, Instant now);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from UserPresence p where p.userId = :userId")
    Optional<UserPresence> findForUpdate(UUID userId);
}
