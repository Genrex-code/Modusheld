package com.modushield.gateway.policy;

import org.springframework.http.HttpStatus;

/** Shared result returned by every independent gateway policy. */
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
