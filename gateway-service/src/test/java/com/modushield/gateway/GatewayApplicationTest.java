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
                "modushield.auth.jwt-secret=integration-test-secret-at-least-32-bytes-long",
                "modushield.auth.admin-username=test-admin",
                "modushield.auth.admin-password=test-password-123",
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
    void rejectsMissingTokenUsingTheSharedJsonContract() {
        webTestClient.get()
                .uri("/api/products")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectHeader().exists("X-Request-Id")
                .expectBody()
                .jsonPath("$.status").isEqualTo(401)
                .jsonPath("$.error").isEqualTo("INVALID_TOKEN")
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
                .header("Authorization", "Bearer " + adminToken())
                .exchange()
                .expectStatus().isEqualTo(502)
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.error").isEqualTo("UPSTREAM_UNAVAILABLE")
                .jsonPath("$.requestId").isNotEmpty();
    }

    @Test
    void registersAndLogsInUser() {
        webTestClient.post()
                .uri("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"username\":\"new-user\",\"password\":\"secure-pass-123\"}")
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.username").isEqualTo("new-user")
                .jsonPath("$.role").isEqualTo("USER");

        webTestClient.post()
                .uri("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"username\":\"new-user\",\"password\":\"secure-pass-123\"}")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.token").isNotEmpty()
                .jsonPath("$.tokenType").isEqualTo("Bearer")
                .jsonPath("$.role").isEqualTo("USER")
                .jsonPath("$.expiresAt").isNotEmpty();
    }

    @Test
    void rejectsInvalidLoginAndRegistrationContracts() {
        webTestClient.post()
                .uri("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"username\":\"missing-user\",\"password\":\"wrong-password\"}")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.status").isEqualTo(401)
                .jsonPath("$.error").isEqualTo("INVALID_CREDENTIALS");

        webTestClient.post()
                .uri("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"username\":\"x\",\"password\":\"secure-pass-123\"}")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.status").isEqualTo(400)
                .jsonPath("$.error").isEqualTo("INVALID_REGISTRATION");
    }

    @Test
    void rejectsDuplicateRegistration() {
        String body = "{\"username\":\"duplicate-user\",\"password\":\"secure-pass-123\"}";
        webTestClient.post()
                .uri("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange()
                .expectStatus().isCreated();

        webTestClient.post()
                .uri("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange()
                .expectStatus().isEqualTo(409)
                .expectBody()
                .jsonPath("$.status").isEqualTo(409)
                .jsonPath("$.error").isEqualTo("USERNAME_EXISTS");
    }

    private String adminToken() {
        byte[] body = webTestClient.post()
                .uri("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"username\":\"test-admin\",\"password\":\"test-password-123\"}")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .returnResult()
                .getResponseBody();
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper()
                    .readTree(body)
                    .get("token")
                    .asText();
        } catch (java.io.IOException exception) {
            throw new AssertionError(exception);
        }
    }
}
