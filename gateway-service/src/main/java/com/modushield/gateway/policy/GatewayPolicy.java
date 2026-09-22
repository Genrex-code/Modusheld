package com.modushield.gateway.policy;

import org.springframework.web.server.ServerWebExchange;

/**
 * Contrato compartido (plan maestro seccion 6.1 / manual seccion 5.2).
 * Ver nota de propiedad en PolicyDecision.java: referencia, no propiedad de Access.
 */
public interface GatewayPolicy {

    PolicyDecision evaluate(ServerWebExchange exchange);
}
