package com.modushield.gateway.policy.access;

import com.modushield.gateway.policy.PolicyDecision;
import com.modushield.gateway.testsupport.NoOpErrorResponseWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pruebas obligatorias segun manual seccion 9.2.6:
 * clave valida, clave ausente, clave incorrecta, endpoint /health.
 * Se agrega ademas la verificacion de arranque fallido sin MODUSHIELD_API_KEY.
 */
class ApiKeyFilterTest {

    private static final String VALID_KEY = "demo-key-change-me";

    private ApiKeyFilter apiKeyFilter;

    @BeforeEach
    void setUp() {
        AccessProperties properties = new AccessProperties();
        properties.setApiKey(VALID_KEY);
        apiKeyFilter = new ApiKeyFilter(properties, new NoOpErrorResponseWriter());
    }

    @Test
    void permiteSolicitudConClaveValida() {
        ServerWebExchange exchange = exchangeFor(HttpMethod.POST, "/api/orders", VALID_KEY);

        PolicyDecision decision = apiKeyFilter.evaluate(exchange);

        assertThat(decision.allowed()).isTrue();
    }

    @Test
    void rechazaSolicitudSinClave() {
        ServerWebExchange exchange = exchangeFor(HttpMethod.POST, "/api/orders", null);

        PolicyDecision decision = apiKeyFilter.evaluate(exchange);

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.status()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(decision.code()).isEqualTo("INVALID_API_KEY");
    }

    @Test
    void rechazaSolicitudConClaveIncorrecta() {
        ServerWebExchange exchange = exchangeFor(HttpMethod.POST, "/api/orders", "clave-equivocada");

        PolicyDecision decision = apiKeyFilter.evaluate(exchange);

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.status()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(decision.code()).isEqualTo("INVALID_API_KEY");
    }

    @Test
    void permiteHealthSinCredencial() {
        ServerWebExchange exchange = exchangeFor(HttpMethod.GET, "/health", null);

        PolicyDecision decision = apiKeyFilter.evaluate(exchange);

        assertThat(decision.allowed()).isTrue();
    }

    @Test
    void rechazaSiHayMasDeUnaCabeceraApiKey() {
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.method(HttpMethod.POST, "/api/orders")
                        .header("X-API-Key", VALID_KEY)
                        .header("X-API-Key", "otra-clave")
                        .build());

        PolicyDecision decision = apiKeyFilter.evaluate(exchange);

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.status()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void fallaAlConstruirseSiLaClaveDeConfiguracionEstaVacia() {
        AccessProperties emptyProperties = new AccessProperties();
        emptyProperties.setApiKey("");

        assertThatThrownBy(() -> new ApiKeyFilter(emptyProperties, new NoOpErrorResponseWriter()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void productosUsanJwtYEstanExentosDeApiKey() {
        PolicyDecision decision = apiKeyFilter.evaluate(
                exchangeFor(HttpMethod.GET, "/api/products/P-100", null));

        assertThat(decision.allowed()).isTrue();
    }

    @Test
    void fallaAlConstruirseSiLaClaveDeConfiguracionEsNula() {
        AccessProperties nullProperties = new AccessProperties();

        assertThatThrownBy(() -> new ApiKeyFilter(nullProperties, new NoOpErrorResponseWriter()))
                .isInstanceOf(IllegalStateException.class);
    }

    private ServerWebExchange exchangeFor(HttpMethod method, String path, String apiKey) {
        MockServerHttpRequest.BaseBuilder<?> builder = MockServerHttpRequest.method(method, path);
        if (apiKey != null) {
            builder.header("X-API-Key", apiKey);
        }
        return MockServerWebExchange.from(builder.build());
    }
}
