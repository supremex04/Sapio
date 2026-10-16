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

    /**
     * {@code engineChoice}: what was asked for (AUTO unless the learner chose); {@code engine}: what is used, once
     * planned. {@code planJson}: the engine's data from the storyboard; {@code sceneJson}: what the viewer shows.
     */
    record Row(UUID id, UUID userId, String request, String requestHash, UUID documentId, String context, Status status,
               VisualizationEngine engineChoice, VisualizationEngine engine, String title, String kind,
               String storyboardJson, String planJson, String sceneJson, String code, int attempts, String error,
               String videoKey, String thumbnailKey, String audioKey, String structureKey, Double durationSeconds,
               boolean narrated, String model, Instant createdAt, Instant updatedAt, Instant completedAt) {
    }

    /** What a non-video engine produced; null keys: none. */
    record Scene(String sceneJson, String audioKey, String structureKey, String thumbnailKey, double durationSeconds,
                 boolean narrated) {
    }

    private static final RowMapper<Row> ROW = VisualizationStore::map;

    private final JdbcTemplate jdbc;

    VisualizationStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    void insert(UUID id, UUID userId, String request, String hash, UUID documentId, String context,
                VisualizationEngine engineChoice, Instant now) {
        jdbc.update("""
                        INSERT INTO visualizations (id, user_id, request, request_hash, document_id, context, status,
                                                    engine_choice, created_at, updated_at)
                        VALUES (?, ?, ?, ?, ?, ?, 'PLANNING', ?, ?, ?)""",
                id, userId, request, hash, documentId, context, engineChoice.name(), Timestamp.from(now),
                Timestamp.from(now));
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

    /** Planned: an animation goes on to CODING, the other engines straight to RENDERING (building their scene). */
    void storyboard(UUID id, VisualizationEngine engine, String title, String kind, String storyboardJson,
                    String planJson, String model, Instant now) {
        Status next = engine == VisualizationEngine.ANIMATION ? Status.CODING : Status.RENDERING;
        jdbc.update("UPDATE visualizations SET status = ?, engine = ?, title = ?, kind = ?, storyboard_json = ?, "
                        + "plan_json = ?, model = ?, updated_at = ? WHERE id = ?",
                next.name(), engine.name(), title, kind, storyboardJson, planJson, model, Timestamp.from(now), id);
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

    void ready(UUID id, Scene scene, Instant now) {
        jdbc.update("""
                        UPDATE visualizations SET status = 'READY', attempts = attempts + 1, error = NULL,
                               scene_json = ?, audio_key = ?, structure_key = ?, thumbnail_key = ?,
                               duration_seconds = ?, narrated = ?, updated_at = ?, completed_at = ?
                        WHERE id = ?""",
                scene.sceneJson(), scene.audioKey(), scene.structureKey(), scene.thumbnailKey(),
                scene.durationSeconds(), scene.narrated(), Timestamp.from(now), Timestamp.from(now), id);
    }

    void failed(UUID id, String reason, Instant now) {
        jdbc.update("UPDATE visualizations SET status = 'FAILED', error = ?, updated_at = ?, completed_at = ? WHERE id = ?",
                reason, Timestamp.from(now), Timestamp.from(now), id);
    }

    /** Starts over (a retry): keeps the request and the engine asked for, forgets the plan, code and result. */
    void restart(UUID id, Instant now) {
        jdbc.update("""
                        UPDATE visualizations SET status = 'PLANNING', engine = NULL, title = NULL, kind = NULL,
                               storyboard_json = NULL, plan_json = NULL, scene_json = NULL, code = NULL, attempts = 0,
                               error = NULL, video_key = NULL, thumbnail_key = NULL, audio_key = NULL,
                               structure_key = NULL, duration_seconds = NULL, narrated = FALSE, completed_at = NULL,
                               updated_at = ?
                        WHERE id = ?""", Timestamp.from(now), id);
    }

    void delete(UUID id) {
        jdbc.update("DELETE FROM visualizations WHERE id = ?", id);
    }

    private static Row map(ResultSet rs, int i) throws SQLException {
        double duration = rs.getDouble("duration_seconds");
        Double d = rs.wasNull() ? null : duration;
        String engine = rs.getString("engine");
        return new Row(rs.getObject("id", UUID.class), rs.getObject("user_id", UUID.class), rs.getString("request"),
                rs.getString("request_hash"), rs.getObject("document_id", UUID.class), rs.getString("context"),
                Status.valueOf(rs.getString("status")), VisualizationEngine.valueOf(rs.getString("engine_choice")),
                engine == null ? null : VisualizationEngine.valueOf(engine), rs.getString("title"),
                rs.getString("kind"), rs.getString("storyboard_json"), rs.getString("plan_json"),
                rs.getString("scene_json"), rs.getString("code"), rs.getInt("attempts"), rs.getString("error"),
                rs.getString("video_key"), rs.getString("thumbnail_key"), rs.getString("audio_key"),
                rs.getString("structure_key"), d, rs.getBoolean("narrated"), rs.getString("model"),
                rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant(),
                rs.getTimestamp("completed_at") == null ? null : rs.getTimestamp("completed_at").toInstant());
    }
}
