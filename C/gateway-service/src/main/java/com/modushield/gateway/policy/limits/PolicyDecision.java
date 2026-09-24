package com.modushield.gateway.policy.limits;

import org.springframework.http.HttpStatus;

public class PolicyDecision {

    private final boolean allowed;
    private final HttpStatus status;
    private final String code;
    private final String type;
    private final String message;

    public PolicyDecision(
            boolean allowed,
            HttpStatus status,
            String code
    ) {
        this(
                allowed,
                status,
                code,
                null,
                null
        );
    }

    public PolicyDecision(
            boolean allowed,
            HttpStatus status,
            String code,
            String type,
            String message
    ) {
        this.allowed = allowed;
        this.status = status;
        this.code = code;
        this.type = type;
        this.message = message;
    }

    public boolean allowed() {
        return allowed;
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }

    public String type() {
        return type;
    }

    public String message() {
        return message;
    }

    public static PolicyDecision allow() {
        return new PolicyDecision(
                true,
                HttpStatus.OK,
                null,
                null,
                null
        );
    }
}
