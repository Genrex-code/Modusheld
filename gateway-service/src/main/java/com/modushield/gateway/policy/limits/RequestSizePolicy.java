package com.modushield.gateway.policy.limits;

import com.modushield.gateway.error.ErrorResponseWriter;
import com.modushield.gateway.policy.GatewayPolicy;
import com.modushield.gateway.policy.PolicyDecision;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Rejects unverifiable or oversized request bodies before routing upstream. */
@Component
public class RequestSizePolicy implements GlobalFilter, Ordered, GatewayPolicy {

    private static final int ORDER = 50;
    private static final Set<HttpMethod> BODY_METHODS = Set.of(
            HttpMethod.POST,
            HttpMethod.PUT,
            HttpMethod.PATCH
    );

    private final long maxSizeBytes;
    private final ErrorResponseWriter errorResponseWriter;

    public RequestSizePolicy(
            @Value("${modushield.limits.max-request-size-bytes:8192}") long maxSizeBytes,
            ErrorResponseWriter errorResponseWriter
    ) {
        if (maxSizeBytes <= 0) {
            throw new IllegalArgumentException("maxSizeBytes must be greater than zero");
        }
        this.maxSizeBytes = maxSizeBytes;
        this.errorResponseWriter = Objects.requireNonNull(
                errorResponseWriter,
                "errorResponseWriter"
        );
    }

    @Override
    public int getOrder() {
        return ORDER;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        PolicyDecision decision = evaluate(exchange);
        if (!decision.allowed()) {
            return errorResponseWriter.write(exchange, decision);
        }
        return chain.filter(exchange);
    }

    @Override
    public PolicyDecision evaluate(ServerWebExchange exchange) {
        HttpHeaders headers = exchange.getRequest().getHeaders();
        if (headers.containsKey(HttpHeaders.TRANSFER_ENCODING)) {
            return payloadTooLarge();
        }

        List<String> contentLengths = headers.get(HttpHeaders.CONTENT_LENGTH);
        String contentLength = contentLengths == null || contentLengths.isEmpty()
                ? null
                : contentLengths.get(0);

        if (contentLength == null) {
            boolean unknownBodyLength = BODY_METHODS.contains(exchange.getRequest().getMethod());
            return unknownBodyLength ? payloadTooLarge() : PolicyDecision.allow();
        }

        if (contentLengths.size() != 1) {
            return payloadTooLarge();
        }

        final long declaredSize;
        try {
            declaredSize = Long.parseLong(contentLength.trim());
        } catch (NumberFormatException exception) {
            return payloadTooLarge();
        }

        if (declaredSize < 0 || declaredSize > maxSizeBytes) {
            return payloadTooLarge();
        }

        return PolicyDecision.allow();
    }

    long maxSizeBytes() {
        return maxSizeBytes;
    }

    private PolicyDecision payloadTooLarge() {
        return new PolicyDecision(
                false,
                HttpStatus.PAYLOAD_TOO_LARGE,
                "PAYLOAD_TOO_LARGE",
                "SIZE",
                "Request payload exceeds the maximum allowed size or cannot be safely measured"
        );
    }
}
