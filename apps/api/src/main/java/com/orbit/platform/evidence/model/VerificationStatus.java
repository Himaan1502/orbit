package com.orbit.platform.evidence.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.orbit.platform.common.model.LowerCaseEnumConverter;
import jakarta.persistence.Converter;

import java.util.Locale;

/**
 * How trustworthy an evidence item is (Blueprint §13, ADR-025 §3). Every M5 row
 * is {@code SELF_ATTESTED} — the other states arrive with the external
 * verification integrations (M6+).
 */
public enum VerificationStatus {
    SELF_ATTESTED,
    PENDING,
    VERIFIED,
    DISPUTED;

    @JsonValue
    public String value() {
        return name().toLowerCase(Locale.ROOT);
    }

    @JsonCreator
    public static VerificationStatus fromValue(String value) {
        return valueOf(value.trim().toUpperCase(Locale.ROOT));
    }

    @Converter
    public static class JpaConverter extends LowerCaseEnumConverter<VerificationStatus> {
        public JpaConverter() {
            super(VerificationStatus.class);
        }
    }
}
