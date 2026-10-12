package com.ved.sharing;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import com.ved.common.error.ApplicationException;
import com.ved.common.error.ErrorCode;
import com.ved.common.web.PageResponse;
import com.ved.document.Document;
import com.ved.document.DocumentRepository;
import com.ved.document.copy.DocumentCloner;
import com.ved.sharing.SharingResponses.Category;
import com.ved.sharing.SharingResponses.ImportResult;
import com.ved.sharing.SharingResponses.ShareDetail;
import com.ved.sharing.SharingResponses.ShareSummary;
import com.ved.user.User;
import com.ved.user.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** Browsing what others shared, and importing it as one's own independent copy. */
@Service
class ExploreService {

    enum Sort {
        NEW("s.created_at DESC, s.id"),
        TOP("s.quality DESC, s.created_at DESC, s.id"),
        POPULAR("s.import_count DESC, s.created_at DESC, s.id");

        final String orderBy;

        Sort(String orderBy) {
            this.orderBy = orderBy;
        }
    }

    private static final String FROM = """
             FROM document_shares s
             JOIN share_categories c ON c.slug = s.category
             JOIN users u ON u.id = s.owner_id""";
    private static final String COLUMNS = """
            SELECT s.id, s.title, s.summary, s.kind, s.language, s.page_count, s.topic_titles, s.description,
                   s.import_count, s.upvotes, s.downvotes, s.comment_count, s.created_at, s.updated_at, s.owner_id,
                   c.slug, c.name, c.emoji,
                   coalesce((SELECT v.value FROM share_votes v WHERE v.share_id = s.id AND v.user_id = :viewer), 0)
                       AS my_vote,
                   u.full_name AS shared_by,
                   (SELECT d.id FROM documents d WHERE d.owner_id = :viewer AND d.origin_share_id = s.id) AS copy_id""";

    private final NamedParameterJdbcTemplate jdbc;
    private final DocumentShareRepository shares;
    private final DocumentRepository documents;
    private final UserRepository users;
    private final DocumentCloner cloner;
    private final TransactionTemplate tx;

    ExploreService(NamedParameterJdbcTemplate jdbc, DocumentShareRepository shares, DocumentRepository documents,
                   UserRepository users, DocumentCloner cloner, TransactionTemplate tx) {
        this.jdbc = jdbc;
        this.shares = shares;
        this.documents = documents;
        this.users = users;
        this.cloner = cloner;
        this.tx = tx;
    }

    PageResponse<ShareSummary> list(UUID viewer, String category, String query, Sort sort, int page, int size) {
        MapSqlParameterSource params = new MapSqlParameterSource("viewer", viewer)
                .addValue("limit", size).addValue("offset", (long) page * size);
        List<String> where = new ArrayList<>();
        if (category != null && !category.isBlank()) {
            where.add("s.category = :category");
            params.addValue("category", category.strip());
        }
        if (query != null && !query.isBlank()) {
            where.add("(s.title ILIKE :q ESCAPE '\\' OR s.summary ILIKE :q ESCAPE '\\')");
            params.addValue("q", "%" + escapeLike(query.strip().toLowerCase(Locale.ROOT)) + "%");
        }
        String filter = where.isEmpty() ? "" : " WHERE " + String.join(" AND ", where);
        long total = jdbc.queryForObject("SELECT count(*) " + FROM + filter, params, Long.class);
        List<ShareSummary> items = jdbc.query(COLUMNS + " " + FROM + filter + " ORDER BY " + sort.orderBy
                + " LIMIT :limit OFFSET :offset", params, (rs, i) -> summary(rs, viewer));
        return new PageResponse<>(items, page, size, total, (int) ((total + size - 1) / size));
    }

    ShareDetail detail(UUID viewer, UUID shareId) {
        List<ShareDetail> found = jdbc.query(COLUMNS + " " + FROM + " WHERE s.id = :id",
                new MapSqlParameterSource("viewer", viewer).addValue("id", shareId),
                (rs, i) -> new ShareDetail(summary(rs, viewer), rs.getString("description"), topics(rs),
                        rs.getTimestamp("updated_at").toInstant()));
        return found.stream().findFirst().orElseThrow(() -> ApplicationException.notFound("Shared document"));
    }

    /**
     * Imports a share as the viewer's own copy (document, knowledge store and study content). Idempotent: importing
     * again returns the existing copy.
     */
    ImportResult importShare(UUID viewer, UUID shareId) {
        DocumentShare share = shares.findById(shareId)
                .orElseThrow(() -> ApplicationException.notFound("Shared document"));
        if (share.getOwnerId().equals(viewer)) {
            throw new ApplicationException(ErrorCode.INVALID_STATE, "This is your own document; it's already in "
                    + "your library");
        }
        Optional<UUID> existing = existingCopy(viewer, shareId);
        if (existing.isPresent()) {
            return new ImportResult(existing.get(), true);
        }
        String sharedBy = users.findById(share.getOwnerId()).map(User::getFullName).orElse("another learner");
        UUID copy;
        try {
            copy = cloner.copy(share.getDocumentId(), viewer, new DocumentCloner.Origin(shareId, sharedBy));
        } catch (DataIntegrityViolationException raced) {
            // A second click imported it concurrently (unique per learner and share): hand back that copy.
            return existingCopy(viewer, shareId).map(id -> new ImportResult(id, true)).orElseThrow(() -> raced);
        }
        // Separate short transaction: concurrent imports never conflict on the counter during the copy.
        tx.executeWithoutResult(status -> shares.countImport(shareId));
        return new ImportResult(copy, false);
    }

    private Optional<UUID> existingCopy(UUID viewer, UUID shareId) {
        return documents.findByOwnerIdAndOriginShareId(viewer, shareId).map(Document::getId);
    }

    private static ShareSummary summary(ResultSet rs, UUID viewer) throws SQLException {
        return new ShareSummary(rs.getObject("id", UUID.class), rs.getString("title"), rs.getString("summary"),
                new Category(rs.getString("slug"), rs.getString("name"), rs.getString("emoji")),
                rs.getString("kind"), rs.getString("language"), rs.getInt("page_count"), topics(rs).size(),
                rs.getString("shared_by"), rs.getTimestamp("created_at").toInstant(), rs.getInt("import_count"),
                new SharingResponses.Votes(rs.getInt("upvotes"), rs.getInt("downvotes"), rs.getInt("my_vote")),
                rs.getInt("comment_count"), viewer.equals(rs.getObject("owner_id", UUID.class)), rs.getObject("copy_id", UUID.class));
    }

    private static List<String> topics(ResultSet rs) throws SQLException {
        Array array = rs.getArray("topic_titles");
        return array == null ? List.of() : Arrays.asList((String[]) array.getArray());
    }

    private static String escapeLike(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
