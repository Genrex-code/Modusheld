package com.modushield.demo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class DemoApiApplicationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void healthIsUp() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void productsAreAvailableWithoutDuplicatingGatewayAuthentication() throws Exception {
        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("P-100"));
    }

    @Test
    void createsSimulatedOrder() throws Exception {
        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":\"P-100\",\"quantity\":2}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.productId").value("P-100"))
                .andExpect(jsonPath("$.status").value("SIMULATED"));
    }

    @Test
    void acceptsTheExact8192ByteFixtureDirectly() throws Exception {
        byte[] payload = Files.readAllBytes(findRepositoryRoot()
                .resolve("client-tests/payload-8192.json"));
        assertThat(payload).hasSize(8192);

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.productId").value("P-100"));
    }

    @Test
    void adminEndpointExistsAndIsAllowedInsideThePrivateNetwork() throws Exception {
        mockMvc.perform(get("/api/admin/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INTERNAL_OK"));
    }

    @Test
    void unsupportedMethodReturnsMethodNotAllowed() throws Exception {
        mockMvc.perform(delete("/api/products"))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void performsCrudOnTheMainProductApi() throws Exception {
        String created = "{\"id\":\"P-CRUD\",\"name\":\"Created\",\"stock\":3}";
        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(created))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("P-CRUD"));

        mockMvc.perform(get("/api/products/P-CRUD"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stock").value(3));

        mockMvc.perform(put("/api/products/P-CRUD")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":\"P-CRUD\",\"name\":\"Updated\",\"stock\":9}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated"));

        mockMvc.perform(delete("/api/products/P-CRUD"))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/products/P-CRUD"))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsInvalidAndConflictingProducts() throws Exception {
        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":\"bad/id\",\"name\":\"Invalid\",\"stock\":1}"))
                .andExpect(status().isBadRequest());

        String existing = "{\"id\":\"P-100\",\"name\":\"Duplicate\",\"stock\":1}";
        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(existing))
                .andExpect(status().isConflict());
    }

    @Test
    void rejectsInvalidOrMissingProductUpdatesAndDeletes() throws Exception {
        mockMvc.perform(put("/api/products/P-100")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":\"P-200\",\"name\":\"Mismatch\",\"stock\":1}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(put("/api/products/P-MISSING")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":\"P-MISSING\",\"name\":\"Missing\",\"stock\":1}"))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/products/P-MISSING"))
                .andExpect(status().isNotFound());
    }

    @Test
    void invalidOrderIsRejected() throws Exception {
        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":\"\",\"quantity\":0}"))
                .andExpect(status().isBadRequest());
    }

    private Path findRepositoryRoot() throws IOException {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            if (Files.isRegularFile(current.resolve("client-tests/payload-8192.json"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IOException("Could not locate the repository root");
    }
}
