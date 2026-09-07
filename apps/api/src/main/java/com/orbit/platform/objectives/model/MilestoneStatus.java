package com.orbit.platform.objectives.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.orbit.platform.common.model.LowerCaseEnumConverter;
import jakarta.persistence.Converter;

import java.util.Locale;

/** Milestone lifecycle (ADR-016 §1). */
public enum MilestoneStatus {
    PENDING,
    IN_PROGRESS,
    COMPLETED;

    @JsonValue
    public String value() {
        return name().toLowerCase(Locale.ROOT);
    }

    @JsonCreator
    public static MilestoneStatus fromValue(String value) {
        return valueOf(value.trim().toUpperCase(Locale.ROOT));
    }

    @Converter
    public static class JpaConverter extends LowerCaseEnumConverter<MilestoneStatus> {
        public JpaConverter() {
            super(MilestoneStatus.class);
        }
    }
}
