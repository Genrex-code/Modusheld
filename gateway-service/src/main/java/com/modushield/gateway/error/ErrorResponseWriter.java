package com.modushield.gateway.error;

import com.modushield.gateway.policy.PolicyDecision;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Contrato compartido (plan maestro seccion 6.1 / manual seccion 5.2).
 * La IMPLEMENTACION real (el JSON exacto: timestamp, status, error, message, path, requestId)
 * la construye Java A - Nucleo. Access solo depende de esta interfaz.
 */
public interface ErrorResponseWriter {

    Mono<Void> write(ServerWebExchange exchange, PolicyDecision decision);
}
