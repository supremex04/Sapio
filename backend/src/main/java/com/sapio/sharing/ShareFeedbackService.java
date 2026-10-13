package com.sapio.sharing;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.sapio.common.error.ApplicationException;
import com.sapio.common.error.ErrorCode;
import com.sapio.common.web.PageResponse;
import com.sapio.sharing.SharingResponses.Comment;
import com.sapio.sharing.SharingResponses.Votes;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Quality signals on shared documents: one up or down vote per learner, and comments. Counters on the share are
 * updated in the same transaction after locking the share row, so concurrent votes never lose an update.
 */
@Service
class ShareFeedbackService {

    private final JdbcTemplate jdbc;
    private final SharingProperties.Comments config;
    private final Clock clock;

    ShareFeedbackService(JdbcTemplate jdbc, SharingProperties properties, Clock clock) {
        this.jdbc = jdbc;
        this.config = properties.comments();
        this.clock = clock;
    }

    /** @param value 1 (up), -1 (down) or 0 (take the vote back) */
    @Transactional
    public Votes vote(UUID userId, UUID shareId, int value) {
        if (value < -1 || value > 1) {
            throw new ApplicationException(ErrorCode.VALIDATION_FAILED, "value must be 1, -1 or 0");
        }
        UUID owner = lockShare(shareId);
        if (owner.equals(userId)) {
            throw new ApplicationException(ErrorCode.INVALID_STATE, "You can't vote on a document you shared");
        }
        List<Integer> previous = jdbc.queryForList(
                "SELECT value FROM share_votes WHERE share_id = ? AND user_id = ?", Integer.class, shareId, userId);
        int old = previous.isEmpty() ? 0 : previous.getFirst();
        Timestamp now = Timestamp.from(clock.instant());
        if (value == 0) {
            jdbc.update("DELETE FROM share_votes WHERE share_id = ? AND user_id = ?", shareId, userId);
        } else {
            jdbc.update("""
                    INSERT INTO share_votes (share_id, user_id, value, created_at, updated_at) VALUES (?, ?, ?, ?, ?)
                    ON CONFLICT (share_id, user_id) DO UPDATE SET value = EXCLUDED.value, updated_at = EXCLUDED.updated_at""",
                    shareId, userId, value, now, now);
        }
        jdbc.update("UPDATE document_shares SET upvotes = upvotes + ?, downvotes = downvotes + ? WHERE id = ?",
                is(value, 1) - is(old, 1), is(value, -1) - is(old, -1), shareId);
        return jdbc.queryForObject("SELECT upvotes, downvotes FROM document_shares WHERE id = ?",
                (rs, i) -> new Votes(rs.getInt(1), rs.getInt(2), value), shareId);
    }

    /** Newest first. */
    @Transactional(readOnly = true)
    public PageResponse<Comment> comments(UUID viewer, UUID shareId, int page, int size) {
        UUID owner = shareOwner(shareId);
        long total = jdbc.queryForObject("SELECT count(*) FROM share_comments WHERE share_id = ?", Long.class,
                shareId);
        List<Comment> items = jdbc.query("""
                        SELECT c.id, c.body, c.created_at, c.author_id, u.full_name
                        FROM share_comments c JOIN users u ON u.id = c.author_id
                        WHERE c.share_id = ? ORDER BY c.created_at DESC, c.id LIMIT ? OFFSET ?""",
                (rs, i) -> comment(rs.getObject("id", UUID.class), rs.getString("body"),
                        rs.getTimestamp("created_at").toInstant(), rs.getObject("author_id", UUID.class),
                        rs.getString("full_name"), viewer, owner),
                shareId, size, (long) page * size);
        return new PageResponse<>(items, page, size, total, (int) ((total + size - 1) / size));
    }

    @Transactional
    public Comment comment(UUID userId, UUID shareId, String text) {
        String body = text == null ? "" : text.strip();
        if (body.isEmpty() || body.length() > config.maxLength()) {
            throw new ApplicationException(ErrorCode.VALIDATION_FAILED,
                    "A comment needs 1 to " + config.maxLength() + " characters");
        }
        Instant now = clock.instant();
        Integer recent = jdbc.queryForObject("SELECT count(*) FROM share_comments WHERE author_id = ? AND created_at > ?",
                Integer.class, userId, Timestamp.from(now.minus(Duration.ofMinutes(1))));
        if (recent != null && recent >= config.perMinute()) {
            throw new ApplicationException(ErrorCode.RATE_LIMITED, "You're commenting very fast. Try again in a minute");
        }
        UUID owner = lockShare(shareId);
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO share_comments (id, share_id, author_id, body, created_at) VALUES (?, ?, ?, ?, ?)",
                id, shareId, userId, body, Timestamp.from(now));
        jdbc.update("UPDATE document_shares SET comment_count = comment_count + 1 WHERE id = ?", shareId);
        String name = jdbc.queryForObject("SELECT full_name FROM users WHERE id = ?", String.class, userId);
        return comment(id, body, now, userId, name, userId, owner);
    }

    /** The author, or the sharer (moderating their own share), may delete a comment. */
    @Transactional
    public void deleteComment(UUID userId, UUID shareId, UUID commentId) {
        UUID owner = lockShare(shareId);
        List<UUID> author = jdbc.queryForList("SELECT author_id FROM share_comments WHERE id = ? AND share_id = ?",
                UUID.class, commentId, shareId);
        if (author.isEmpty()) {
            throw ApplicationException.notFound("Comment");
        }
        if (!author.getFirst().equals(userId) && !owner.equals(userId)) {
            throw new ApplicationException(ErrorCode.ACCESS_DENIED, "Only the author or the sharer can delete this");
        }
        jdbc.update("DELETE FROM share_comments WHERE id = ?", commentId);
        jdbc.update("UPDATE document_shares SET comment_count = comment_count - 1 WHERE id = ?", shareId);
    }

    private UUID lockShare(UUID shareId) {
        return jdbc.queryForList("SELECT owner_id FROM document_shares WHERE id = ? FOR UPDATE", UUID.class, shareId)
                .stream().findFirst().orElseThrow(() -> ApplicationException.notFound("Shared document"));
    }

    private UUID shareOwner(UUID shareId) {
        return jdbc.queryForList("SELECT owner_id FROM document_shares WHERE id = ?", UUID.class, shareId)
                .stream().findFirst().orElseThrow(() -> ApplicationException.notFound("Shared document"));
    }

    private static Comment comment(UUID id, String body, Instant at, UUID author, String name, UUID viewer,
                                   UUID owner) {
        boolean mine = author.equals(viewer);
        return new Comment(id, body, name, at, author.equals(owner), mine, mine || viewer.equals(owner));
    }

    private static int is(int value, int expected) {
        return value == expected ? 1 : 0;
    }
}
