package com.modushield.demo.controller;

import com.modushield.demo.model.OrderRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.UUID;

@RestController
public class OrderController {

    @PostMapping("/api/orders")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> create(@RequestBody OrderRequest request) {
        if (request == null
                || request.productId() == null
                || request.productId().isBlank()
                || request.quantity() < 1
                || request.quantity() > 100) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid productId or quantity");
        }

        return Map.of(
                "orderId", "ORD-" + UUID.randomUUID().toString().substring(0, 8),
                "productId", request.productId(),
                "quantity", request.quantity(),
                "status", "SIMULATED");
    }
}
