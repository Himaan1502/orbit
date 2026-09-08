package com.orbit.platform.projects.progress;

import java.util.List;

/**
 * Pure derived progress for a project (ADR-024 §6). No I/O — the caller supplies
 * the project's task tally (from the {@code execution} domain via a port) and its
 * skill contributions. Not stored; reproducible from the same inputs
 * (Blueprint §43).
 */
public final class ProjectProgressCalculator {

    private ProjectProgressCalculator() {
    }

    /** A project's task completion, from {@code execution}. */
    public record TaskTally(int total, int done) {
        public static TaskTally empty() {
            return new TaskTally(0, 0);
        }
    }

    /** One skill's claimed vs demonstrated contribution to the project. */
    public record SkillContribution(double expected, double demonstrated) {
    }

    public record ProjectProgress(
            /** done / total tasks, or {@code null} when the project has no tasks. */
            Double taskProgress,
            /** Σ demonstrated / Σ expected, or {@code null} when the project has no skills. */
            Double skillsDemonstrated,
            /** Mean of the components that have data, or 0. */
            double overall) {
    }

    public static ProjectProgress compute(TaskTally tasks, List<SkillContribution> skills) {
        Double taskProgress = tasks.total() == 0 ? null : round((double) tasks.done() / tasks.total());

        Double skillsDemonstrated = null;
        double expSum = skills.stream().mapToDouble(SkillContribution::expected).sum();
        if (expSum > 0) {
            double demSum = skills.stream().mapToDouble(SkillContribution::demonstrated).sum();
            skillsDemonstrated = round(Math.min(1.0, demSum / expSum));
        }

        double overall = 0;
        int n = 0;
        if (taskProgress != null) {
            overall += taskProgress;
            n++;
        }
        if (skillsDemonstrated != null) {
            overall += skillsDemonstrated;
            n++;
        }
        return new ProjectProgress(taskProgress, skillsDemonstrated, n == 0 ? 0 : round(overall / n));
    }

    private static double round(double v) {
        return Math.round(v * 1000) / 1000.0;
    }
}
