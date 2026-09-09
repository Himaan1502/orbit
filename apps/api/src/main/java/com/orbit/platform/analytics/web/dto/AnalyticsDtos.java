package com.orbit.platform.analytics.web.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Response shapes for {@code /api/v1/analytics} (Blueprint §15/§20, UX §24,
 * ADR-027). Every series is derived at read time; a metric with no underlying
 * data carries {@code status: "no-data-yet"} and empty points — never a
 * zero-filled line (Blueprint M8 "no fabricated metrics").
 */
public final class AnalyticsDtos {

    private AnalyticsDtos() {
    }

    public record Range(LocalDate from, LocalDate to, int weeks) {
    }

    // ----- Execution -----

    public record ExecutionWeek(LocalDate weekStart, int planned, int completed, int focusedMinutes) {
    }

    public record StatusCounts(long todo, long inProgress, long blocked, long done, long skipped) {
    }

    public record BlockerRow(UUID taskId, String title, Instant blockedSince, long daysBlocked) {
    }

    public record ObjectiveProgressPoint(Instant at, double progress, String status) {
    }

    public record ObjectiveProgressSeries(UUID objectiveId, String title, List<ObjectiveProgressPoint> points) {
    }

    public record ExecutionAnalytics(
            Range range,
            String status,
            List<ExecutionWeek> weekly,
            Double completionRate,
            double throughputPerWeek,
            StatusCounts statusCounts,
            List<BlockerRow> blockers,
            List<ObjectiveProgressSeries> objectiveProgress) {
    }

    // ----- Career -----

    public record ReadinessPoint(Instant at, double overall, double skillCapability, int assessedSkillCount) {
    }

    public record CountWeek(LocalDate weekStart, int count) {
    }

    public record ProgressionPoint(Instant at, int skillsAtThreshold, int targetSkillCount) {
    }

    public record CareerAnalytics(
            UUID careerTargetId,
            String targetName,
            String status,
            List<ReadinessPoint> readinessTrend,
            List<CountWeek> evidenceGrowth,
            List<ProgressionPoint> targetProgression) {
    }

    // ----- Skills -----

    public record SkillSeriesPoint(Instant at, double overall) {
    }

    public record SkillProgression(UUID skillId, String name, List<SkillSeriesPoint> points) {
    }

    public record NeglectedSkill(UUID skillId, String name, Instant lastAssessedAt, Long daysSince) {
    }

    public record LeverageSkill(UUID skillId, String name, double leverage, int unlocks) {
    }

    public record Bottleneck(UUID skillId, String name, List<String> blockedDependents) {
    }

    public record SkillAnalytics(
            String status,
            List<SkillProgression> progression,
            List<NeglectedSkill> neglected,
            List<LeverageSkill> highLeverage,
            List<Bottleneck> bottlenecks) {
    }
}
