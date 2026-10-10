package com.ved.knowledge.web;

import java.util.UUID;

import com.ved.common.error.ApplicationException;
import com.ved.common.error.ErrorCode;
import com.ved.document.Document;
import com.ved.document.DocumentService;
import com.ved.knowledge.store.KnowledgeStore;
import com.ved.knowledge.store.KnowledgeStore.NodeRow;
import org.springframework.stereotype.Component;

/** "May this user read this document's knowledge store?" Today: ownership. Unauthorised = 404. */
@Component
public class KnowledgeAccess {

    private final DocumentService documents;
    private final KnowledgeStore store;

    KnowledgeAccess(DocumentService documents, KnowledgeStore store) {
        this.documents = documents;
        this.store = store;
    }

    public Document requireDocument(UUID userId, UUID documentId) {
        Document document = documents.get(userId, documentId);
        if (!store.hasTree(documentId)) {
            throw new ApplicationException(ErrorCode.RESULT_NOT_READY, "This document is still being processed");
        }
        return document;
    }

    public NodeRow requireNode(UUID userId, UUID documentId, UUID nodeId) {
        requireDocument(userId, documentId);
        return store.node(nodeId).filter(n -> n.documentId().equals(documentId))
                .orElseThrow(() -> ApplicationException.notFound("Section"));
    }
}
