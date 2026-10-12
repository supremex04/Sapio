package com.ved.document.copy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.ved.document.storage.DocumentStorage;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * A document copy in progress, handed to each {@link DocumentCopyStep}. Every copied row gets a new id; the
 * old → new pairs live in the transaction-scoped table {@value #ID_MAP}, so later steps can remap references
 * (foreign keys, id arrays, ids inside text) to the copy.
 *
 * <p>{@link #copyRows} copies <em>all</em> columns of a table except the ones it is told to change, reading the
 * column list from the database: a column added by a later migration is copied without touching this code.
 */
public final class DocumentCopy {

    /** Transaction-scoped map of copied ids: {@code (old_id uuid primary key, new_id uuid)}. */
    public static final String ID_MAP = "copy_ids";
    private static final Pattern UUID_PATTERN =
            Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");

    private final UUID sourceId;
    private final UUID targetId;
    private final UUID targetOwnerId;
    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate named;
    private final DocumentStorage storage;
    private final Map<String, List<String>> columns = new HashMap<>();
    private Map<UUID, UUID> ids;

    DocumentCopy(UUID sourceId, UUID targetId, UUID targetOwnerId, JdbcTemplate jdbc, DocumentStorage storage) {
        this.sourceId = sourceId;
        this.targetId = targetId;
        this.targetOwnerId = targetOwnerId;
        this.jdbc = jdbc;
        this.named = new NamedParameterJdbcTemplate(jdbc);
        this.storage = storage;
    }

    public UUID sourceId() {
        return sourceId;
    }

    public UUID targetId() {
        return targetId;
    }

    public UUID targetOwnerId() {
        return targetOwnerId;
    }

    public JdbcTemplate jdbc() {
        return jdbc;
    }

    /** Assigns new ids to the rows {@code selectIds} returns (one uuid column; may use {@code :source}). */
    public int mapIds(String selectIds) {
        ids = null;
        return named.update("INSERT INTO " + ID_MAP + " (old_id, new_id) SELECT s.id, gen_random_uuid() FROM ("
                + selectIds + ") s(id)", new MapSqlParameterSource("source", sourceId));
    }

    /** SQL expression: the copy's id for {@code oldId} (NULL stays NULL; an unmapped id becomes NULL). */
    public static String mapped(String oldId) {
        return "(SELECT m.new_id FROM " + ID_MAP + " m WHERE m.old_id = " + oldId + ")";
    }

    /** SQL expression: a uuid[] column with every element mapped to the copy, order kept, unmapped ids dropped. */
    public static String mappedArray(String column) {
        return "coalesce(ARRAY(SELECT m.new_id FROM unnest(" + column + ") WITH ORDINALITY u(id, ord) JOIN " + ID_MAP
                + " m ON m.old_id = u.id ORDER BY u.ord), '{}')";
    }

    /**
     * {@code INSERT INTO table SELECT ... FROM table src WHERE where}: every column copied as is, except those in
     * {@code overrides} (column → SQL expression over {@code src}). SQL uses named parameters; {@code :source},
     * {@code :target} and {@code :owner} (the copy's owner) are always bound.
     *
     * @return rows copied
     */
    public int copyRows(String table, Map<String, String> overrides, String where) {
        return copyRows(table, overrides, where, Map.of());
    }

    public int copyRows(String table, Map<String, String> overrides, String where, Map<String, ?> params) {
        List<String> cols = columnsOf(table);
        for (String column : overrides.keySet()) {
            if (!cols.contains(column)) {
                throw new IllegalArgumentException(table + " has no column " + column);
            }
        }
        List<String> exprs = new ArrayList<>();
        for (String column : cols) {
            exprs.add(overrides.getOrDefault(column, "src." + column));
        }
        MapSqlParameterSource bound = new MapSqlParameterSource(params)
                .addValue("source", sourceId).addValue("target", targetId).addValue("owner", targetOwnerId);
        return named.update("INSERT INTO " + table + " (" + String.join(", ", cols) + ") SELECT "
                + String.join(", ", exprs) + " FROM " + table + " src WHERE " + where, bound);
    }

    /** Convenience for {@link #copyRows} overrides, keeping their order readable. */
    public static Map<String, String> set(String... columnExpressionPairs) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < columnExpressionPairs.length; i += 2) {
            map.put(columnExpressionPairs[i], columnExpressionPairs[i + 1]);
        }
        return map;
    }

    /** Copies a stored file; the copy is deleted again if the transaction rolls back. */
    public String copyFile(String key) {
        String copy = storage.copy(key);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    storage.delete(copy);
                }
            }
        });
        return copy;
    }

    /** Replaces every id of a copied row inside {@code text} (e.g. {@code figure:<id>} in markdown). */
    public String rewriteIds(String text) {
        if (text == null) {
            return null;
        }
        Map<UUID, UUID> map = ids();
        Matcher m = UUID_PATTERN.matcher(text);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            UUID copy = map.get(UUID.fromString(m.group()));
            m.appendReplacement(out, copy == null ? m.group() : copy.toString());
        }
        return m.appendTail(out).toString();
    }

    private Map<UUID, UUID> ids() {
        if (ids == null) {
            Map<UUID, UUID> map = new HashMap<>();
            jdbc.query("SELECT old_id, new_id FROM " + ID_MAP,
                    rs -> { map.put(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class)); });
            ids = map;
        }
        return ids;
    }

    private List<String> columnsOf(String table) {
        return columns.computeIfAbsent(table, t -> {
            List<String> cols = jdbc.queryForList("""
                    SELECT column_name FROM information_schema.columns
                    WHERE table_schema = current_schema() AND table_name = ? AND is_generated = 'NEVER'
                    ORDER BY ordinal_position""", String.class, t);
            if (cols.isEmpty()) {
                throw new IllegalArgumentException("Unknown table " + t);
            }
            return cols;
        });
    }
}
