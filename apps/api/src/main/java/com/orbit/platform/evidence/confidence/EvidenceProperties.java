package com.orbit.platform.evidence.confidence;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Thresholds that turn logged evidence into a skill-assessment {@code confidence}
 * level (ADR-026 §1). Startup-validated ({@link EvidenceConfig}).
 */
@ConfigurationProperties(prefix = "orbit.evidence")
public record EvidenceProperties(
        /** Summed contribution weight at/above which confidence is at least partially-verified. */
        @DefaultValue("0.40") double partialThreshold,
        /** …and, with enough distinct evidence types, verified. */
        @DefaultValue("1.00") double verifiedThreshold,
        /** Distinct evidence types required alongside {@code verifiedThreshold} for "verified". */
        @DefaultValue("2") int verifiedMinTypes) {

    public void validate() {
        if (partialThreshold < 0 || partialThreshold > 1) {
            throw new IllegalStateException("orbit.evidence.partial-threshold must be within [0,1]");
        }
        if (verifiedThreshold < partialThreshold) {
            throw new IllegalStateException(
                    "orbit.evidence.verified-threshold must be >= partial-threshold");
        }
        if (verifiedMinTypes < 1) {
            throw new IllegalStateException("orbit.evidence.verified-min-types must be >= 1");
        }
    }
}
