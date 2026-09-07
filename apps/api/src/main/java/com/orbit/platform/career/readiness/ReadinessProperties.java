package com.orbit.platform.career.readiness;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * The blend of readiness components that have data in Milestone 3 (ADR-018 §1).
 * Renormalised to sum to 1 among the available components; evidence / project
 * coverage take a share once their engines land (M5).
 */
@ConfigurationProperties(prefix = "orbit.career.readiness")
public record ReadinessProperties(
        @DefaultValue("0.70") double skillCapabilityWeight,
        @DefaultValue("0.30") double interviewReadinessWeight) {
}
