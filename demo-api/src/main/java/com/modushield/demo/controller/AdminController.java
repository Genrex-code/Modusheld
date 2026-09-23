package com.modushield.demo.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class AdminController {

    /**
     * Deliberately exists and returns 200 inside the protected network. The
     * gateway must block the same route with 403 before this method is called.
     */
    @GetMapping("/api/admin/status")
    public Map<String, String> status() {
        return Map.of("status", "INTERNAL_OK");
    }
}
