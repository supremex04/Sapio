package com.ved.knowledge;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import com.ved.knowledge.figure.FigureUrlSigner;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FigureUrlSignerTest {

    @Test
    void signsVerifiesAndExpires() throws Exception {
        UUID id = UUID.randomUUID();
        var ctor = FigureUrlSigner.class.getDeclaredConstructor(KnowledgeProperties.class, Clock.class);
        ctor.setAccessible(true);
        Instant now = Instant.parse("2026-10-10T10:20:00Z");
        FigureUrlSigner s = ctor.newInstance(props("secret"), Clock.fixed(now, ZoneOffset.UTC));

        String url = s.url(id);
        long exp = Long.parseLong(url.replaceAll(".*exp=(\\d+).*", "$1"));
        String sig = url.replaceAll(".*sig=", "");
        assertThat(s.verify(id, exp, sig)).isTrue();
        assertThat(s.url(id)).isEqualTo(url); // stable within the hour (cacheable)
        assertThat(s.verify(UUID.randomUUID(), exp, sig)).isFalse();
        assertThat(s.verify(id, exp + 1, sig)).isFalse();

        FigureUrlSigner later = ctor.newInstance(props("secret"), Clock.fixed(Instant.ofEpochSecond(exp + 1), ZoneOffset.UTC));
        assertThat(later.verify(id, exp, sig)).isFalse();
        FigureUrlSigner otherKey = ctor.newInstance(props("other"), Clock.fixed(now, ZoneOffset.UTC));
        assertThat(otherKey.verify(id, exp, sig)).isFalse();
    }

    private static KnowledgeProperties props(String secret) {
        return new KnowledgeProperties(secret, Duration.ofHours(6), null, null);
    }
}
