package com.ved.document;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

import com.ved.common.error.ApplicationException;
import com.ved.common.error.ErrorCode;
import com.ved.document.storage.DocumentStorage;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

/**
 * Document use-cases for the owning user. Every read/write is scoped by owner id, so users can never
 * see each other's documents.
 */
@Service
public class DocumentService {

    private static final int SIGNATURE_BYTES = 16;

    private final DocumentRepository documents;
    private final DocumentStorage storage;
    private final ApplicationEventPublisher events;
    private final DocumentProperties properties;

    DocumentService(DocumentRepository documents, DocumentStorage storage, ApplicationEventPublisher events,
                    DocumentProperties properties) {
        this.documents = documents;
        this.storage = storage;
        this.events = events;
        this.properties = properties;
    }

    @Transactional
    public Document upload(UUID ownerId, MultipartFile file) {
        SupportedFileType type = validate(file);
        String filename = sanitizeFilename(file.getOriginalFilename());
        String key;
        try (InputStream in = file.getInputStream()) {
            key = storage.store(in, type.name().toLowerCase());
        } catch (IOException e) {
            throw new ApplicationException(ErrorCode.VALIDATION_FAILED, "Could not read uploaded file");
        }
        deleteFileOnRollback(key);
        Document document = documents.save(new Document(ownerId, filename, type.mediaType(), file.getSize(), key));
        events.publishEvent(new DocumentUploadedEvent(document.getId()));
        return document;
    }

    @Transactional(readOnly = true)
    public Page<Document> list(UUID ownerId, Pageable pageable) {
        return documents.findByOwnerIdOrderByCreatedAtDesc(ownerId, pageable);
    }

    @Transactional(readOnly = true)
    public Document get(UUID ownerId, UUID documentId) {
        return documents.findByIdAndOwnerId(documentId, ownerId)
                .orElseThrow(() -> ApplicationException.notFound("Document"));
    }

    @Transactional(readOnly = true)
    public String getResult(UUID ownerId, UUID documentId) {
        Document document = get(ownerId, documentId);
        if (document.getStatus() != DocumentStatus.COMPLETED) {
            throw new ApplicationException(ErrorCode.RESULT_NOT_READY,
                    "Result not available, document is " + document.getStatus());
        }
        return document.getResultMarkdown();
    }

    @Transactional
    public void delete(UUID ownerId, UUID documentId) {
        Document document = get(ownerId, documentId);
        documents.delete(document);
        String key = document.getStorageKey();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                storage.delete(key);
            }
        });
    }

    private SupportedFileType validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApplicationException(ErrorCode.VALIDATION_FAILED, "File is empty");
        }
        if (file.getSize() > properties.maxSizeBytes()) {
            throw new ApplicationException(ErrorCode.FILE_TOO_LARGE,
                    "File exceeds the maximum size of " + properties.maxSizeBytes() / (1024 * 1024) + " MB");
        }
        SupportedFileType type = SupportedFileType.fromFilename(file.getOriginalFilename())
                .orElseThrow(() -> unsupported());
        try (InputStream in = file.getInputStream()) {
            if (!type.matchesSignature(in.readNBytes(SIGNATURE_BYTES))) {
                throw unsupported();
            }
        } catch (IOException e) {
            throw new ApplicationException(ErrorCode.VALIDATION_FAILED, "Could not read uploaded file");
        }
        return type;
    }

    private void deleteFileOnRollback(String key) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    storage.delete(key);
                }
            }
        });
    }

    private static ApplicationException unsupported() {
        return new ApplicationException(ErrorCode.UNSUPPORTED_FILE_TYPE,
                "Unsupported file type. Allowed: PDF, PNG, JPEG, WEBP, TIFF, BMP");
    }

    /** Keeps only the last path segment and strips control characters; the name is display-only. */
    static String sanitizeFilename(String original) {
        String name = original == null ? "document" : original.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}\"]", "").strip();
        if (name.isEmpty()) {
            name = "document";
        }
        return name.length() > 255 ? name.substring(name.length() - 255) : name;
    }
}
