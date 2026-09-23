package com.modushield.gateway.policy.access;

import com.modushield.gateway.error.ErrorResponseWriter;
import com.modushield.gateway.policy.GatewayPolicy;
import com.modushield.gateway.policy.PolicyDecision;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Java B - Politicas de acceso.
 * Rama: feature/access-policies
 * Contrato: manual seccion 9.3 (tabla de allowlist), plan maestro seccion 5.1/5.2.
 *
 * Tabla congelada:
 *   /health              GET      -> permitir
 *   /api/products        GET      -> permitir
 *   /api/orders          POST     -> permitir
 *   /api/admin/status    GET      -> 403 ROUTE_NOT_ALLOWED (existe en demo-api a proposito,
 *                                     el bloqueo debe verse aqui, no en el backend)
 *   cualquier otra /api/** *      -> 403 ROUTE_NOT_ALLOWED
 *   ruta permitida + otro metodo  -> 405 METHOD_NOT_ALLOWED
 *
 * Logica (orden obligatorio, manual 9.3.1-9.3.3):
 *   1) normalizar la ruta sin decodificar de forma insegura ni aceptar variantes ambiguas
 *   2) comprobar primero si la ruta esta en la allowlist; si no, 403
 *   3) si la ruta existe pero el metodo no esta permitido, 405
 */
@Component
public class RouteMethodPolicy implements GlobalFilter, Ordered, GatewayPolicy {

    private static final Map<String, Set<HttpMethod>> ALLOWLIST = Map.of(
            "/health", Set.of(HttpMethod.GET),
            "/api/products", Set.of(HttpMethod.GET),
            "/api/orders", Set.of(HttpMethod.POST)
    );

    private final ErrorResponseWriter errorResponseWriter;

    public RouteMethodPolicy(ErrorResponseWriter errorResponseWriter) {
        this.errorResponseWriter = errorResponseWriter;
    }

    @Override
    public int getOrder() {
        // Corre antes de ApiKeyFilter (30 < 40), segun el orden congelado del plan maestro.
        return 30;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        PolicyDecision decision = evaluate(exchange);
        if (!decision.allowed()) {
            return errorResponseWriter.write(exchange, decision);
        }
        return chain.filter(exchange);
    }

    @Override
    public PolicyDecision evaluate(ServerWebExchange exchange) {
        ServerHttpRequest request = exchange.getRequest();
        // Inspect the raw URI before Spring's path representation can collapse
        // repeated separators or otherwise hide an ambiguous input.
        String rawPath = request.getURI().getRawPath();

        if (isAmbiguous(rawPath)) {
            return routeNotAllowed();
        }

        String normalized = normalize(rawPath);
        Set<HttpMethod> allowedMethods = ALLOWLIST.get(normalized);

        if (allowedMethods == null) {
            return routeNotAllowed();
        }

        HttpMethod method = request.getMethod();
        if (method == null || !allowedMethods.contains(method)) {
            return methodNotAllowed();
        }

        return PolicyDecision.allow();
    }

    /**
     * Rechaza variantes ambiguas o intentos de evasion (path traversal, doble
     * codificacion de "/", segmentos vacios repetidos) sin intentar decodificar
     * de forma "inteligente" — mejor negar por defecto.
     */
    private boolean isAmbiguous(String path) {
        if (path == null) {
            return true;
        }
        String lower = path.toLowerCase(Locale.ROOT);
        return lower.contains("..") || lower.contains("%2f") || lower.contains("%2e") || lower.contains("//");
    }

    private String normalize(String path) {
        if (path == null || path.isBlank()) {
            return "/";
        }
        String trimmed = path.trim();
        if (trimmed.length() > 1 && trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    private PolicyDecision routeNotAllowed() {
        return new PolicyDecision(
                false,
                HttpStatus.FORBIDDEN,
                "ROUTE_NOT_ALLOWED",
                "ROUTE",
                "Route is not permitted");
    }

    private PolicyDecision methodNotAllowed() {
        return new PolicyDecision(
                false,
                HttpStatus.METHOD_NOT_ALLOWED,
                "METHOD_NOT_ALLOWED",
                "METHOD",
                "Method is not permitted for this route");
    }
}
