package com.modushield.gateway.policy.access;

import com.modushield.gateway.policy.PolicyDecision;
import com.modushield.gateway.testsupport.NoOpErrorResponseWriter;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pruebas parametrizadas segun manual seccion 9.3.4:
 * cada par ruta-metodo de la tabla congelada, mas dos rutas desconocidas.
 *
 * Columnas del CSV: metodo, ruta, permitido esperado, status HTTP esperado (vacio si permitido).
 */
class RouteMethodPolicyTest {

    private final RouteMethodPolicy policy = new RouteMethodPolicy(new NoOpErrorResponseWriter());

    @ParameterizedTest(name = "{0} {1} -> permitido={2}")
    @CsvSource({
            "GET,   /health,             true,  ",
            "GET,   /api/products,       true,  ",
            "POST,  /api/orders,         true,  ",
            "GET,   /api/admin/status,   false, 403",
            "POST,  /api/admin/status,   false, 403",
            "DELETE,/api/products,       false, 405",
            "POST,  /api/products,       false, 405",
            "GET,   /api/orders,         false, 405",
            "GET,   /api/unknown,        false, 403",
            "GET,   /totally/not/mapped, false, 403"
    })
    void evaluaCadaParRutaMetodo(String method, String path, boolean expectedAllowed, String expectedStatus) {
        ServerWebExchange exchange = exchangeFor(HttpMethod.valueOf(method.trim()), path.trim());

        PolicyDecision decision = policy.evaluate(exchange);

        assertThat(decision.allowed()).isEqualTo(expectedAllowed);
        if (!expectedAllowed) {
            assertThat(decision.status()).isEqualTo(HttpStatus.valueOf(Integer.parseInt(expectedStatus.trim())));
        }
    }

    @ParameterizedTest(name = "ruta ambigua rechazada: {0}")
    @CsvSource({
            "/api/../admin/status",
            "/api/%2e%2e/admin/status",
            "/api//products",
            "/api/products%2f..%2fadmin"
    })
    void rechazaRutasAmbiguasOConIntentoDeEvasion(String path) {
        ServerWebExchange exchange = exchangeFor(HttpMethod.GET, path);

        PolicyDecision decision = policy.evaluate(exchange);

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.status()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(decision.code()).isEqualTo("ROUTE_NOT_ALLOWED");
    }

    @ParameterizedTest(name = "tolera slash final: {0}")
    @CsvSource({
            "/health/",
            "/api/products/"
    })
    void normalizaSlashFinalSinCambiarLaDecision(String path) {
        ServerWebExchange exchange = exchangeFor(HttpMethod.GET, path);

        PolicyDecision decision = policy.evaluate(exchange);

        assertThat(decision.allowed()).isTrue();
    }

    private ServerWebExchange exchangeFor(HttpMethod method, String path) {
        return MockServerWebExchange.from(MockServerHttpRequest.method(method, path).build());
    }
}
