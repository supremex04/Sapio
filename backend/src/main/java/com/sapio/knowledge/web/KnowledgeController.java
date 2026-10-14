package com.sapio.knowledge.web;

import java.util.UUID;

import jakarta.validation.Valid;

import com.sapio.common.error.ApplicationException;
import com.sapio.common.error.ErrorCode;
import com.sapio.security.CurrentUserId;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/documents/{documentId}")
class KnowledgeController {

    private final KnowledgeService knowledge;

    private final SectionEditService edits;

    KnowledgeController(KnowledgeService knowledge, SectionEditService edits) {
        this.knowledge = knowledge;
        this.edits = edits;
    }

    @GetMapping("/tree")
    KnowledgeResponses.TreeResponse tree(@CurrentUserId UUID userId, @PathVariable UUID documentId) {
        return knowledge.tree(userId, documentId);
    }

    @GetMapping("/content")
    KnowledgeResponses.ContentResponse content(@CurrentUserId UUID userId, @PathVariable UUID documentId) {
        return knowledge.content(userId, documentId);
    }

    /** Saves the learner's edit of one section; returns the reading view as it is now. */
    @PutMapping("/sections/{nodeId}")
    KnowledgeResponses.ContentResponse editSection(@CurrentUserId UUID userId, @PathVariable UUID documentId,
                                                   @PathVariable UUID nodeId,
                                                   @Valid @RequestBody SectionEditRequest body) {
        return edits.edit(userId, documentId, nodeId, body);
    }

    @GetMapping("/nodes/{nodeId}")
    KnowledgeResponses.NodeResponse node(@CurrentUserId UUID userId, @PathVariable UUID documentId,
                                         @PathVariable UUID nodeId) {
        return knowledge.node(userId, documentId, nodeId);
    }

    @GetMapping("/search")
    KnowledgeResponses.SearchResponse search(@CurrentUserId UUID userId, @PathVariable UUID documentId,
                                             @RequestParam String q, @RequestParam(required = false) UUID nodeId,
                                             @RequestParam(defaultValue = "12") int limit) {
        if (q.isBlank() || q.length() > 300) {
            throw new ApplicationException(ErrorCode.VALIDATION_FAILED, "Search text must be 1-300 characters");
        }
        return knowledge.search(userId, documentId, q, nodeId, limit);
    }

    @GetMapping("/terms")
    KnowledgeResponses.TermsResponse terms(@CurrentUserId UUID userId, @PathVariable UUID documentId) {
        return knowledge.terms(userId, documentId);
    }
}
