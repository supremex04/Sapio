package com.sapio.visualization;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/** Rows of {@code visualizations}. */
@Repository
class VisualizationStore {

    enum Status { PLANNING, CODING, RENDERING, READY, FAILED;

        boolean working() {
            return this == PLANNING || this == CODING || this == RENDERING;
        }
    }

    record Row(UUID id, UUID userId, String request, String requestHash, UUID documentId, String context, Status status,
               String title, String kind, String storyboardJson, String code, int attempts, String error,
               String videoKey, String thumbnailKey, Double durationSeconds, boolean narrated, String model,
               Instant createdAt,
               Instant updatedAt, Instant completedAt) {
    }

    private final JdbcTemplate jdbc;

    VisualizationStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    void insert(UUID id, UUID userId, String request, String hash, UUID documentId, String context, Instant now) {
        jdbc.update("""
                        INSERT INTO visualizations (id, user_id, request, request_hash, document_id, context, status,
                                                    created_at, updated_at)
                        VALUES (?, ?, ?, ?, ?, ?, 'PLANNING', ?, ?)""",
                id, userId, request, hash, documentId, context, Timestamp.from(now), Timestamp.from(now));
    }

    Optional<Row> find(UUID id) {
        return jdbc.query("SELECT * FROM visualizations WHERE id = ?", ROW, id).stream().findFirst();
    }

    Optional<Row> find(UUID userId, UUID id) {
        return find(id).filter(r -> r.userId().equals(userId));
    }

    List<Row> list(UUID userId, int limit) {
        return jdbc.query("SELECT * FROM visualizations WHERE user_id = ? ORDER BY created_at DESC LIMIT ?", ROW,
                userId, limit);
    }

    /** The latest one for the same request that is finished or still being made (reused instead of a new one). */
    Optional<Row> reusable(UUID userId, String hash) {
        return jdbc.query("SELECT * FROM visualizations WHERE user_id = ? AND request_hash = ? AND status <> 'FAILED' "
                + "ORDER BY created_at DESC LIMIT 1", ROW, userId, hash).stream().findFirst();
    }

    int countSince(UUID userId, Instant since) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM visualizations WHERE user_id = ? AND created_at >= ?",
                Integer.class, userId, Timestamp.from(since));
        return n == null ? 0 : n;
    }

    List<UUID> working() {
        return jdbc.queryForList("SELECT id FROM visualizations WHERE status IN ('PLANNING', 'CODING', 'RENDERING')",
                UUID.class);
    }

    void storyboard(UUID id, String title, String kind, String storyboardJson, String model, Instant now) {
        jdbc.update("UPDATE visualizations SET status = 'CODING', title = ?, kind = ?, storyboard_json = ?, model = ?, "
                + "updated_at = ? WHERE id = ?", title, kind, storyboardJson, model, Timestamp.from(now), id);
    }

    void code(UUID id, String code, Instant now) {
        jdbc.update("UPDATE visualizations SET status = 'RENDERING', code = ?, updated_at = ? WHERE id = ?", code,
                Timestamp.from(now), id);
    }

    void attemptFailed(UUID id, String log, Instant now) {
        jdbc.update("UPDATE visualizations SET attempts = attempts + 1, error = ?, updated_at = ? WHERE id = ?", log,
                Timestamp.from(now), id);
    }

    void ready(UUID id, String videoKey, String thumbnailKey, double duration, boolean narrated, Instant now) {
        jdbc.update("""
                        UPDATE visualizations SET status = 'READY', attempts = attempts + 1, error = NULL, video_key = ?,
                               thumbnail_key = ?, duration_seconds = ?, narrated = ?, updated_at = ?, completed_at = ?
                        WHERE id = ?""",
                videoKey, thumbnailKey, duration, narrated, Timestamp.from(now), Timestamp.from(now), id);
    }

    void failed(UUID id, String reason, Instant now) {
        jdbc.update("UPDATE visualizations SET status = 'FAILED', error = ?, updated_at = ?, completed_at = ? WHERE id = ?",
                reason, Timestamp.from(now), Timestamp.from(now), id);
    }

    /** Starts over (a retry): keeps the request, forgets the plan, code and result. */
    void restart(UUID id, Instant now) {
        jdbc.update("""
                        UPDATE visualizations SET status = 'PLANNING', title = NULL, kind = NULL, storyboard_json = NULL,
                               code = NULL, attempts = 0, error = NULL, video_key = NULL, thumbnail_key = NULL,
                               duration_seconds = NULL, narrated = FALSE, completed_at = NULL, updated_at = ?
                        WHERE id = ?""", Timestamp.from(now), id);
    }

    void delete(UUID id) {
        jdbc.update("DELETE FROM visualizations WHERE id = ?", id);
    }

    private static final RowMapper<Row> ROW = VisualizationStore::map;

    private static Row map(ResultSet rs, int i) throws SQLException {
        double duration = rs.getDouble("duration_seconds");
        Double d = rs.wasNull() ? null : duration;
        return new Row(rs.getObject("id", UUID.class), rs.getObject("user_id", UUID.class), rs.getString("request"),
                rs.getString("request_hash"), rs.getObject("document_id", UUID.class), rs.getString("context"),
                Status.valueOf(rs.getString("status")), rs.getString("title"), rs.getString("kind"),
                rs.getString("storyboard_json"), rs.getString("code"), rs.getInt("attempts"), rs.getString("error"),
                rs.getString("video_key"), rs.getString("thumbnail_key"), d, rs.getBoolean("narrated"),
                rs.getString("model"),
                rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant(),
                rs.getTimestamp("completed_at") == null ? null : rs.getTimestamp("completed_at").toInstant());
    }
}
