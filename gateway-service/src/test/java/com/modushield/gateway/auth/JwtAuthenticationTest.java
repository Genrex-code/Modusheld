package com.modushield.gateway.auth;

import com.modushield.gateway.error.ErrorResponseWriter;
import com.modushield.gateway.policy.PolicyDecision;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class JwtAuthenticationTest {

    private static final String SECRET = "unit-test-secret-that-is-longer-than-32-bytes";
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    private AuthProperties properties;
    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        properties = new AuthProperties();
        properties.setJwtSecret(SECRET);
        properties.setJwtExpirationSeconds(60);
        jwtService = new JwtService(properties, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void tokenContainsValidatedIdentityAndRole() {
        String token = tokenFor("reader", UserRole.USER);

        JwtService.AuthenticatedUser user = jwtService.validate(token).orElseThrow();

        assertThat(user.username()).isEqualTo("reader");
        assertThat(user.role()).isEqualTo(UserRole.USER);
    }

    @Test
    void rejectsTamperedAndExpiredTokens() {
        String token = tokenFor("reader", UserRole.USER);
        int payloadStart = token.indexOf('.') + 2;
        char replacement = token.charAt(payloadStart) == 'a' ? 'b' : 'a';
        String tampered = token.substring(0, payloadStart) + replacement + token.substring(payloadStart + 1);
        JwtService later = new JwtService(
                properties,
                Clock.fixed(NOW.plusSeconds(61), ZoneOffset.UTC));

        assertThat(jwtService.validate(tampered)).isEmpty();
        assertThat(later.validate(token)).isEmpty();
    }

    @Test
    void userMayReadButCannotWriteProducts() {
        CapturingWriter writer = new CapturingWriter();
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtService, writer);
        AtomicBoolean getContinued = new AtomicBoolean();
        AtomicBoolean postContinued = new AtomicBoolean();

        filter.filter(exchange(HttpMethod.GET, "/api/products", tokenFor("reader", UserRole.USER)),
                ignored -> {
                    getContinued.set(true);
                    return Mono.empty();
                }).block();
        filter.filter(exchange(HttpMethod.POST, "/api/products", tokenFor("reader", UserRole.USER)),
                ignored -> {
                    postContinued.set(true);
                    return Mono.empty();
                }).block();

        assertThat(getContinued).isTrue();
        assertThat(postContinued).isFalse();
        assertThat(writer.lastDecision.get().status()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void adminMayWriteAndMissingTokenIsUnauthorized() {
        CapturingWriter writer = new CapturingWriter();
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtService, writer);
        AtomicBoolean continued = new AtomicBoolean();

        filter.filter(exchange(HttpMethod.DELETE, "/api/products/P-100", tokenFor("root", UserRole.ADMIN)),
                ignored -> {
                    continued.set(true);
                    return Mono.empty();
                }).block();
        filter.filter(exchange(HttpMethod.GET, "/api/products", null), ignored -> Mono.empty()).block();

        assertThat(continued).isTrue();
        assertThat(writer.lastDecision.get().status()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(writer.lastDecision.get().code()).isEqualTo("INVALID_TOKEN");
    }

    private String tokenFor(String username, UserRole role) {
        return jwtService.createToken(new UserAccount(username, "unused", role)).value();
    }

    private MockServerWebExchange exchange(HttpMethod method, String path, String token) {
        MockServerHttpRequest.BaseBuilder<?> request = MockServerHttpRequest.method(method, path);
        if (token != null) {
            request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        return MockServerWebExchange.from(request.build());
    }

    private static final class CapturingWriter implements ErrorResponseWriter {
        private final AtomicReference<PolicyDecision> lastDecision = new AtomicReference<>();

        @Override
        public Mono<Void> write(org.springframework.web.server.ServerWebExchange exchange,
                                PolicyDecision decision) {
            lastDecision.set(decision);
            return Mono.empty();
        }
    }
}
