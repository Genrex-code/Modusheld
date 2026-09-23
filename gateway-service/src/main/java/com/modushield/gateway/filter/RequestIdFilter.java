package com.modushield.gateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Creates a correlation id when it is absent and preserves a safe client id
 * when it is present. The same value is forwarded upstream and returned to the
 * client.
 */
@Component
public class RequestIdFilter implements GlobalFilter, Ordered {

    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    public static final String REQUEST_ID_ATTRIBUTE =
            RequestIdFilter.class.getName() + ".requestId";

    private static final int ORDER = -100;
    private static final Pattern SAFE_REQUEST_ID =
            Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}");

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String supplied = exchange.getRequest().getHeaders().getFirst(REQUEST_ID_HEADER);
        String requestId = isSafe(supplied) ? supplied : UUID.randomUUID().toString();

        ServerHttpRequest request = exchange.getRequest()
                .mutate()
                .headers(headers -> headers.set(REQUEST_ID_HEADER, requestId))
                .build();

        ServerWebExchange correlatedExchange = exchange.mutate().request(request).build();
        correlatedExchange.getAttributes().put(REQUEST_ID_ATTRIBUTE, requestId);
        correlatedExchange.getResponse().getHeaders().set(REQUEST_ID_HEADER, requestId);

        return chain.filter(correlatedExchange);
    }

    @Override
    public int getOrder() {
        return ORDER;
    }

    private boolean isSafe(String candidate) {
        return candidate != null && SAFE_REQUEST_ID.matcher(candidate).matches();
    }
}
