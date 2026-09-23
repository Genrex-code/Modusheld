package com.modushield.gateway.error;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.modushield.gateway.audit.AuditContext;
import com.modushield.gateway.filter.RequestIdFilter;
import com.modushield.gateway.policy.PolicyDecision;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Writes every gateway rejection using the shared public JSON contract. */
@Component
public class JsonErrorResponseWriter implements ErrorResponseWriter {

    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Autowired
    public JsonErrorResponseWriter(ObjectMapper objectMapper) {
        this(objectMapper, Clock.systemUTC());
    }

    JsonErrorResponseWriter(ObjectMapper objectMapper, Clock clock) {
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    public Mono<Void> write(ServerWebExchange exchange, PolicyDecision decision) {
        Objects.requireNonNull(exchange, "exchange");
        Objects.requireNonNull(decision, "decision");
        if (decision.allowed() || decision.status() == null) {
            return Mono.error(new IllegalArgumentException("Only denied decisions can be written"));
        }
        if (exchange.getResponse().isCommitted()) {
            return Mono.empty();
        }

        AuditContext.record(exchange, decision);
        String requestId = resolveRequestId(exchange);
        exchange.getResponse().setStatusCode(decision.status());
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        exchange.getResponse().getHeaders().set(RequestIdFilter.REQUEST_ID_HEADER, requestId);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now(clock).toString());
        body.put("status", decision.status().value());
        body.put("error", decision.code());
        body.put("message", decision.safeMessage());
        body.put("path", exchange.getRequest().getPath().value());
        body.put("requestId", requestId);

        try {
            byte[] bytes = objectMapper.writeValueAsBytes(body);
            DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);
            return exchange.getResponse().writeWith(Mono.just(buffer));
        } catch (JsonProcessingException serializationFailure) {
            return Mono.error(serializationFailure);
        }
    }

    private String resolveRequestId(ServerWebExchange exchange) {
        Object attribute = exchange.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
        if (attribute instanceof String requestId && !requestId.isBlank()) {
            return requestId;
        }

        String header = exchange.getRequest().getHeaders()
                .getFirst(RequestIdFilter.REQUEST_ID_HEADER);
        if (header != null && !header.isBlank()) {
            return header;
        }
        return UUID.randomUUID().toString();
    }
}
