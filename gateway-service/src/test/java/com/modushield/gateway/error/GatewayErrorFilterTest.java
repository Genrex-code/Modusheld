package com.modushield.gateway.error;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.ConnectException;

import static org.assertj.core.api.Assertions.assertThat;

class GatewayErrorFilterTest {

    private final GatewayErrorFilter filter = new GatewayErrorFilter(
            new JsonErrorResponseWriter(new ObjectMapper()));

    @Test
    void mapsConnectionFailureToBadGateway() {
        MockServerWebExchange exchange = exchange();

        filter.filter(exchange, ignored -> Mono.error(new ConnectException("refused"))).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(exchange.getResponse().getBodyAsString().block())
                .contains("\"error\":\"UPSTREAM_UNAVAILABLE\"")
                .doesNotContain("refused");
    }

    @Test
    void mapsUnexpectedFailureWithoutLeakingItsMessage() {
        MockServerWebExchange exchange = exchange();

        filter.filter(exchange, ignored -> Mono.error(
                new IllegalStateException("sensitive internal detail"))).block();

        assertThat(exchange.getResponse().getStatusCode())
                .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(exchange.getResponse().getBodyAsString().block())
                .contains("\"error\":\"INTERNAL_GATEWAY_ERROR\"")
                .doesNotContain("sensitive internal detail");
    }

    private MockServerWebExchange exchange() {
        return MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/products")
                        .header("X-Request-Id", "gateway-error-test")
                        .build());
    }
}
