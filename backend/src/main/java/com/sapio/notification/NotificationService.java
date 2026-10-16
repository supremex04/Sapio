package com.sapio.notification;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

/**
 * A user's inbox: short messages with an optional link, posted by any module (classroom invitations, shared
 * documents, assigned tests, notices, calendar events). Posting joins the caller's transaction.
 */
@Service
public class NotificationService {

    public enum Type {
        CLASS_INVITE,
        INVITE_ANSWERED,
        DOCUMENT_SHARED,
        TEST_ASSIGNED,
        NOTICE,
        EVENT
    }

    public record Notification(UUID id, Type type, String title, String body, String link, String actorName,
                               Instant createdAt, Instant readAt) {
    }

    private static final RowMapper<Notification> ROW = (rs, i) -> new Notification(rs.getObject("id", UUID.class),
            Type.valueOf(rs.getString("type")), rs.getString("title"), rs.getString("body"), rs.getString("link"),
            rs.getString("actor_name"), rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("read_at") == null ? null : rs.getTimestamp("read_at").toInstant());

    private final JdbcTemplate jdbc;
    private final Clock clock;

    NotificationService(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public void post(UUID userId, Type type, String title, String body, String link, String actorName) {
        jdbc.update("""
                        INSERT INTO notifications (id, user_id, type, title, body, link, actor_name, created_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?)""",
                UUID.randomUUID(), userId, type.name(), truncate(title, 300), body, link, actorName,
                Timestamp.from(clock.instant()));
    }

    public void postAll(List<UUID> userIds, Type type, String title, String body, String link, String actorName) {
        userIds.forEach(id -> post(id, type, title, body, link, actorName));
    }

    public List<Notification> recent(UUID userId, int limit) {
        return jdbc.query("SELECT * FROM notifications WHERE user_id = ? ORDER BY created_at DESC LIMIT ?", ROW,
                userId, Math.clamp(limit, 1, 100));
    }

    public int unread(UUID userId) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM notifications WHERE user_id = ? AND read_at IS NULL",
                Integer.class, userId);
        return n == null ? 0 : n;
    }

    public void markAllRead(UUID userId) {
        jdbc.update("UPDATE notifications SET read_at = ? WHERE user_id = ? AND read_at IS NULL",
                Timestamp.from(clock.instant()), userId);
    }

    private static String truncate(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }
}
