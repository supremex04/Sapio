package com.ved.knowledge.figure;

import java.time.Duration;
import java.util.UUID;

import com.ved.common.error.ApplicationException;
import com.ved.document.storage.DocumentStorage;
import com.ved.knowledge.store.KnowledgeStore;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Serves figure images. Public route, authorised by the HMAC signature in the URL (issued only to users who
 * may see the document), so {@code <img src>} works without an Authorization header.
 */
@RestController
class FigureController {

    private final KnowledgeStore store;
    private final DocumentStorage storage;
    private final FigureUrlSigner signer;

    FigureController(KnowledgeStore store, DocumentStorage storage, FigureUrlSigner signer) {
        this.store = store;
        this.storage = storage;
        this.signer = signer;
    }

    @GetMapping("/api/figures/{figureId}/image")
    ResponseEntity<Resource> image(@PathVariable UUID figureId, @RequestParam long exp, @RequestParam String sig) {
        if (!signer.verify(figureId, exp, sig)) {
            throw ApplicationException.notFound("Figure");
        }
        KnowledgeStore.FigureRow figure = store.figure(figureId).orElseThrow(() -> ApplicationException.notFound("Figure"));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(figure.mime()))
                .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePrivate().immutable())
                .header("X-Content-Type-Options", "nosniff")
                .body(storage.load(figure.storageKey()));
    }
}
