package com.modushield.gateway.policy.limits;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ServerWebExchange;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequestSizePolicyTest {

    @Test
    void shouldAllowPayloadUnder8KB() {
        RequestSizePolicy policy = new RequestSizePolicy();

        ReflectionTestUtils.setField(policy, "maxSizeBytes", 8192L);

        MockServerHttpRequest request = MockServerHttpRequest
                .post("/api/orders")
                .header("Content-Length", "8192")
                .build();

        ServerWebExchange exchange = MockServerWebExchange.from(request);

        PolicyDecision decision = policy.evaluate(exchange);

        assertTrue(decision.allowed());
    }

    @Test
    void shouldRejectPayloadOver8KB() {
        RequestSizePolicy policy = new RequestSizePolicy();

        ReflectionTestUtils.setField(policy, "maxSizeBytes", 8192L);

        MockServerHttpRequest request = MockServerHttpRequest
                .post("/api/orders")
                .header("Content-Length", "8193")
                .build();

        ServerWebExchange exchange = MockServerWebExchange.from(request);

        PolicyDecision decision = policy.evaluate(exchange);

        assertFalse(decision.allowed());
        assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, decision.status());
        assertEquals("PAYLOAD_TOO_LARGE", decision.code());
    }
}