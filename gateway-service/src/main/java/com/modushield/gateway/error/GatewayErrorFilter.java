package com.modushield.gateway.error;

import com.modushield.gateway.policy.PolicyDecision;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.ConnectException;
import java.net.UnknownHostException;
import java.util.concurrent.TimeoutException;

/** Converts routing failures and unexpected downstream errors to the shared contract. */
@Component
public class GatewayErrorFilter implements GlobalFilter, Ordered {

    private static final Logger LOGGER = LoggerFactory.getLogger(GatewayErrorFilter.class);
    // AuditFilter (owned by Java D) must use -90 so it can observe the final
    // status produced here for upstream and unexpected failures.
    private static final int ORDER = -80;

    private final ErrorResponseWriter errorResponseWriter;

    public GatewayErrorFilter(ErrorResponseWriter errorResponseWriter) {
        this.errorResponseWriter = errorResponseWriter;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return chain.filter(exchange).onErrorResume(exception -> {
            if (exchange.getResponse().isCommitted()) {
                return Mono.error(exception);
            }

            PolicyDecision decision;
            if (isUpstreamUnavailable(exception)) {
                decision = new PolicyDecision(
                        false,
                        HttpStatus.BAD_GATEWAY,
                        "UPSTREAM_UNAVAILABLE",
                        "UPSTREAM",
                        "Upstream service unavailable");
            } else {
                LOGGER.error("Unexpected gateway failure for {} {} (types={})",
                        exchange.getRequest().getMethod(),
                        exchange.getRequest().getPath(),
                        failureTypes(exception));
                decision = new PolicyDecision(
                        false,
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "INTERNAL_GATEWAY_ERROR",
                        "INTERNAL",
                        "Unexpected gateway error");
            }
            return errorResponseWriter.write(exchange, decision);
        });
    }

    @Override
    public int getOrder() {
        return ORDER;
    }

    private boolean isUpstreamUnavailable(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof ConnectException
                    || current instanceof UnknownHostException
                    || current instanceof TimeoutException) {
                return true;
            }
            String type = current.getClass().getSimpleName();
            if (type.contains("ConnectTimeout") || type.contains("ReadTimeout")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private String failureTypes(Throwable failure) {
        StringBuilder types = new StringBuilder();
        Throwable current = failure;
        while (current != null) {
            if (!types.isEmpty()) {
                types.append(" -> ");
            }
            types.append(current.getClass().getName());
            current = current.getCause();
        }
        return types.toString();
    }
}
