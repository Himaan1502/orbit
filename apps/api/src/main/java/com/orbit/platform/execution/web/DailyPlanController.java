package com.orbit.platform.execution.web;

import com.orbit.platform.common.dto.ApiResponse;
import com.orbit.platform.execution.service.DailyPlanService;
import com.orbit.platform.execution.web.dto.DailyPlanDtos.DailyPlanResponse;
import com.orbit.platform.execution.web.dto.DailyPlanDtos.GeneratePlanRequest;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

/** {@code /api/v1/daily-plans} (Blueprint §20, ADR-015). Scoped to the caller. */
@RestController
@RequestMapping("/api/v1/daily-plans")
public class DailyPlanController {

    private final DailyPlanService plans;

    public DailyPlanController(DailyPlanService plans) {
        this.plans = plans;
    }

    @GetMapping("/{date}")
    public ApiResponse<DailyPlanResponse> getForDate(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ApiResponse.of(plans.getForDate(date));
    }

    @PostMapping("/generate")
    public ApiResponse<DailyPlanResponse> generate(
            @Valid @RequestBody(required = false) GeneratePlanRequest request) {
        GeneratePlanRequest req = request != null ? request : new GeneratePlanRequest(null, null, null);
        return ApiResponse.of(plans.generate(req));
    }

    @PostMapping("/{id}/regenerate")
    public ApiResponse<DailyPlanResponse> regenerate(@PathVariable UUID id) {
        return ApiResponse.of(plans.regenerate(id));
    }
}
