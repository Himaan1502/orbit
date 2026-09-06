package com.orbit.auth.identity;

/**
 * Thrown when a registration request fails a semantic check (weak password,
 * email already taken, …). Carries the form field the error belongs to so the
 * controller can attach it to the right input.
 */
public class RegistrationException extends RuntimeException {

    private final String field;

    public RegistrationException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
