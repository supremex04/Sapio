package com.sapio.sharing;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

import jakarta.validation.Valid;

import com.sapio.common.error.ApplicationException;
import com.sapio.common.error.ErrorCode;
import com.sapio.common.web.PageResponse;
import com.sapio.security.CurrentUserId;
import com.sapio.sharing.SharingResponses.Category;
import com.sapio.sharing.SharingResponses.Comment;
import com.sapio.sharing.SharingResponses.CommentRequest;
import com.sapio.sharing.SharingResponses.ImportResult;
import com.sapio.sharing.SharingResponses.MyShare;
import com.sapio.sharing.SharingResponses.ShareDetail;
import com.sapio.sharing.SharingResponses.ShareRequest;
import com.sapio.sharing.SharingResponses.ShareSummary;
import com.sapio.sharing.SharingResponses.VoteRequest;
import com.sapio.sharing.SharingResponses.Votes;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Sharing documents to Explore and importing from it. Spec: docs/specs/sharing.md. */
@RestController
class SharingController {

    private static final int MAX_PAGE_SIZE = 60;

    private final ShareService shares;
    private final ExploreService explore;
    private final ShareCategories categories;
    private final ShareFeedbackService feedback;

    SharingController(ShareService shares, ExploreService explore, ShareCategories categories,
                      ShareFeedbackService feedback) {
        this.feedback = feedback;
        this.shares = shares;
        this.explore = explore;
        this.categories = categories;
    }

    @GetMapping("/api/explore/categories")
    List<Category> categories() {
        return categories.all();
    }

    @GetMapping("/api/explore")
    PageResponse<ShareSummary> explore(@CurrentUserId UUID userId,
                                       @RequestParam(required = false) String category,
                                       @RequestParam(required = false) String q,
                                       @RequestParam(defaultValue = "new") String sort,
                                       @RequestParam(defaultValue = "0") int page,
                                       @RequestParam(defaultValue = "24") int size) {
        return explore.list(userId, category, q, sort(sort), Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE));
    }

    @GetMapping("/api/explore/{shareId}")
    ShareDetail detail(@CurrentUserId UUID userId, @PathVariable UUID shareId) {
        return explore.detail(userId, shareId);
    }

    /** 201 with the new copy, or 200 with the copy made earlier. */
    @PostMapping("/api/explore/{shareId}/import")
    ResponseEntity<ImportResult> importShare(@CurrentUserId UUID userId, @PathVariable UUID shareId) {
        ImportResult result = explore.importShare(userId, shareId);
        return ResponseEntity.status(result.alreadyImported() ? HttpStatus.OK : HttpStatus.CREATED).body(result);
    }

    @PutMapping("/api/explore/{shareId}/vote")
    Votes vote(@CurrentUserId UUID userId, @PathVariable UUID shareId, @RequestBody VoteRequest request) {
        return feedback.vote(userId, shareId, request.value());
    }

    @GetMapping("/api/explore/{shareId}/comments")
    PageResponse<Comment> comments(@CurrentUserId UUID userId, @PathVariable UUID shareId,
                                   @RequestParam(defaultValue = "0") int page,
                                   @RequestParam(defaultValue = "20") int size) {
        return feedback.comments(userId, shareId, Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE));
    }

    @PostMapping("/api/explore/{shareId}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    Comment comment(@CurrentUserId UUID userId, @PathVariable UUID shareId, @RequestBody CommentRequest request) {
        return feedback.comment(userId, shareId, request.body());
    }

    @DeleteMapping("/api/explore/{shareId}/comments/{commentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteComment(@CurrentUserId UUID userId, @PathVariable UUID shareId, @PathVariable UUID commentId) {
        feedback.deleteComment(userId, shareId, commentId);
    }

    @GetMapping("/api/shares")
    List<MyShare> mine(@CurrentUserId UUID userId) {
        return shares.mine(userId);
    }

    @PutMapping("/api/documents/{documentId}/share")
    MyShare share(@CurrentUserId UUID userId, @PathVariable UUID documentId,
                  @Valid @RequestBody ShareRequest request) {
        return shares.share(userId, documentId, request);
    }

    @DeleteMapping("/api/documents/{documentId}/share")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void unshare(@CurrentUserId UUID userId, @PathVariable UUID documentId) {
        shares.unshare(userId, documentId);
    }

    private static ExploreService.Sort sort(String value) {
        try {
            return ExploreService.Sort.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ApplicationException(ErrorCode.VALIDATION_FAILED, "sort must be 'new', 'top' or 'popular'");
        }
    }
}
