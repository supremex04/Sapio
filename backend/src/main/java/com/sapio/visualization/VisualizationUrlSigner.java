package com.sapio.visualization;

import java.time.Clock;
import java.time.Duration;
import java.util.UUID;

import com.sapio.common.web.UrlSignature;
import org.springframework.stereotype.Component;

/**
 * Signed, expiring URLs for a visualization's files (video, thumbnail, narration, protein structure), so
 * {@code <video>}, {@code <img>}, {@code <audio>} and the 3D viewer load them without an Authorization header.
 */
@Component
class VisualizationUrlSigner {

    enum Asset { video, thumbnail, audio, structure }

    private final UrlSignature signature;
    private final Duration ttl;
    private final Clock clock;

    VisualizationUrlSigner(VisualizationProperties properties, Clock clock) {
        this.signature = new UrlSignature(properties.urlSecret());
        this.ttl = properties.urlTtl();
        this.clock = clock;
    }

    String url(UUID id, Asset asset) {
        long expires = UrlSignature.expiry(clock.instant(), ttl);
        return "/api/visualizations/" + id + "/" + asset + "?exp=" + expires + "&sig="
                + signature.sign(payload(id, asset, expires));
    }

    boolean verify(UUID id, Asset asset, long expires, String sig) {
        return signature.verify(payload(id, asset, expires), expires, clock.instant(), sig);
    }

    private static String payload(UUID id, Asset asset, long expires) {
        return "viz:" + id + ":" + asset + ":" + expires; // namespaced: never opens a figure or a recording
    }
}
