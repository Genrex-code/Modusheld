package com.modushield.demo.controller;

import com.modushield.demo.model.Product;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final Map<String, Product> products = new ConcurrentHashMap<>();

    public ProductController() {
        products.put("P-100", new Product("P-100", "Demo product", 12));
        products.put("P-200", new Product("P-200", "Sample item", 7));
    }

    @GetMapping
    public List<Product> products() {
        return products.values().stream()
                .sorted(Comparator.comparing(Product::id))
                .toList();
    }

    @GetMapping("/{id}")
    public Product product(@PathVariable String id) {
        return requireProduct(id);
    }

    @PostMapping
    public ResponseEntity<Product> create(@RequestBody Product product) {
        validate(product);
        if (products.putIfAbsent(product.id(), product) != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Product ID already exists");
        }
        return ResponseEntity.created(URI.create("/api/products/" + product.id())).body(product);
    }

    @PutMapping("/{id}")
    public Product update(@PathVariable String id, @RequestBody Product product) {
        if (product == null || !id.equals(product.id())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Path ID must match product ID");
        }
        validate(product);
        if (products.replace(id, product) == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found");
        }
        return product;
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        if (products.remove(id) == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found");
        }
        return ResponseEntity.noContent().build();
    }

    private Product requireProduct(String id) {
        Product product = products.get(id);
        if (product == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found");
        }
        return product;
    }

    private void validate(Product product) {
        if (product == null
                || product.id() == null
                || !product.id().matches("[A-Za-z0-9._-]{1,64}")
                || product.name() == null
                || product.name().isBlank()
                || product.name().length() > 200
                || product.stock() < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid product");
        }
    }
}
