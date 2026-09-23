package com.modushield.gateway.error;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.modushield.gateway.policy.PolicyDecision;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class JsonErrorResponseWriterTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final JsonErrorResponseWriter writer = new JsonErrorResponseWriter(
            objectMapper,
            Clock.fixed(Instant.parse("2026-09-24T18:30:00Z"), ZoneOffset.UTC));

    @Test
    void writesTheSharedErrorContract() throws Exception {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/admin/status")
                        .header("X-Request-Id", "test-123")
                        .build());
        PolicyDecision decision = new PolicyDecision(
                false,
                HttpStatus.FORBIDDEN,
                "ROUTE_NOT_ALLOWED",
                "ROUTE",
                "Route is not permitted");

        writer.write(exchange, decision).block();

        String json = exchange.getResponse().getBodyAsString().block();
        Map<String, Object> body = objectMapper.readValue(json, new TypeReference<>() { });
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(exchange.getResponse().getHeaders().getContentType().toString())
                .isEqualTo("application/json");
        assertThat(body).containsEntry("timestamp", "2026-09-24T18:30:00Z")
                .containsEntry("status", 403)
                .containsEntry("error", "ROUTE_NOT_ALLOWED")
                .containsEntry("message", "Route is not permitted")
                .containsEntry("path", "/api/admin/status")
                .containsEntry("requestId", "test-123")
                .doesNotContainKey("code");
    }
}
