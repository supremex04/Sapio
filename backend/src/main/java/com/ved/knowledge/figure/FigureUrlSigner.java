package com.ved.knowledge.figure;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.ved.knowledge.KnowledgeProperties;

import org.springframework.stereotype.Component;

/**
 * HMAC-signed, expiring figure URLs (the local equivalent of S3 presigned URLs), so plain {@code <img>}
 * tags can load figures without an Authorization header. Expiry is aligned to the hour, so a figure's URL
 * stays identical for an hour and browsers can cache it.
 */
@Component
public class FigureUrlSigner {

    private static final long HOUR = 3600;

    private final SecretKeySpec key;
    private final Duration ttl;
    private final Clock clock;

    FigureUrlSigner(KnowledgeProperties properties, Clock clock) {
        this.key = new SecretKeySpec(properties.figureUrlSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        this.ttl = properties.figureUrlTtl();
        this.clock = clock;
    }

    public String url(UUID figureId) {
        long now = clock.instant().getEpochSecond();
        long expires = ((now / HOUR) + 1) * HOUR + ttl.toSeconds();
        return "/api/figures/" + figureId + "/image?exp=" + expires + "&sig=" + sign(figureId, expires);
    }

    public boolean verify(UUID figureId, long expires, String signature) {
        if (signature == null || expires < clock.instant().getEpochSecond()) {
            return false;
        }
        return MessageDigest.isEqual(sign(figureId, expires).getBytes(StandardCharsets.US_ASCII),
                signature.getBytes(StandardCharsets.US_ASCII));
    }

    private String sign(UUID figureId, long expires) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(key);
            byte[] digest = mac.doFinal((figureId + ":" + expires).getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC unavailable", e);
        }
    }
}
