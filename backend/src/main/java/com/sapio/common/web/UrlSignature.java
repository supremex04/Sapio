package com.sapio.common.web;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * HMAC-SHA256 signatures for expiring URLs (the local equivalent of S3 presigned URLs), so plain {@code <img>},
 * {@code <audio>} and {@code <video>} elements can load protected files without an Authorization header.
 */
public final class UrlSignature {

    private static final long HOUR = 3600;

    private final SecretKeySpec key;

    public UrlSignature(String secret) {
        this.key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    /**
     * Expiry aligned to the hour (then {@code ttl} on top), so a URL stays identical for an hour and browsers can
     * cache what it points to.
     */
    public static long expiry(Instant now, Duration ttl) {
        return ((now.getEpochSecond() / HOUR) + 1) * HOUR + ttl.toSeconds();
    }

    public String sign(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(key);
            byte[] digest = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC unavailable", e);
        }
    }

    /** Constant-time check of {@code signature} for {@code payload}, and that {@code expires} is not past. */
    public boolean verify(String payload, long expires, Instant now, String signature) {
        if (signature == null || expires < now.getEpochSecond()) {
            return false;
        }
        return MessageDigest.isEqual(sign(payload).getBytes(StandardCharsets.US_ASCII),
                signature.getBytes(StandardCharsets.US_ASCII));
    }
}
