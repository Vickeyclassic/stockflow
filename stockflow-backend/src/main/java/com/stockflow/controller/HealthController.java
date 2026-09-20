package com.stockflow.controller;

import com.stockflow.dto.HealthResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class HealthController {
    private final javax.sql.DataSource dataSource;
    public HealthController(javax.sql.DataSource dataSource) { this.dataSource = dataSource; }
    @GetMapping("/health/ready")
    public org.springframework.http.ResponseEntity<HealthResponse> ready() {
        try (var connection = dataSource.getConnection()) {
            if (connection.isValid(2)) return org.springframework.http.ResponseEntity.ok(
                new HealthResponse("UP", "stockflow-backend", "Ready"));
        } catch (java.sql.SQLException ignored) { }
        return org.springframework.http.ResponseEntity.status(503).body(
            new HealthResponse("DOWN", "stockflow-backend", "Not ready"));
    }
    @GetMapping("/health")
    public HealthResponse health() {
        return new HealthResponse("UP", "stockflow-backend", "StockFlow backend is running");
    }
}
