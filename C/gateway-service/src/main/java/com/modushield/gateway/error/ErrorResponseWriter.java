package com.modushield.gateway.error;

import com.modushield.gateway.policy.PolicyDecision;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Staging mirror of the Core rejection contract. C policies return a
 * PolicyDecision; only Core's writer owns the public JSON response.
 */
public interface ErrorResponseWriter {

    Mono<Void> write(ServerWebExchange exchange, PolicyDecision decision);
}
