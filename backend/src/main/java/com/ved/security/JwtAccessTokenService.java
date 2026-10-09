package com.ved.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import com.ved.user.User;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

@Service
class JwtAccessTokenService implements AccessTokenService {

    static final String ROLES_CLAIM = "roles";
    static final String NAME_CLAIM = "name";

    private final JwtEncoder encoder;
    private final Clock clock;
    private final String issuer;
    private final Duration ttl;

    JwtAccessTokenService(JwtEncoder encoder, Clock clock, SecurityProperties properties) {
        this.encoder = encoder;
        this.clock = clock;
        this.issuer = properties.jwt().issuer();
        this.ttl = properties.jwt().accessTokenTtl();
    }

    @Override
    public AccessToken issue(User user) {
        Instant now = clock.instant();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject(user.getId().toString())
                .issuedAt(now)
                .expiresAt(now.plus(ttl))
                .claim(NAME_CLAIM, user.getFullName())
                .claim(ROLES_CLAIM, List.of(user.getRole().name()))
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new AccessToken(token, ttl.toSeconds());
    }
}
