package com.modushield.gateway.policy.limits;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RateLimitPolicy {

    // Máximo de solicitudes permitidas (5 por defecto)
    @Value("${modushield.limits.rate-limit-capacity:5}")
    private int capacity;

    // Ventana de tiempo en segundos (10 segundos por defecto)
    @Value("${modushield.limits.rate-limit-window-seconds:10}")
    private long windowSeconds;

    // Guarda en memoria cuántas peticiones lleva cada cliente
    private final Map<String, UserBucket> buckets = new ConcurrentHashMap<>();

    public PolicyDecision evaluate(ServerWebExchange exchange) {
        // Obtenemos la identidad del cliente (API Key o IP)
        String clientKey = exchange.getRequest().getHeaders().getFirst("X-API-Key");
        if (clientKey == null || clientKey.isBlank()) {
            clientKey = exchange.getRequest().getRemoteAddress() != null ?
                    exchange.getRequest().getRemoteAddress().getAddress().getHostAddress() : "unknown";
        }

        UserBucket bucket = buckets.computeIfAbsent(clientKey, k -> new UserBucket());

        synchronized (bucket) {
            long now = Instant.now().getEpochSecond();

            // Si pasaron más de 10 segundos desde el inicio de la ventana, reiniciamos el contador
            if (now - bucket.windowStart >= windowSeconds) {
                bucket.windowStart = now;
                bucket.requestCount = 0;
            }

            // Si ya alcanzó el límite de 5 peticiones, rechazamos con error 429
            if (bucket.requestCount >= capacity) {
                return new PolicyDecision(
                        false,
                        HttpStatus.TOO_MANY_REQUESTS,
                        "RATE_LIMIT_EXCEEDED",
                        "RATE_LIMIT",
                        "Rate limit exceeded. Maximum " + capacity + " requests allowed per " + windowSeconds + " seconds."
                );
            }

            // Aumentamos el contador y permitimos el acceso
            bucket.requestCount++;
            return PolicyDecision.allow();
        }
    }

    // Estructura interna para controlar la ventana de cada cliente
    private static class UserBucket {
        long windowStart = Instant.now().getEpochSecond();
        int requestCount = 0;
    }
}
