package com.orbit.platform.execution;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Daily-planning defaults (ADR-015 §2), bound from {@code orbit.planning.*}. */
@ConfigurationProperties(prefix = "orbit.planning")
public record PlanningProperties(
        @DefaultValue("120") int defaultAvailableMinutes,
        @DefaultValue("30") int defaultTaskMinutes) {
}
