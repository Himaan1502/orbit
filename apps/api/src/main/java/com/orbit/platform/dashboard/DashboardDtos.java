package com.orbit.platform.dashboard;

import com.orbit.platform.execution.priority.PriorityReason;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Command Center aggregate (Blueprint §30, UX §16). Read-only projection. */
public final class DashboardDtos {

    private DashboardDtos() {
    }

    public record DashboardResponse(
            UserBlock user,
            ObjectiveHealth objectiveHealth,
            ObjectiveSummary primaryObjective,
            List<ObjectiveSummary> activeObjectives,
            NextBestAction nextBestAction,
            TodayBlock today,
            ExecutionHealth executionHealth) {
    }

    public record UserBlock(String displayName, String email) {
    }

    public record ObjectiveHealth(int active, int completed, BigDecimal averageProgress) {
    }

    public record ObjectiveSummary(
            UUID id, String title, int priority, String status, BigDecimal progress, LocalDate targetDate) {
    }

    public record NextBestAction(
            UUID taskId, String title, BigDecimal priorityScore, PriorityReason why,
            UUID objectiveId, String objectiveTitle, Integer estimatedMinutes, Instant dueAt) {
    }

    public record TodayBlock(
            LocalDate date, boolean planExists, int taskCount, int allocatedMinutes, int availableMinutes) {
    }

    public record ExecutionHealth(int openTasks, int dueWithin7Days, int completedLast7Days) {
    }
}
