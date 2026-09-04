package com.datingapp.chat.health.controller;

import com.datingapp.chat.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

/**
 * Health check controller providing REST probe for load balancers and Flutter clients.
 */
@RestController
@RequestMapping("/api/v1/health")
@Tag(name = "Health", description = "Service Health Check API")
public class HealthCheckController {

    @GetMapping
    @Operation(summary = "Get Chat Service health status", description = "Returns service availability status and timestamp.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> checkHealth() {
        Map<String, Object> healthData = Map.of(
                "status", "UP",
                "service", "chat-service",
                "version", "1.0.0",
                "timestamp", Instant.now().toString()
        );
        return ResponseEntity.ok(ApiResponse.success(healthData, "Chat service is running healthy"));
    }
}
