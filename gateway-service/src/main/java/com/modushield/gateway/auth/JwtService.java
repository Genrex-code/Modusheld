package com.modushield.gateway.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

@Service
public class JwtService {

    private final SecretKey signingKey;
    private final long expirationSeconds;
    private final Clock clock;

    @Autowired
    public JwtService(AuthProperties properties) {
        this(properties, Clock.systemUTC());
    }

    JwtService(AuthProperties properties, Clock clock) {
        String secret = properties.getJwtSecret();
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("JWT_SECRET must be configured with at least 32 bytes");
        }
        if (properties.getJwtExpirationSeconds() <= 0) {
            throw new IllegalStateException("JWT_EXPIRATION_SECONDS must be greater than zero");
        }
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationSeconds = properties.getJwtExpirationSeconds();
        this.clock = clock;
    }

    public Token createToken(UserAccount account) {
        Instant issuedAt = clock.instant();
        Instant expiresAt = issuedAt.plusSeconds(expirationSeconds);
        String value = Jwts.builder()
                .subject(account.username())
                .claim("role", account.role().name())
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .signWith(signingKey)
                .compact();
        return new Token(value, expiresAt);
    }

    public Optional<AuthenticatedUser> validate(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .clock(() -> Date.from(clock.instant()))
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            String username = claims.getSubject();
            String roleClaim = claims.get("role", String.class);
            if (username == null || username.isBlank() || roleClaim == null) {
                return Optional.empty();
            }
            return Optional.of(new AuthenticatedUser(username, UserRole.valueOf(roleClaim)));
        } catch (JwtException | IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    public record Token(String value, Instant expiresAt) {
    }

    public record AuthenticatedUser(String username, UserRole role) {
    }
}
