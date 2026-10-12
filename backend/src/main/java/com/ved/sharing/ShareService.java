package com.ved.sharing;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

import com.ved.common.error.ApplicationException;
import com.ved.common.error.ErrorCode;
import com.ved.document.Document;
import com.ved.document.DocumentService;
import com.ved.document.DocumentStatus;
import com.ved.sharing.SharingResponses.MyShare;
import com.ved.sharing.SharingResponses.ShareRequest;
import com.ved.study.material.StudyMaterial;
import com.ved.study.material.StudyMaterialRepository;
import com.ved.study.material.StudyTopic;
import com.ved.study.material.StudyTopicRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Publishing one's own documents to Explore, and withdrawing them. */
@Service
class ShareService {

    private final DocumentShareRepository shares;
    private final ShareCategories categories;
    private final DocumentService documents;
    private final StudyMaterialRepository materials;
    private final StudyTopicRepository topics;
    private final Clock clock;

    ShareService(DocumentShareRepository shares, ShareCategories categories, DocumentService documents,
                 StudyMaterialRepository materials, StudyTopicRepository topics, Clock clock) {
        this.shares = shares;
        this.categories = categories;
        this.documents = documents;
        this.materials = materials;
        this.topics = topics;
        this.clock = clock;
    }

    /** Shares the document, or updates its category and description (and refreshes the snapshot). */
    @Transactional
    public MyShare share(UUID userId, UUID documentId, ShareRequest request) {
        Document document = documents.get(userId, documentId);
        if (document.isImported()) {
            throw new ApplicationException(ErrorCode.INVALID_STATE,
                    "Imported documents can't be shared again. Share documents you uploaded yourself");
        }
        if (document.getStatus() != DocumentStatus.READY) {
            throw new ApplicationException(ErrorCode.INVALID_STATE, "Only ready documents can be shared");
        }
        String category = request.category().strip();
        if (categories.find(category).isEmpty()) {
            throw new ApplicationException(ErrorCode.VALIDATION_FAILED, "Unknown category: " + category);
        }
        StudyMaterial material = materials.findByDocumentId(documentId)
                .orElseThrow(() -> new ApplicationException(ErrorCode.MATERIAL_NOT_READY,
                        "This document has no learning path yet"));
        List<String> topicTitles = topics.findByMaterialIdOrderByPosition(material.getId()).stream()
                .map(StudyTopic::getTitle).toList();
        String title = material.getTitle().isBlank() ? document.getOriginalFilename() : material.getTitle();
        DocumentShare share = shares.findByDocumentId(documentId)
                .orElseGet(() -> new DocumentShare(documentId, userId, clock.instant()));
        share.publish(category, request.description() == null ? "" : request.description().strip(),
                new DocumentShare.Snapshot(title, material.getSummary(), material.getKind().name(),
                        material.getLanguage(), document.getPageCount() == null ? 0 : document.getPageCount(),
                        topicTitles),
                clock.instant());
        return toMyShare(shares.save(share));
    }

    /** Withdraws the share. Copies others imported stay theirs. */
    @Transactional
    public void unshare(UUID userId, UUID documentId) {
        documents.get(userId, documentId); // ownership (404 otherwise)
        shares.findByDocumentId(documentId).ifPresent(shares::delete);
    }

    @Transactional(readOnly = true)
    public List<MyShare> mine(UUID userId) {
        return shares.findByOwnerIdOrderByCreatedAtDesc(userId).stream().map(this::toMyShare).toList();
    }

    private MyShare toMyShare(DocumentShare s) {
        return new MyShare(s.getId(), s.getDocumentId(), categories.find(s.getCategory()).orElseThrow(),
                s.getDescription(), s.getImportCount(), s.getCreatedAt(), s.getUpdatedAt());
    }
}
