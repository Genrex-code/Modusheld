package com.modushield.gateway.filter;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class RequestIdFilterTest {

    private final RequestIdFilter filter = new RequestIdFilter();

    @Test
    void createsAndForwardsRequestIdWhenMissing() {
        MockServerWebExchange exchange = exchange(null);
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();

        filter.filter(exchange, nextExchange -> {
            forwarded.set(nextExchange);
            return Mono.empty();
        }).block();

        String requestId = forwarded.get().getRequest().getHeaders()
                .getFirst(RequestIdFilter.REQUEST_ID_HEADER);
        assertThat(requestId).isNotBlank();
        assertThat(exchange.getResponse().getHeaders()
                .getFirst(RequestIdFilter.REQUEST_ID_HEADER)).isEqualTo(requestId);
    }

    @Test
    void preservesSafeRequestId() {
        MockServerWebExchange exchange = exchange("test-123");
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();

        filter.filter(exchange, nextExchange -> {
            forwarded.set(nextExchange);
            return Mono.empty();
        }).block();

        assertThat(forwarded.get().getRequest().getHeaders()
                .getFirst(RequestIdFilter.REQUEST_ID_HEADER)).isEqualTo("test-123");
        assertThat(exchange.getResponse().getHeaders()
                .getFirst(RequestIdFilter.REQUEST_ID_HEADER)).isEqualTo("test-123");
    }

    @Test
    void replacesUnsafeRequestIdToProtectLogs() {
        MockServerWebExchange exchange = exchange("id with spaces");
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();

        filter.filter(exchange, nextExchange -> {
            forwarded.set(nextExchange);
            return Mono.empty();
        }).block();

        assertThat(forwarded.get().getRequest().getHeaders()
                .getFirst(RequestIdFilter.REQUEST_ID_HEADER))
                .isNotBlank()
                .doesNotContain(" ");
    }

    private MockServerWebExchange exchange(String requestId) {
        MockServerHttpRequest.BaseBuilder<?> request =
                MockServerHttpRequest.method(HttpMethod.GET, "/api/products");
        if (requestId != null) {
            request.header(RequestIdFilter.REQUEST_ID_HEADER, requestId);
        }
        return MockServerWebExchange.from(request.build());
    }
}
