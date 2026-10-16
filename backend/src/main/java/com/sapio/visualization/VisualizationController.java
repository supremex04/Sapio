package com.sapio.visualization;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import com.sapio.security.CurrentUserId;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Visualizations API (docs/specs/visualizations.md). The video and thumbnail are authorised by the signature in their
 * URL; Spring serves Range requests on resource bodies, so the player can seek.
 */
@RestController
@RequestMapping("/api/visualizations")
class VisualizationController {

    private final VisualizationService visualizations;

    VisualizationController(VisualizationService visualizations) {
        this.visualizations = visualizations;
    }

    record CreateRequest(@NotBlank @Size(max = VisualizationService.MAX_REQUEST) String request, UUID documentId,
                         @Size(max = 20_000) String context) {
    }

    @PostMapping
    VisualizationService.View create(@CurrentUserId UUID userId, @Valid @RequestBody CreateRequest body) {
        return visualizations.create(userId, body.request(), body.documentId(), body.context());
    }

    @GetMapping
    List<VisualizationService.View> list(@CurrentUserId UUID userId) {
        return visualizations.list(userId);
    }

    @GetMapping("/{id}")
    VisualizationService.View get(@CurrentUserId UUID userId, @PathVariable UUID id) {
        return visualizations.get(userId, id);
    }

    @PostMapping("/{id}/retry")
    VisualizationService.View retry(@CurrentUserId UUID userId, @PathVariable UUID id) {
        return visualizations.retry(userId, id);
    }

    @DeleteMapping("/{id}")
    ResponseEntity<Void> delete(@CurrentUserId UUID userId, @PathVariable UUID id) {
        visualizations.delete(userId, id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/video")
    ResponseEntity<Resource> video(@PathVariable UUID id, @RequestParam long exp, @RequestParam String sig) {
        return file(visualizations.asset(id, VisualizationUrlSigner.Asset.video, exp, sig), "video/mp4");
    }

    @GetMapping("/{id}/thumbnail")
    ResponseEntity<Resource> thumbnail(@PathVariable UUID id, @RequestParam long exp, @RequestParam String sig) {
        return file(visualizations.asset(id, VisualizationUrlSigner.Asset.thumbnail, exp, sig), "image/png");
    }

    private static ResponseEntity<Resource> file(Resource content, String type) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(type))
                .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePrivate())
                .header("X-Content-Type-Options", "nosniff")
                .body(content);
    }
}
