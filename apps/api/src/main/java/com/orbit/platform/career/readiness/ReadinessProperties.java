package com.orbit.platform.career.readiness;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * The Career Readiness component blend (ADR-018 §1, amended by ADR-026 §2).
 * Renormalised among the components that have data; every component has data
 * from M5 on, so a {@code 0} is an honest gap, not an absent signal. Weights
 * ratified by the product owner 2026-09-08.
 */
@ConfigurationProperties(prefix = "orbit.career.readiness")
public record ReadinessProperties(
        @DefaultValue("0.45") double skillCapabilityWeight,
        @DefaultValue("0.20") double interviewReadinessWeight,
        @DefaultValue("0.20") double evidenceCoverageWeight,
        @DefaultValue("0.15") double projectCoverageWeight,
        /** A target skill at/above this capability but with ~no evidence is an "evidence gap". */
        @DefaultValue("0.50") double evidenceGapCapabilityBar) {

    public void validate() {
        double sum = skillCapabilityWeight + interviewReadinessWeight
                + evidenceCoverageWeight + projectCoverageWeight;
        if (Math.abs(sum - 1.0) > 0.001) {
            throw new IllegalStateException(
                    "orbit.career.readiness component weights must sum to 1.0 but sum to " + sum);
        }
        for (double w : new double[] {skillCapabilityWeight, interviewReadinessWeight,
                evidenceCoverageWeight, projectCoverageWeight, evidenceGapCapabilityBar}) {
            if (w < 0 || w > 1) {
                throw new IllegalStateException("orbit.career.readiness values must be within [0,1]");
            }
        }
    }
}
