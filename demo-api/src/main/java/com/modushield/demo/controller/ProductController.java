package com.modushield.demo.controller;

import com.modushield.demo.model.Product;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class ProductController {

    @GetMapping("/api/products")
    public List<Product> products() {
        return List.of(
                new Product("P-100", "Demo product", 12),
                new Product("P-200", "Sample item", 7));
    }
}
