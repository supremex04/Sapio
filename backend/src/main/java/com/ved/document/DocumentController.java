package com.ved.document;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import com.ved.common.web.PageResponse;
import com.ved.security.CurrentUserId;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/documents")
class DocumentController {

    private static final MediaType MARKDOWN = new MediaType("text", "markdown", StandardCharsets.UTF_8);
    private static final int MAX_PAGE_SIZE = 100;

    private final DocumentService documentService;

    DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    DocumentResponse upload(@CurrentUserId UUID userId, @RequestParam("file") MultipartFile file) {
        return DocumentResponse.from(documentService.upload(userId, file));
    }

    @GetMapping
    PageResponse<DocumentResponse> list(@CurrentUserId UUID userId,
                                        @RequestParam(defaultValue = "0") int page,
                                        @RequestParam(defaultValue = "20") int size) {
        PageRequest pageRequest = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE));
        return PageResponse.from(documentService.list(userId, pageRequest), DocumentResponse::from);
    }

    @GetMapping("/{id}")
    DocumentResponse get(@CurrentUserId UUID userId, @PathVariable UUID id) {
        return DocumentResponse.from(documentService.get(userId, id));
    }

    @GetMapping("/{id}/result")
    DocumentResultResponse result(@CurrentUserId UUID userId, @PathVariable UUID id) {
        return new DocumentResultResponse(id, "markdown", documentService.getResult(userId, id));
    }

    @GetMapping("/{id}/result/download")
    ResponseEntity<byte[]> download(@CurrentUserId UUID userId, @PathVariable UUID id) {
        Document document = documentService.get(userId, id);
        String markdown = documentService.getResult(userId, id);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(baseName(document.getOriginalFilename()) + ".md", StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(MARKDOWN)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(markdown.getBytes(StandardCharsets.UTF_8));
    }

    /** Streams the original upload inline (the browser's own PDF/image viewer renders it). */
    @GetMapping("/{id}/original")
    ResponseEntity<Resource> original(@CurrentUserId UUID userId, @PathVariable UUID id) {
        DocumentService.Original original = documentService.getOriginal(userId, id);
        ContentDisposition disposition = ContentDisposition.inline()
                .filename(original.document().getOriginalFilename(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(original.document().getContentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=3600")
                .body(original.content());
    }

    @PostMapping("/{id}/retry")
    @ResponseStatus(HttpStatus.ACCEPTED)
    DocumentResponse retry(@CurrentUserId UUID userId, @PathVariable UUID id) {
        return DocumentResponse.from(documentService.retry(userId, id));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@CurrentUserId UUID userId, @PathVariable UUID id) {
        documentService.delete(userId, id);
    }

    private static String baseName(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot > 0 ? filename.substring(0, dot) : filename;
    }
}
