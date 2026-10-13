package com.ved.document;

import java.time.Duration;
import java.util.UUID;

import com.ved.common.error.ApplicationException;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Streams audio / video documents to {@code <audio>} / {@code <video>} elements. Authorised by the HMAC signature in
 * the URL (from {@code GET /api/documents/{id}/playback}). Spring serves HTTP Range requests for resource bodies
 * (206 Partial Content), so the player can seek without downloading the whole recording.
 */
@RestController
class MediaController {

    private final DocumentService documents;
    private final MediaUrlSigner signer;

    MediaController(DocumentService documents, MediaUrlSigner signer) {
        this.documents = documents;
        this.signer = signer;
    }

    @GetMapping("/api/media/{documentId}")
    ResponseEntity<Resource> stream(@PathVariable UUID documentId, @RequestParam long exp, @RequestParam String sig) {
        if (!signer.verify(documentId, exp, sig)) {
            throw ApplicationException.notFound("Recording");
        }
        DocumentService.Original media = documents.media(documentId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(media.document().getContentType()))
                .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePrivate())
                .header("X-Content-Type-Options", "nosniff")
                .body(media.content());
    }
}
