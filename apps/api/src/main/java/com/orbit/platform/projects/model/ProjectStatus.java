package com.orbit.platform.projects.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.orbit.platform.common.model.LowerCaseEnumConverter;
import jakarta.persistence.Converter;

import java.util.Locale;

/**
 * Project lifecycle (Blueprint §14). Unlike an objective's status, {@code COMPLETED}
 * is user-settable here — a project is "done" when the person says so, and that
 * self-reported completion is exactly what the PRD keeps distinct from evidence
 * (ADR-024 §1).
 */
public enum ProjectStatus {
    PLANNED,
    ACTIVE,
    COMPLETED,
    ARCHIVED;

    @JsonValue
    public String value() {
        return name().toLowerCase(Locale.ROOT);
    }

    @JsonCreator
    public static ProjectStatus fromValue(String value) {
        return valueOf(value.trim().toUpperCase(Locale.ROOT));
    }

    @Converter
    public static class JpaConverter extends LowerCaseEnumConverter<ProjectStatus> {
        public JpaConverter() {
            super(ProjectStatus.class);
        }
    }
}
