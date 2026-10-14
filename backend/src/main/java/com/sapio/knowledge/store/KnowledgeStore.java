package com.sapio.knowledge.store;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;

import com.sapio.document.extraction.ExtractionResult;
import com.sapio.knowledge.edit.SectionMarkdown;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * Persistence of the knowledge store: tree, leaf chunks, figures, term index, keyword and vector search.
 * Plain SQL (ltree, pgvector, regconfig, generated tsvector are outside what JPA maps well).
 * Schema: V4__knowledge_store.sql; design: docs/specs/knowledge.md.
 */
@Repository
public class KnowledgeStore {

    private final JdbcTemplate jdbc;

    KnowledgeStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ------------------------------------------------------------------------------- rows

    /** @param contentVersion bumped by significant learner edits (see V13__section_editing.sql) */
    public record NodeRow(UUID id, UUID documentId, UUID parentId, String path, String label, String title, int level,
                          int ordinal, int pageFrom, int pageTo, String summary, List<String> keyTerms,
                          int contentVersion) {
    }

    public record ChunkRow(UUID id, UUID nodeId, int ordinal, String content, int pageFrom, int pageTo) {
    }

    public record FigureRow(UUID id, UUID documentId, UUID nodeId, UUID chunkId, int ordinal, int page,
                            String storageKey, String mime, int width, int height, String caption, String description,
                            String kind) {
    }

    /** A ranked search candidate; {@code snippet} is highlighted with ** for keyword hits. */
    public record Ranked(UUID id, UUID nodeId, double score, String snippet) {
    }

    public record TermRow(String term, UUID nodeId) {
    }

    private static final String NODE_COLUMNS = "id, document_id, parent_id, path::text AS path, label, title, level, "
            + "ordinal, page_from, page_to, summary, key_terms, content_version";
    private static final String CHUNK_COLUMNS = "c.id, c.node_id, c.ordinal, c.content, c.page_from, c.page_to";
    private static final String FIGURE_COLUMNS = "f.id, f.document_id, f.node_id, f.chunk_id, f.ordinal, f.page, "
            + "f.storage_key, f.mime, f.width, f.height, f.caption, f.description, f.kind";

    private static final RowMapper<NodeRow> NODE = (rs, i) -> new NodeRow(rs.getObject("id", UUID.class),
            rs.getObject("document_id", UUID.class), rs.getObject("parent_id", UUID.class), rs.getString("path"),
            rs.getString("label"), rs.getString("title"), rs.getInt("level"), rs.getInt("ordinal"),
            rs.getInt("page_from"), rs.getInt("page_to"), rs.getString("summary"), strings(rs.getArray("key_terms")),
            rs.getInt("content_version"));
    private static final RowMapper<ChunkRow> CHUNK = (rs, i) -> new ChunkRow(rs.getObject("id", UUID.class),
            rs.getObject("node_id", UUID.class), rs.getInt("ordinal"), rs.getString("content"), rs.getInt("page_from"),
            rs.getInt("page_to"));
    private static final RowMapper<FigureRow> FIGURE = (rs, i) -> new FigureRow(rs.getObject("id", UUID.class),
            rs.getObject("document_id", UUID.class), rs.getObject("node_id", UUID.class),
            rs.getObject("chunk_id", UUID.class), rs.getInt("ordinal"), rs.getInt("page"), rs.getString("storage_key"),
            rs.getString("mime"), rs.getInt("width"), rs.getInt("height"), rs.getString("caption"),
            rs.getString("description"), rs.getString("kind"));

    // ------------------------------------------------------------------------------- ingest

    /**
     * Inserts a freshly extracted tree. Keys are mapped to new UUIDs; nodes arrive in pre-order, so parents
     * are always inserted before children. {@code figureFiles} maps figure key → storage key.
     */
    public void insertTree(UUID documentId, String searchConfig, ExtractionResult.Tree tree,
                           Map<String, String> figureFiles) {
        Map<String, UUID> nodeIds = new HashMap<>();
        tree.nodes().forEach(n -> nodeIds.put(n.key(), UUID.randomUUID()));
        Map<String, UUID> chunkIds = new HashMap<>();
        tree.chunks().forEach(c -> chunkIds.put(c.key(), UUID.randomUUID()));

        jdbc.batchUpdate("""
                        INSERT INTO document_nodes (id, document_id, parent_id, path, label, title, level, ordinal,
                                                    page_from, page_to)
                        VALUES (?, ?, ?, ?::ltree, ?, ?, ?, ?, ?, ?)""",
                tree.nodes(), 200, (ps, n) -> {
                    ps.setObject(1, nodeIds.get(n.key()));
                    ps.setObject(2, documentId);
                    ps.setObject(3, n.parentKey() == null ? null : nodeIds.get(n.parentKey()));
                    ps.setString(4, n.path());
                    ps.setString(5, truncate(n.label(), 120));
                    ps.setString(6, truncate(n.title(), 400));
                    ps.setInt(7, n.level());
                    ps.setInt(8, n.ordinal());
                    ps.setInt(9, n.pageFrom());
                    ps.setInt(10, n.pageTo());
                });
        jdbc.batchUpdate("""
                        INSERT INTO document_chunks (id, document_id, node_id, ordinal, content, page_from, page_to,
                                                     search_config)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?::regconfig)""",
                tree.chunks(), 500, (ps, c) -> {
                    ps.setObject(1, chunkIds.get(c.key()));
                    ps.setObject(2, documentId);
                    ps.setObject(3, nodeIds.get(c.nodeKey()));
                    ps.setInt(4, c.ordinal());
                    ps.setString(5, c.text());
                    ps.setInt(6, c.pageFrom());
                    ps.setInt(7, c.pageTo());
                    ps.setString(8, searchConfig);
                });
        jdbc.batchUpdate("""
                        INSERT INTO document_figures (id, document_id, node_id, chunk_id, ordinal, page, bbox,
                                                      storage_key, mime, width, height, caption, search_config)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::regconfig)""",
                tree.figures(), 200, (ps, f) -> {
                    ps.setObject(1, UUID.randomUUID());
                    ps.setObject(2, documentId);
                    ps.setObject(3, nodeIds.get(f.nodeKey()));
                    ps.setObject(4, f.chunkKey() == null ? null : chunkIds.get(f.chunkKey()));
                    ps.setInt(5, f.ordinal());
                    ps.setInt(6, f.page());
                    ps.setArray(7, f.bbox() == null ? null : ps.getConnection().createArrayOf("real", f.bbox().toArray()));
                    ps.setString(8, figureFiles.get(f.key()));
                    ps.setString(9, f.mime());
                    ps.setInt(10, f.width());
                    ps.setInt(11, f.height());
                    ps.setString(12, f.caption() == null ? "" : f.caption());
                    ps.setString(13, searchConfig);
                });
    }

    /** Removes a document's tree (cascades to chunks, figures, terms, notes). Returns the figure files to delete. */
    public List<String> deleteTree(UUID documentId) {
        List<String> files = jdbc.queryForList("SELECT storage_key FROM document_figures WHERE document_id = ?",
                String.class, documentId);
        jdbc.update("DELETE FROM document_terms WHERE document_id = ?", documentId);
        jdbc.update("DELETE FROM document_nodes WHERE document_id = ?", documentId);
        return files;
    }

    public boolean hasTree(UUID documentId) {
        Boolean exists = jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM document_nodes WHERE document_id = ?)",
                Boolean.class, documentId);
        return Boolean.TRUE.equals(exists);
    }

    // ------------------------------------------------------------------------------- nodes

    public List<NodeRow> nodes(UUID documentId) {
        return jdbc.query("SELECT " + NODE_COLUMNS + " FROM document_nodes WHERE document_id = ? ORDER BY ordinal",
                NODE, documentId);
    }

    public Optional<NodeRow> node(UUID nodeId) {
        return jdbc.query("SELECT " + NODE_COLUMNS + " FROM document_nodes WHERE id = ?", NODE, nodeId).stream()
                .findFirst();
    }

    public List<NodeRow> nodesWithoutSummary(UUID documentId) {
        return jdbc.query("SELECT " + NODE_COLUMNS + " FROM document_nodes WHERE document_id = ? AND summary IS NULL "
                + "ORDER BY level DESC, ordinal", NODE, documentId);
    }

    public void updateSummary(UUID nodeId, String summary, List<String> keyTerms) {
        jdbc.update(con -> {
            // The summary is now written from the section's current text.
            var ps = con.prepareStatement("UPDATE document_nodes SET summary = ?, key_terms = ?, "
                    + "summary_version = content_version WHERE id = ?");
            ps.setString(1, summary);
            ps.setArray(2, con.createArrayOf("text", keyTerms.toArray()));
            ps.setObject(3, nodeId);
            return ps;
        });
    }

    public void insertTerms(UUID documentId, UUID nodeId, Collection<String> terms) {
        jdbc.batchUpdate("INSERT INTO document_terms (document_id, term, node_id) VALUES (?, ?, ?) ON CONFLICT DO NOTHING",
                new ArrayList<>(terms), 100, (ps, term) -> {
                    ps.setObject(1, documentId);
                    ps.setString(2, truncate(term, 200));
                    ps.setObject(3, nodeId);
                });
    }

    public List<TermRow> terms(UUID documentId) {
        return jdbc.query("""
                        SELECT t.term, t.node_id FROM document_terms t JOIN document_nodes n ON n.id = t.node_id
                        WHERE t.document_id = ? ORDER BY lower(t.term), n.ordinal""",
                (rs, i) -> new TermRow(rs.getString("term"), rs.getObject("node_id", UUID.class)), documentId);
    }

    // ------------------------------------------------------------------------------- chunks

    /** Chunks of a node's whole subtree, in reading order. */
    public List<ChunkRow> chunksInSubtree(UUID documentId, String path) {
        return jdbc.query("SELECT " + CHUNK_COLUMNS + " FROM document_chunks c JOIN document_nodes n ON n.id = c.node_id "
                + "WHERE c.document_id = ? AND n.path <@ ?::ltree ORDER BY c.ordinal", CHUNK, documentId, path);
    }

    public List<ChunkRow> chunksOfDocument(UUID documentId) {
        return jdbc.query("SELECT " + CHUNK_COLUMNS + " FROM document_chunks c WHERE c.document_id = ? ORDER BY c.ordinal",
                CHUNK, documentId);
    }

    public List<ChunkRow> chunksOfNode(UUID nodeId) {
        return jdbc.query("SELECT " + CHUNK_COLUMNS + " FROM document_chunks c WHERE c.node_id = ? ORDER BY c.ordinal",
                CHUNK, nodeId);
    }

    public List<ChunkRow> chunksByIds(Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return jdbc.query("SELECT " + CHUNK_COLUMNS + " FROM document_chunks c WHERE c.id = ANY (?) ORDER BY c.ordinal",
                CHUNK, (Object) ids.toArray(UUID[]::new));
    }

    /** Neighbouring chunks of the same node (small-to-big expansion). */
    public List<ChunkRow> neighbours(UUID nodeId, int ordinal, int radius) {
        return jdbc.query("SELECT " + CHUNK_COLUMNS + " FROM document_chunks c WHERE c.node_id = ? "
                + "AND c.ordinal BETWEEN ? AND ? ORDER BY c.ordinal", CHUNK, nodeId, ordinal - radius, ordinal + radius);
    }

    public List<ChunkRow> chunksWithoutEmbedding(UUID documentId, int limit) {
        return jdbc.query("SELECT " + CHUNK_COLUMNS + " FROM document_chunks c WHERE c.document_id = ? "
                + "AND c.embedding IS NULL ORDER BY c.ordinal LIMIT ?", CHUNK, documentId, limit);
    }

    public void updateChunkEmbeddings(String model, Map<UUID, float[]> vectors) {
        jdbc.batchUpdate("UPDATE document_chunks SET embedding = ?::vector, embedding_model = ? WHERE id = ?",
                new ArrayList<>(vectors.entrySet()), 200, (ps, e) -> {
                    ps.setString(1, vectorLiteral(e.getValue()));
                    ps.setString(2, model);
                    ps.setObject(3, e.getKey());
                });
    }

    /** Whether any of the document's vectors came from a model other than {@code model}. */
    public boolean hasStaleEmbeddings(UUID documentId, String model) {
        Boolean stale = jdbc.queryForObject("""
                        SELECT EXISTS (SELECT 1 FROM document_chunks WHERE document_id = ? AND embedding IS NOT NULL
                                         AND embedding_model IS DISTINCT FROM ?)
                            OR EXISTS (SELECT 1 FROM document_figures WHERE document_id = ? AND embedding IS NOT NULL
                                         AND embedding_model IS DISTINCT FROM ?)""",
                Boolean.class, documentId, model, documentId, model);
        return Boolean.TRUE.equals(stale);
    }

    /** Clears vectors made by other models so the indexer embeds them again. Returns the rows cleared. */
    public int clearStaleEmbeddings(UUID documentId, String model) {
        String set = " SET embedding = NULL, embedding_model = NULL WHERE document_id = ? AND embedding IS NOT NULL "
                + "AND embedding_model IS DISTINCT FROM ?";
        return jdbc.update("UPDATE document_chunks" + set, documentId, model)
                + jdbc.update("UPDATE document_figures" + set, documentId, model);
    }

    // ------------------------------------------------------------------------------- learner edits

    /** A section the learner has edited: its current revision and when it was last saved. */
    public record SectionEdit(int revision, Instant editedAt) {
    }

    /** Revision of every node (0 = as extracted), with the time of the last edit where there was one. */
    public Map<UUID, SectionEdit> sectionEdits(UUID documentId) {
        Map<UUID, SectionEdit> edits = new HashMap<>();
        jdbc.query("SELECT id, revision, edited_at FROM document_nodes WHERE document_id = ?", (ResultSet rs) -> {
            Timestamp at = rs.getTimestamp("edited_at");
            edits.put(rs.getObject("id", UUID.class), new SectionEdit(rs.getInt("revision"),
                    at == null ? null : at.toInstant()));
        }, documentId);
        return edits;
    }

    /**
     * Records a save: renames the section and bumps its revision, but only if it is still at
     * {@code expectedRevision} (optimistic concurrency); returns false when someone saved in between.
     * {@code changedChars} accumulate until they reach {@code significantChars}, which bumps the content version
     * (what AI-written summaries and notes are compared against) and starts counting again.
     */
    public boolean recordEdit(UUID nodeId, int expectedRevision, String title, Instant now, int changedChars,
                              int significantChars) {
        return jdbc.update("""
                        UPDATE document_nodes
                        SET title = ?, revision = revision + 1, edited_at = ?,
                            content_version = content_version + CASE WHEN pending_change + ? >= ? THEN 1 ELSE 0 END,
                            pending_change = CASE WHEN pending_change + ? >= ? THEN 0 ELSE pending_change + ? END
                        WHERE id = ? AND revision = ?""",
                truncate(title, 400), Timestamp.from(now), changedChars, significantChars, changedChars,
                significantChars, changedChars, nodeId, expectedRevision) == 1;
    }

    /**
     * Gives a section its new chunks: kept ones stay as they are (text and vector), the others are removed and
     * the new ones inserted without a vector (the indexer embeds them). Chunk ordinals are unique per document
     * and follow reading order, so they are moved out of the way and renumbered in one pass.
     */
    public void replaceChunks(UUID documentId, UUID nodeId, List<SectionMarkdown.Planned> chunks, int pageFrom,
                              int pageTo, String searchConfig) {
        Object kept = chunks.stream().filter(SectionMarkdown.Planned::kept).map(SectionMarkdown.Planned::id)
                .toArray(UUID[]::new);
        jdbc.update("DELETE FROM document_chunks WHERE node_id = ? AND NOT (id = ANY (?))", nodeId, kept);
        jdbc.update("UPDATE document_chunks SET ordinal = ordinal + 1000000 WHERE document_id = ?", documentId);
        List<Integer> positions = IntStream.range(0, chunks.size()).boxed().toList();
        jdbc.batchUpdate("UPDATE document_chunks SET ordinal = ? WHERE id = ?",
                positions.stream().filter(i -> chunks.get(i).kept()).toList(), 200, (ps, i) -> {
                    ps.setInt(1, 2000000 + i);
                    ps.setObject(2, chunks.get(i).id());
                });
        jdbc.batchUpdate("""
                        INSERT INTO document_chunks (id, document_id, node_id, ordinal, content, page_from, page_to,
                                                     search_config)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?::regconfig)""",
                positions.stream().filter(i -> !chunks.get(i).kept()).toList(), 200, (ps, i) -> {
                    ps.setObject(1, chunks.get(i).id());
                    ps.setObject(2, documentId);
                    ps.setObject(3, nodeId);
                    ps.setInt(4, 2000000 + i);
                    ps.setString(5, chunks.get(i).text());
                    ps.setInt(6, pageFrom);
                    ps.setInt(7, pageTo);
                    ps.setString(8, searchConfig);
                });
        jdbc.update("""
                UPDATE document_chunks c SET ordinal = r.position
                FROM (SELECT c2.id, row_number() OVER (ORDER BY n.ordinal, c2.ordinal) - 1 AS position
                      FROM document_chunks c2 JOIN document_nodes n ON n.id = c2.node_id
                      WHERE c2.document_id = ?) r
                WHERE c.id = r.id""", documentId);
    }

    /**
     * Sections in a subtree whose summary was written from older text than they have now, or is missing (an
     * earlier refresh was interrupted).
     */
    public List<NodeRow> nodesWithStaleSummary(UUID documentId, String path) {
        return jdbc.query("SELECT " + NODE_COLUMNS + " FROM document_nodes WHERE document_id = ? AND path <@ ?::ltree "
                + "AND (summary IS NULL OR summary_version < content_version) ORDER BY ordinal", NODE, documentId, path);
    }

    /** Clears summaries (and their terms) so the indexer writes them again. */
    public void clearSummaries(Collection<UUID> nodeIds) {
        if (nodeIds.isEmpty()) {
            return;
        }
        Object ids = nodeIds.toArray(UUID[]::new);
        jdbc.update("UPDATE document_nodes SET summary = NULL, key_terms = '{}' WHERE id = ANY (?)", ids);
        jdbc.update("DELETE FROM document_terms WHERE node_id = ANY (?)", ids);
    }

    /**
     * The sum of a subtree's content versions. It only grows, so anything written from the subtree's text is
     * behind exactly when this is larger than the value recorded with it.
     */
    public int contentStamp(UUID documentId, String path) {
        Integer stamp = jdbc.queryForObject("SELECT coalesce(sum(content_version), 0) FROM document_nodes "
                + "WHERE document_id = ? AND path <@ ?::ltree", Integer.class, documentId, path);
        return stamp == null ? 0 : stamp;
    }

    /**
     * Places a section's figures after the given chunks (null: at the end of the section) and orders them as
     * listed, reusing the ordinals the section's figures already had.
     */
    public void placeFigures(List<UUID> figureIds, List<UUID> chunkIds) {
        if (figureIds.isEmpty()) {
            return;
        }
        Object ids = figureIds.toArray(UUID[]::new);
        List<Integer> ordinals = jdbc.queryForList(
                "SELECT ordinal FROM document_figures WHERE id = ANY (?) ORDER BY ordinal", Integer.class, ids);
        jdbc.update("UPDATE document_figures SET ordinal = ordinal + 1000000 WHERE id = ANY (?)", ids);
        List<Integer> positions = IntStream.range(0, figureIds.size()).boxed().toList();
        jdbc.batchUpdate("UPDATE document_figures SET chunk_id = ?, ordinal = ? WHERE id = ?", positions, 200,
                (ps, i) -> {
                    ps.setObject(1, chunkIds.get(i));
                    ps.setInt(2, ordinals.get(i));
                    ps.setObject(3, figureIds.get(i));
                });
    }

    /** Deletes figures (rows only); returns their files, for the caller to remove after commit. */
    public List<String> deleteFigures(Collection<UUID> figureIds) {
        if (figureIds.isEmpty()) {
            return List.of();
        }
        Object ids = figureIds.toArray(UUID[]::new);
        List<String> files = jdbc.queryForList("SELECT storage_key FROM document_figures WHERE id = ANY (?)",
                String.class, ids);
        jdbc.update("DELETE FROM document_figures WHERE id = ANY (?)", ids);
        return files;
    }

    /** Whether some of the document's text has no vector yet (e.g. a section the learner just edited). */
    public boolean hasUnembeddedChunks(UUID documentId) {
        Boolean missing = jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM document_chunks WHERE document_id = ? AND embedding IS NULL)",
                Boolean.class, documentId);
        return Boolean.TRUE.equals(missing);
    }

    // ------------------------------------------------------------------------------- figures

    public List<FigureRow> figuresInSubtree(UUID documentId, String path) {
        return jdbc.query("SELECT " + FIGURE_COLUMNS + " FROM document_figures f JOIN document_nodes n ON n.id = f.node_id "
                + "WHERE f.document_id = ? AND n.path <@ ?::ltree ORDER BY f.ordinal", FIGURE, documentId, path);
    }

    public List<FigureRow> figuresOfDocument(UUID documentId) {
        return jdbc.query("SELECT " + FIGURE_COLUMNS + " FROM document_figures f WHERE f.document_id = ? ORDER BY f.ordinal",
                FIGURE, documentId);
    }

    public List<FigureRow> figuresByIds(Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return jdbc.query("SELECT " + FIGURE_COLUMNS + " FROM document_figures f WHERE f.id = ANY (?) ORDER BY f.ordinal",
                FIGURE, (Object) ids.toArray(UUID[]::new));
    }

    public List<FigureRow> figuresOfChunks(Collection<UUID> chunkIds) {
        if (chunkIds.isEmpty()) {
            return List.of();
        }
        return jdbc.query("SELECT " + FIGURE_COLUMNS + " FROM document_figures f WHERE f.chunk_id = ANY (?) "
                + "ORDER BY f.ordinal", FIGURE, (Object) chunkIds.toArray(UUID[]::new));
    }

    public Optional<FigureRow> figure(UUID figureId) {
        return figuresByIds(List.of(figureId)).stream().findFirst();
    }

    public List<FigureRow> figuresWithoutDescription(UUID documentId) {
        return jdbc.query("SELECT " + FIGURE_COLUMNS + " FROM document_figures f WHERE f.document_id = ? "
                + "AND f.description IS NULL ORDER BY f.ordinal", FIGURE, documentId);
    }

    public List<FigureRow> figuresWithoutEmbedding(UUID documentId, int limit) {
        return jdbc.query("SELECT " + FIGURE_COLUMNS + " FROM document_figures f WHERE f.document_id = ? "
                + "AND f.embedding IS NULL AND f.description IS NOT NULL ORDER BY f.ordinal LIMIT ?", FIGURE, documentId,
                limit);
    }

    public void updateFigureDescription(UUID figureId, String description, String kind) {
        jdbc.update("UPDATE document_figures SET description = ?, kind = ? WHERE id = ?", description, kind, figureId);
    }

    public void updateFigureEmbeddings(String model, Map<UUID, float[]> vectors) {
        jdbc.batchUpdate("UPDATE document_figures SET embedding = ?::vector, embedding_model = ? WHERE id = ?",
                new ArrayList<>(vectors.entrySet()), 100, (ps, e) -> {
                    ps.setString(1, vectorLiteral(e.getValue()));
                    ps.setString(2, model);
                    ps.setObject(3, e.getKey());
                });
    }

    /**
     * Adds text to the section passage where a figure appears (its chunk, else its node's last chunk, else a
     * new chunk), so the content stays searchable; the passage is re-embedded.
     */
    public void appendToFigurePassage(FigureRow figure, String text) {
        int updated = jdbc.update("""
                        UPDATE document_chunks SET content = content || ?, embedding = NULL, embedding_model = NULL
                        WHERE id = coalesce(?, (SELECT id FROM document_chunks WHERE node_id = ?
                                                ORDER BY ordinal DESC LIMIT 1))""",
                "\n\n" + text, figure.chunkId(), figure.nodeId());
        if (updated == 0) {
            jdbc.update("""
                            INSERT INTO document_chunks (id, document_id, node_id, ordinal, content, page_from, page_to,
                                                         search_config)
                            SELECT ?, f.document_id, f.node_id,
                                   (SELECT coalesce(max(ordinal), -1) + 1 FROM document_chunks WHERE document_id = f.document_id),
                                   ?, f.page, f.page, f.search_config
                            FROM document_figures f WHERE f.id = ?""",
                    UUID.randomUUID(), text, figure.id());
        }
    }

    public void deleteFigure(UUID figureId) {
        jdbc.update("DELETE FROM document_figures WHERE id = ?", figureId);
    }

    public Map<UUID, Integer> figureCounts(UUID documentId) {
        Map<UUID, Integer> counts = new HashMap<>();
        jdbc.query("SELECT node_id, count(*) AS n FROM document_figures WHERE document_id = ? GROUP BY node_id",
                (ResultSet rs) -> {
                    counts.put(rs.getObject("node_id", UUID.class), rs.getInt("n"));
                }, documentId);
        return counts;
    }

    // ------------------------------------------------------------------------------- search

    /**
     * Keyword search on the inverted index (websearch syntax: "quoted phrases", -exclusions, OR), ranked by
     * cover density. {@code scopePaths} restricts results to those subtrees (null = whole document).
     */
    public List<Ranked> keywordChunks(UUID documentId, String query, List<String> scopePaths, int limit) {
        return jdbc.query("""
                        WITH q AS (SELECT websearch_to_tsquery(c.search_config, ?) AS query, c.search_config
                                   FROM document_chunks c WHERE c.document_id = ? LIMIT 1)
                        SELECT c.id, c.node_id, ts_rank_cd(c.tsv, q.query) AS score,
                               ts_headline(q.search_config, c.content, q.query,
                                           'MaxFragments=1, MaxWords=32, MinWords=14, StartSel=**, StopSel=**') AS snippet
                        FROM document_chunks c JOIN document_nodes n ON n.id = c.node_id, q
                        WHERE c.document_id = ? AND c.tsv @@ q.query
                          AND (?::ltree[] IS NULL OR n.path <@ ?::ltree[])
                        ORDER BY score DESC LIMIT ?""",
                (rs, i) -> new Ranked(rs.getObject("id", UUID.class), rs.getObject("node_id", UUID.class),
                        rs.getDouble("score"), rs.getString("snippet")),
                query, documentId, documentId, ltreeArray(scopePaths), ltreeArray(scopePaths), limit);
    }

    /** Dense (semantic) search: cosine distance on the HNSW index. */
    /** Only vectors from the query's {@code model} are compared (other models live in other vector spaces). */
    public List<Ranked> vectorChunks(UUID documentId, String model, float[] queryVector, List<String> scopePaths,
                                     int limit) {
        return jdbc.query("""
                        SELECT c.id, c.node_id, 1 - (c.embedding <=> ?::vector) AS score, left(c.content, 240) AS snippet
                        FROM document_chunks c JOIN document_nodes n ON n.id = c.node_id
                        WHERE c.document_id = ? AND c.embedding IS NOT NULL AND c.embedding_model = ?
                          AND (?::ltree[] IS NULL OR n.path <@ ?::ltree[])
                        ORDER BY c.embedding <=> ?::vector LIMIT ?""",
                (rs, i) -> new Ranked(rs.getObject("id", UUID.class), rs.getObject("node_id", UUID.class),
                        rs.getDouble("score"), rs.getString("snippet")),
                vectorLiteral(queryVector), documentId, model, ltreeArray(scopePaths), ltreeArray(scopePaths),
                vectorLiteral(queryVector), limit);
    }

    public List<Ranked> keywordFigures(UUID documentId, String query, int limit) {
        return jdbc.query("""
                        SELECT f.id, f.node_id, ts_rank_cd(f.tsv, websearch_to_tsquery(f.search_config, ?)) AS score
                        FROM document_figures f
                        WHERE f.document_id = ? AND f.tsv @@ websearch_to_tsquery(f.search_config, ?)
                        ORDER BY score DESC LIMIT ?""",
                (rs, i) -> new Ranked(rs.getObject("id", UUID.class), rs.getObject("node_id", UUID.class),
                        rs.getDouble("score"), null),
                query, documentId, query, limit);
    }

    /** @param minSimilarity cosine similarity below which a figure is not considered relevant */
    public List<Ranked> vectorFigures(UUID documentId, String model, float[] queryVector, double minSimilarity,
                                      int limit) {
        return jdbc.query("""
                        SELECT f.id, f.node_id, 1 - (f.embedding <=> ?::vector) AS score
                        FROM document_figures f
                        WHERE f.document_id = ? AND f.embedding IS NOT NULL AND f.embedding_model = ?
                          AND 1 - (f.embedding <=> ?::vector) >= ?
                        ORDER BY f.embedding <=> ?::vector LIMIT ?""",
                (rs, i) -> new Ranked(rs.getObject("id", UUID.class), rs.getObject("node_id", UUID.class),
                        rs.getDouble("score"), null),
                vectorLiteral(queryVector), documentId, model, vectorLiteral(queryVector), minSimilarity,
                vectorLiteral(queryVector), limit);
    }

    // ------------------------------------------------------------------------------- helpers

    static String vectorLiteral(float[] v) {
        StringBuilder sb = new StringBuilder(v.length * 10).append('[');
        for (int i = 0; i < v.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(v[i]);
        }
        return sb.append(']').toString();
    }

    private static String ltreeArray(List<String> paths) {
        if (paths == null || paths.isEmpty()) {
            return null;
        }
        return "{" + String.join(",", paths) + "}";
    }

    private static List<String> strings(Array array) throws SQLException {
        return array == null ? List.of() : Arrays.asList((String[]) array.getArray());
    }

    private static String truncate(String value, int max) {
        String v = value == null ? "" : value;
        return v.length() <= max ? v : v.substring(0, max);
    }
}
