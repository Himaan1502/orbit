package com.orbit.platform.objectives.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.orbit.platform.common.model.LowerCaseEnumConverter;
import jakarta.persistence.Converter;

import java.util.Locale;

/** Objective lifecycle (ADR-016 §1). Unchanged from Milestone 1. */
public enum ObjectiveStatus {
    ACTIVE,
    COMPLETED,
    ARCHIVED;

    @JsonValue
    public String value() {
        return name().toLowerCase(Locale.ROOT);
    }

    @JsonCreator
    public static ObjectiveStatus fromValue(String value) {
        return valueOf(value.trim().toUpperCase(Locale.ROOT));
    }

    @Converter
    public static class JpaConverter extends LowerCaseEnumConverter<ObjectiveStatus> {
        public JpaConverter() {
            super(ObjectiveStatus.class);
        }
    }
}
