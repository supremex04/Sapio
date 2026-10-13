package com.sapio.document;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.sapio.document.extraction.Transcript;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** The stored transcript of an audio / video document (written once, read when notes are (re)built). */
@Component
public class DocumentTranscripts {

    private static final TypeReference<List<Transcript.Segment>> SEGMENTS = new TypeReference<>() {
    };

    private final JdbcTemplate jdbc;
    private final JsonMapper json;
    private final Clock clock;

    DocumentTranscripts(JdbcTemplate jdbc, JsonMapper json, Clock clock) {
        this.jdbc = jdbc;
        this.json = json;
        this.clock = clock;
    }

    public Optional<Transcript> find(UUID documentId) {
        return jdbc.query("""
                        SELECT language, duration_seconds, engine, segments_json
                        FROM document_transcripts WHERE document_id = ?""",
                (rs, i) -> new Transcript(rs.getString(1), rs.getDouble(2), rs.getString(3),
                        json.readValue(rs.getString(4), SEGMENTS)), documentId).stream().findFirst();
    }

    /** Replaces any earlier transcript of the document. */
    public void save(UUID documentId, Transcript transcript) {
        jdbc.update("""
                        INSERT INTO document_transcripts (document_id, language, duration_seconds, engine, segments_json,
                                                          created_at)
                        VALUES (?, ?, ?, ?, ?, ?)
                        ON CONFLICT (document_id) DO UPDATE SET language = EXCLUDED.language,
                            duration_seconds = EXCLUDED.duration_seconds, engine = EXCLUDED.engine,
                            segments_json = EXCLUDED.segments_json, created_at = EXCLUDED.created_at""",
                documentId, transcript.language(), transcript.durationSeconds(), transcript.engine(),
                json.writeValueAsString(transcript.segments()), Timestamp.from(clock.instant()));
    }
}
