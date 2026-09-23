package com.modushield.gateway.error;

import com.modushield.gateway.policy.PolicyDecision;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Shared rejection contract. Policies depend on this interface and the Core
 * implementation {@link JsonErrorResponseWriter} owns the public JSON shape.
 */
public interface ErrorResponseWriter {

    Mono<Void> write(ServerWebExchange exchange, PolicyDecision decision);
}
