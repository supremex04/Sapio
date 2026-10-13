package com.sapio.knowledge.figure;

import java.time.Clock;
import java.time.Duration;
import java.util.UUID;

import com.sapio.common.web.UrlSignature;
import com.sapio.knowledge.KnowledgeProperties;
import org.springframework.stereotype.Component;

/**
 * Signed, expiring figure URLs, so plain {@code <img>} tags can load figures without an Authorization header.
 * Expiry is aligned to the hour, so a figure's URL stays identical for an hour and browsers can cache it.
 */
@Component
public class FigureUrlSigner {

    private final UrlSignature signature;
    private final Duration ttl;
    private final Clock clock;

    FigureUrlSigner(KnowledgeProperties properties, Clock clock) {
        this.signature = new UrlSignature(properties.figureUrlSecret());
        this.ttl = properties.figureUrlTtl();
        this.clock = clock;
    }

    public String url(UUID figureId) {
        long expires = UrlSignature.expiry(clock.instant(), ttl);
        return "/api/figures/" + figureId + "/image?exp=" + expires + "&sig=" + signature.sign(payload(figureId, expires));
    }

    public boolean verify(UUID figureId, long expires, String sig) {
        return signature.verify(payload(figureId, expires), expires, clock.instant(), sig);
    }

    private static String payload(UUID figureId, long expires) {
        return figureId + ":" + expires;
    }
}
