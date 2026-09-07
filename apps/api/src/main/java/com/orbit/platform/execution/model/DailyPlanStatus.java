package com.orbit.platform.execution.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.orbit.platform.common.model.LowerCaseEnumConverter;
import jakarta.persistence.Converter;

import java.util.Locale;

/** Daily-plan lifecycle (ADR-015). */
public enum DailyPlanStatus {
    DRAFT,
    ACTIVE,
    COMPLETED;

    @JsonValue
    public String value() {
        return name().toLowerCase(Locale.ROOT);
    }

    @JsonCreator
    public static DailyPlanStatus fromValue(String value) {
        return valueOf(value.trim().toUpperCase(Locale.ROOT));
    }

    @Converter
    public static class JpaConverter extends LowerCaseEnumConverter<DailyPlanStatus> {
        public JpaConverter() {
            super(DailyPlanStatus.class);
        }
    }
}
