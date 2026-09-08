package com.orbit.platform.projects.progress;

import com.orbit.platform.projects.model.ProjectStatus;

/**
 * Pure — {@code project_skills.demonstrated_contribution} (ADR-026 §3). A skill
 * is only "demonstrated" once its project is <em>completed</em> <strong>and</strong>
 * has evidence linked to that skill; even then it is capped at what the project
 * was expected to contribute.
 */
public final class DemonstratedContributionCalculator {

    private DemonstratedContributionCalculator() {
    }

    public static double demonstrated(ProjectStatus status, double expected, double evidenceStrength) {
        if (status != ProjectStatus.COMPLETED) {
            return 0;
        }
        double v = Math.min(clamp(expected), clamp(evidenceStrength));
        return Math.round(v * 100) / 100.0;   // NUMERIC(3,2)
    }

    private static double clamp(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }
}
