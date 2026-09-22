package com.modushield.gateway.testsupport;

import com.modushield.gateway.error.ErrorResponseWriter;
import com.modushield.gateway.policy.PolicyDecision;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Doble de prueba para ErrorResponseWriter. SOLO para pruebas unitarias de Access
 * mientras Java A - Nucleo publica la implementacion real (que arma el JSON del
 * contrato: timestamp, status, error, message, path, requestId). No usar en produccion.
 */
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
