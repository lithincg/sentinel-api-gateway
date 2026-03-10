package com.sentinel.apigateway;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import java.util.LinkedHashMap;
import java.util.Map;


@RestController
public class HealthController {

    @GetMapping("/health")
    public String checkHealth() {
        return "Sentinel is active. System Status: GREEN.";
    }

    @GetMapping("/")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> index() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("name", "Sentinel API Gateway");
        response.put("description", "A production-style API gateway built with Spring Boot 3.4, featuring Redis-backed sliding window rate limiting, JWT authentication, and API key authentication with Redis caching.");
        response.put("github", "https://github.com/lithincg/sentinel-api-gateway");

        Map<String, String> endpoints = new LinkedHashMap<>();
        endpoints.put("POST /api/auth/register", "Register a new user");
        endpoints.put("POST /api/auth/login", "Authenticate and receive a JWT");
        endpoints.put("POST /api/keys", "Generate an API key (JWT required)");
        endpoints.put("GET /api/dummy", "Test endpoint (auth required)");
        endpoints.put("GET /health", "Health check");
        endpoints.put("GET /actuator/health", "Detailed health status");
        response.put("endpoints", endpoints);

        return ResponseEntity.ok(response);
    }
}