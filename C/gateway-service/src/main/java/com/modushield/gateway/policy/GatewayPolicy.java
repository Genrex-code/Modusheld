package com.modushield.gateway.policy;

import org.springframework.web.server.ServerWebExchange;

/**
 * Staging mirror of the Core contract. The canonical interface lives in the
 * main gateway and must be reused when C is integrated.
 */
public interface GatewayPolicy {

    PolicyDecision evaluate(ServerWebExchange exchange);
}
