package com.ved.document;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import com.ved.common.web.UrlSignature;
import org.springframework.stereotype.Component;

/**
 * Signed, expiring playback URLs for audio / video documents: an {@code <audio>} or {@code <video>} element streams
 * them (with seeking) without an Authorization header.
 */
@Component
public class MediaUrlSigner {

    private final UrlSignature signature;
    private final Duration ttl;
    private final Clock clock;

    MediaUrlSigner(DocumentProperties properties, Clock clock) {
        this.signature = new UrlSignature(properties.mediaUrlSecret());
        this.ttl = properties.mediaUrlTtl();
        this.clock = clock;
    }

    public record SignedUrl(String url, Instant expiresAt) {
    }

    public SignedUrl url(UUID documentId) {
        long expires = UrlSignature.expiry(clock.instant(), ttl);
        return new SignedUrl("/api/media/" + documentId + "?exp=" + expires + "&sig="
                + signature.sign(payload(documentId, expires)), Instant.ofEpochSecond(expires));
    }

    public boolean verify(UUID documentId, long expires, String sig) {
        return signature.verify(payload(documentId, expires), expires, clock.instant(), sig);
    }

    private static String payload(UUID documentId, long expires) {
        return "media:" + documentId + ":" + expires; // namespaced: a figure signature never opens a recording
    }
}
