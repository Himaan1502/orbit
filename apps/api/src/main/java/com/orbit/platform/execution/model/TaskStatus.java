package com.orbit.platform.execution.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.orbit.platform.common.model.LowerCaseEnumConverter;
import jakarta.persistence.Converter;

import java.util.Locale;

/** Task lifecycle (ADR-016 §1; maps UX §17). {@code DONE} / {@code SKIPPED} are terminal. */
public enum TaskStatus {
    TODO,
    IN_PROGRESS,
    BLOCKED,
    DONE,
    SKIPPED;

    public boolean isTerminal() {
        return this == DONE || this == SKIPPED;
    }

    public boolean isOpen() {
        return this == TODO || this == IN_PROGRESS;
    }

    @JsonValue
    public String value() {
        return name().toLowerCase(Locale.ROOT);
    }

    @JsonCreator
    public static TaskStatus fromValue(String value) {
        return valueOf(value.trim().toUpperCase(Locale.ROOT));
    }

    @Converter
    public static class JpaConverter extends LowerCaseEnumConverter<TaskStatus> {
        public JpaConverter() {
            super(TaskStatus.class);
        }
    }
}
