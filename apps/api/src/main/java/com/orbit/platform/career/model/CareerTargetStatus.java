package com.orbit.platform.career.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.orbit.platform.common.model.LowerCaseEnumConverter;
import jakarta.persistence.Converter;

import java.util.Locale;

/** Career-target lifecycle (Blueprint §8). */
public enum CareerTargetStatus {
    ACTIVE,
    ARCHIVED;

    @JsonValue
    public String value() {
        return name().toLowerCase(Locale.ROOT);
    }

    @JsonCreator
    public static CareerTargetStatus fromValue(String value) {
        return valueOf(value.trim().toUpperCase(Locale.ROOT));
    }

    @Converter
    public static class JpaConverter extends LowerCaseEnumConverter<CareerTargetStatus> {
        public JpaConverter() {
            super(CareerTargetStatus.class);
        }
    }
}
