package com.orbit.platform.common.model;

import jakarta.persistence.AttributeConverter;

import java.util.Locale;

/**
 * Persists an enum as its lower-cased name and reads it back, so Java code uses
 * idiomatic {@code UPPER_CASE} constants while the database (and API) use
 * lower-case tokens matched by {@code CHECK} constraints. Subclass with a
 * concrete enum type and mark the field {@code @Convert}.
 */
public abstract class LowerCaseEnumConverter<E extends Enum<E>> implements AttributeConverter<E, String> {

    private final Class<E> type;

    protected LowerCaseEnumConverter(Class<E> type) {
        this.type = type;
    }

    @Override
    public String convertToDatabaseColumn(E attribute) {
        return attribute == null ? null : attribute.name().toLowerCase(Locale.ROOT);
    }

    @Override
    public E convertToEntityAttribute(String dbData) {
        return dbData == null ? null : Enum.valueOf(type, dbData.trim().toUpperCase(Locale.ROOT));
    }
}
