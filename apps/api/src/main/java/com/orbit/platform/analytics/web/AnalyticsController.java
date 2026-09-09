package com.orbit.platform.analytics.web;

import com.orbit.platform.analytics.service.AnalyticsService;
import com.orbit.platform.analytics.web.dto.AnalyticsDtos.CareerAnalytics;
import com.orbit.platform.analytics.web.dto.AnalyticsDtos.ExecutionAnalytics;
import com.orbit.platform.analytics.web.dto.AnalyticsDtos.SkillAnalytics;
import com.orbit.platform.common.dto.ApiResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;

/**
 * {@code /api/v1/analytics} (Blueprint §20, UX §24, ADR-027). Read-only,
 * ownership-scoped, derived at read time. Default range is the last 12 weeks.
 */
@RestController
@RequestMapping("/api/v1/analytics")
public class AnalyticsController {

    private final AnalyticsService analytics;
    private final Clock clock;

    public AnalyticsController(AnalyticsService analytics, Clock clock) {
        this.analytics = analytics;
        this.clock = clock;
    }

    @GetMapping("/execution")
    public ApiResponse<ExecutionAnalytics> execution(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        Window w = window(from, to);
        return ApiResponse.of(analytics.execution(w.from(), w.to()));
    }

    @GetMapping("/career")
    public ApiResponse<CareerAnalytics> career(
            @RequestParam(name = "careerTargetId", required = false) UUID careerTargetId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        Window w = window(from, to);
        return ApiResponse.of(analytics.career(careerTargetId, w.from(), w.to()));
    }

    @GetMapping("/skills")
    public ApiResponse<SkillAnalytics> skills() {
        return ApiResponse.of(analytics.skillsAnalytics());
    }

    private record Window(LocalDate from, LocalDate to) {
    }

    private Window window(LocalDate from, LocalDate to) {
        LocalDate end = to != null ? to : LocalDate.now(clock);
        LocalDate start = from != null ? from : end.minusWeeks(12);
        if (start.isAfter(end)) {
            start = end.minusWeeks(12);
        }
        return new Window(start, end);
    }
}
