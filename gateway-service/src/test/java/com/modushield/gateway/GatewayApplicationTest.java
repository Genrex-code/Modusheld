package com.modushield.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "modushield.access.api-key=integration-key",
                "spring.cloud.gateway.routes[0].id=demo-api-test",
                "spring.cloud.gateway.routes[0].uri=http://127.0.0.1:1",
                "spring.cloud.gateway.routes[0].predicates[0]=Path=/api/**"
        })
@AutoConfigureWebTestClient
class GatewayApplicationTest {

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void startsAndExposesHealth() {
        webTestClient.get()
                .uri("/health")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("UP");
    }

    @Test
    void rejectsMissingKeyUsingTheSharedJsonContract() {
        webTestClient.get()
                .uri("/api/products")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectHeader().exists("X-Request-Id")
                .expectBody()
                .jsonPath("$.status").isEqualTo(401)
                .jsonPath("$.error").isEqualTo("INVALID_API_KEY")
                .jsonPath("$.path").isEqualTo("/api/products")
                .jsonPath("$.requestId").isNotEmpty();
    }

    @Test
    void blocksAdminRouteBeforeCallingTheUpstream() {
        webTestClient.get()
                .uri("/api/admin/status")
                .header("X-API-Key", "integration-key")
                .exchange()
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.error").isEqualTo("ROUTE_NOT_ALLOWED");
    }

    @Test
    void mapsUnavailableUpstreamToBadGateway() {
        webTestClient.get()
                .uri("/api/products")
                .header("X-API-Key", "integration-key")
                .exchange()
                .expectStatus().isEqualTo(502)
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.error").isEqualTo("UPSTREAM_UNAVAILABLE")
                .jsonPath("$.requestId").isNotEmpty();
    }
}
