package com.orbit.platform.evidence.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.orbit.platform.common.model.LowerCaseEnumConverter;
import jakarta.persistence.Converter;

import java.util.Locale;

/**
 * The kind of artifact an evidence item represents (UX §23, ADR-025 §2). Fixed
 * set so the library can filter and readiness can weight by type later;
 * {@code OTHER} is the escape hatch.
 */
public enum EvidenceType {
    PROJECT,
    REPOSITORY,
    DEPLOYMENT,
    BENCHMARK,
    DOCUMENT,
    CERTIFICATION,
    ASSESSMENT,
    TALK,
    OTHER;

    @JsonValue
    public String value() {
        return name().toLowerCase(Locale.ROOT);
    }

    @JsonCreator
    public static EvidenceType fromValue(String value) {
        return valueOf(value.trim().toUpperCase(Locale.ROOT));
    }

    @Converter
    public static class JpaConverter extends LowerCaseEnumConverter<EvidenceType> {
        public JpaConverter() {
            super(EvidenceType.class);
        }
    }
}
