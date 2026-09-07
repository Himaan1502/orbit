package com.orbit.platform.execution.web.dto;

import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Request / response shapes for {@code /api/v1/daily-plans}. */
public final class DailyPlanDtos {

    private DailyPlanDtos() {
    }

    public record GeneratePlanRequest(
            LocalDate date,
            @Positive(message = "availableMinutes must be positive.") Integer availableMinutes,
            Boolean force) {
    }

    public record PlanEntryResponse(
            UUID taskId,
            int position,
            int allocatedMinutes,
            String title,
            String status,
            Integer estimatedMinutes,
            BigDecimal priorityScore,
            Instant dueAt,
            UUID objectiveId,
            String objectiveTitle) {
    }

    public record DailyPlanResponse(
            UUID id,
            LocalDate planDate,
            int availableMinutes,
            int allocatedMinutes,
            String status,
            String generatedBy,
            List<PlanEntryResponse> tasks,
            Instant createdAt,
            Instant updatedAt) {
    }
}
