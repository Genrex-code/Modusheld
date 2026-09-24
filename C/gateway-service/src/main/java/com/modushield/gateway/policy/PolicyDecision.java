package com.modushield.gateway.policy;

import org.springframework.http.HttpStatus;

/**
 * Staging mirror of the Core contract.
 *
 * <p>This copy lets the isolated C deliverable compile and be tested before
 * integration. Do not copy it into the main gateway: that module already owns
 * the canonical type with this same package and signature.</p>
 */
public record PolicyDecision(
        boolean allowed,
        HttpStatus status,
        String code,
        String rule,
        String safeMessage
) {

    public static PolicyDecision allow() {
        return new PolicyDecision(true, HttpStatus.OK, null, null, null);
    }
}
