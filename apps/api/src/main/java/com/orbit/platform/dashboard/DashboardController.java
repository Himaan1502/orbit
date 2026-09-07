package com.orbit.platform.dashboard;

import com.orbit.platform.common.dto.ApiResponse;
import com.orbit.platform.dashboard.DashboardDtos.DashboardResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** {@code /api/v1/dashboard} — the Command Center aggregate (Blueprint §20, ADR-003). */
@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardController {

    private final DashboardService dashboard;

    public DashboardController(DashboardService dashboard) {
        this.dashboard = dashboard;
    }

    @GetMapping
    public ApiResponse<DashboardResponse> dashboard() {
        return ApiResponse.of(dashboard.build());
    }
}
