package com.modushield.gateway.testsupport;

import com.modushield.gateway.error.ErrorResponseWriter;
import com.modushield.gateway.policy.PolicyDecision;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/** Test double for isolated Access unit tests. Never used in production. */
public class NoOpErrorResponseWriter implements ErrorResponseWriter {

    private PolicyDecision lastDecision;

    @Override
    public Mono<Void> write(ServerWebExchange exchange, PolicyDecision decision) {
        this.lastDecision = decision;
        exchange.getResponse().setRawStatusCode(decision.status().value());
        return Mono.empty();
    }

    public PolicyDecision getLastDecision() {
        return lastDecision;
    }
}
