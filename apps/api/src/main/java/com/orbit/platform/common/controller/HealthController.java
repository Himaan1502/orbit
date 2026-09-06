package com.orbit.platform.common.controller;

import com.orbit.platform.common.dto.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class HealthController {

    @GetMapping("/health")
    public ApiResponse<Map<String, Object>> health() {
        return ApiResponse.of(Map.of(
                "status", "UP",
                "service", "orbit-api",
                "timestamp", Instant.now().toString()
        ));
    }
}
