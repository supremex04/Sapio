package com.sapio.progress;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "user_presence")
public class UserPresence {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "last_heartbeat_at", nullable = false)
    private Instant lastHeartbeatAt;

    protected UserPresence() {
        // for JPA
    }

    Instant getLastHeartbeatAt() {
        return lastHeartbeatAt;
    }

    void beat(Instant now) {
        this.lastHeartbeatAt = now;
    }
}
