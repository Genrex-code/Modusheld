package com.modushield.gateway.policy.access;

import com.modushield.gateway.error.ErrorResponseWriter;
import com.modushield.gateway.policy.GatewayPolicy;
import com.modushield.gateway.policy.PolicyDecision;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Java B - Politicas de acceso.
 * Rama: feature/access-policies
 * Contrato: manual seccion 9.2, plan maestro seccion 5.2 / 5.3.
 *
 * Reglas implementadas:
 *  - MODUSHIELD_API_KEY se lee al iniciar; si esta vacia, el gateway falla explicitamente
 *    (no se permite arrancar aceptando todo).
 *  - /health y /api/products/** estan exentos: productos se autentica con JWT.
 *  - Se lee exactamente una cabecera X-API-Key y se compara con el valor configurado.
 *  - Ante ausencia o diferencia -> 401 INVALID_API_KEY via ErrorResponseWriter.
 *  - Nunca se registra la clave completa (ver AuditFilter, propiedad de Java D:
 *    ese filtro solo debe recibir/mostrar keyPresent=true/false, nunca el valor).
 *
 * NOTA DE ORDEN: el plan maestro (seccion 3.2, congelado) coloca ApiKeyFilter
 * DESPUES de RouteMethodPolicy. El manual (seccion 5.1) lo pone antes. Ante la
 * contradiccion, el propio manual dice que prevalece el plan maestro, por eso
 * este filtro corre despues de RouteMethodPolicy (ver getOrder()). Confirmar con
 * Jairo si el equipo decide oficialmente lo contrario.
 */
@Component
public class ApiKeyFilter implements GlobalFilter, Ordered, GatewayPolicy {

    private static final String API_KEY_HEADER = "X-API-Key";
    private static final List<String> EXEMPT_PATHS = List.of("/health");

    private final AccessProperties accessProperties;
    private final ErrorResponseWriter errorResponseWriter;

    public ApiKeyFilter(AccessProperties accessProperties, ErrorResponseWriter errorResponseWriter) {
        if (accessProperties.getApiKey() == null || accessProperties.getApiKey().isBlank()) {
            throw new IllegalStateException(
                    "MODUSHIELD_API_KEY no esta configurada. El gateway no debe iniciar sin una clave valida.");
        }
        this.accessProperties = accessProperties;
        this.errorResponseWriter = errorResponseWriter;
    }

    @Override
    public int getOrder() {
        // JWT protege productos en 40; esta politica heredada protege ordenes en 45.
        return 45;
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
        String path = normalize(request.getPath().value());

        // Product operations use JWT authentication in JwtAuthenticationFilter.
        if (EXEMPT_PATHS.contains(path) || isProductPath(path)) {
            return PolicyDecision.allow();
        }

        List<String> headerValues = request.getHeaders().get(API_KEY_HEADER);
        boolean exactlyOneHeader = headerValues != null && headerValues.size() == 1;
        String providedKey = exactlyOneHeader ? headerValues.get(0) : null;

        if (providedKey == null || providedKey.isBlank()) {
            return invalidKey();
        }

        if (!constantTimeEquals(providedKey, accessProperties.getApiKey())) {
            return invalidKey();
        }

        return PolicyDecision.allow();
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

    private boolean isProductPath(String path) {
        return "/api/products".equals(path) || path.startsWith("/api/products/");
    }

    private PolicyDecision invalidKey() {
        return new PolicyDecision(
                false,
                HttpStatus.UNAUTHORIZED,
                "INVALID_API_KEY",
                "API_KEY",
                "Missing or invalid API key");
    }

    /**
     * Comparacion en tiempo constante: evita que un atacante deduzca la clave
     * midiendo cuanto tarda la comparacion caracter por caracter.
     */
    private boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        byte[] aBytes = a.getBytes(StandardCharsets.UTF_8);
        byte[] bBytes = b.getBytes(StandardCharsets.UTF_8);
        int diff = aBytes.length ^ bBytes.length;
        int shortest = Math.min(aBytes.length, bBytes.length);
        for (int i = 0; i < shortest; i++) {
            diff |= aBytes[i] ^ bBytes[i];
        }
        return diff == 0;
    }
}
