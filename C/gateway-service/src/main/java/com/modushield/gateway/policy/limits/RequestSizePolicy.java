package com.modushield.gateway.policy.limits;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ServerWebExchange;

public class RequestSizePolicy {

    private long maxSizeBytes = 8192L;

    public PolicyDecision evaluate(ServerWebExchange exchange) {

        String contentLength = exchange.getRequest()
                .getHeaders()
                .getFirst("Content-Length");

        if (contentLength == null) {
            return new PolicyDecision(
                    true,
                    null,
                    null
            );
        }

        long size = Long.parseLong(contentLength);

        if (size > maxSizeBytes) {
            return new PolicyDecision(
                    false,
                    HttpStatus.PAYLOAD_TOO_LARGE,
                    "PAYLOAD_TOO_LARGE"
            );
        }

        return new PolicyDecision(
                true,
                null,
                null
        );
    }
}
