package com.modushield.gateway.audit;

import com.modushield.gateway.filter.RequestIdFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.time.Instant;

/** Emits exactly one sanitized audit event after every routed request. */
@Component
public class AuditFilter implements GlobalFilter, Ordered {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuditFilter.class);
    private static final int ORDER = -90;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return Mono.defer(() -> {
            long startedAt = System.nanoTime();
            return chain.filter(exchange)
                    .doFinally(ignored -> writeEvent(exchange, startedAt));
        });
    }

    @Override
    public int getOrder() {
        return ORDER;
    }

    private void writeEvent(ServerWebExchange exchange, long startedAt) {
        long durationMs = Math.max(0, (System.nanoTime() - startedAt) / 1_000_000);
        int statusCode = exchange.getResponse().getStatusCode() == null
                ? 200
                : exchange.getResponse().getStatusCode().value();

        String requestId = exchange.getAttributeOrDefault(
                RequestIdFilter.REQUEST_ID_ATTRIBUTE,
                exchange.getRequest().getHeaders()
                        .getFirst(RequestIdFilter.REQUEST_ID_HEADER));
        String apiKeyId = apiKeyId(exchange);

        LOGGER.info(
                "AUDIT timestamp={} requestId={} sourceIp={} method={} path={} "
                        + "decision={} rule={} status={} durationMs={} apiKeyId={}",
                Instant.now(),
                safe(requestId),
                safe(sourceIp(exchange)),
                exchange.getRequest().getMethod(),
                safe(exchange.getRequest().getURI().getRawPath()),
                safe(AuditContext.decision(exchange)),
                safe(AuditContext.rule(exchange)),
                statusCode,
                durationMs,
                apiKeyId);
    }

    private String apiKeyId(ServerWebExchange exchange) {
        if (!exchange.getRequest().getHeaders().containsKey("X-API-Key")) {
            return "none";
        }
        if ("DENY".equals(AuditContext.decision(exchange))
                && "API_KEY".equals(AuditContext.rule(exchange))) {
            return "unknown";
        }
        return "demo-client";
    }

    private String sourceIp(ServerWebExchange exchange) {
        InetSocketAddress remoteAddress = exchange.getRequest().getRemoteAddress();
        if (remoteAddress == null) {
            return "unknown";
        }
        if (remoteAddress.getAddress() != null) {
            return remoteAddress.getAddress().getHostAddress();
        }
        return remoteAddress.getHostString();
    }

    private String safe(Object value) {
        return value == null
                ? ""
                : value.toString().replaceAll("[\\r\\n\\t]", "_");
    }
}
