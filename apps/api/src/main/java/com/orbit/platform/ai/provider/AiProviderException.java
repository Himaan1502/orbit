package com.orbit.platform.ai.provider;

/**
 * A provider call failed. {@code retriable} distinguishes a transient failure
 * (timeout, HTTP 429, HTTP 5xx, malformed body) — which {@code AiService} retries
 * once then fails over (ADR-031 §2) — from a permanent one (401/403 bad key,
 * 400 bad request) which fails over immediately with no retry.
 */
public class AiProviderException extends RuntimeException {

    private final boolean retriable;
    private final int statusCode;

    public AiProviderException(String message, boolean retriable, int statusCode, Throwable cause) {
        super(message, cause);
        this.retriable = retriable;
        this.statusCode = statusCode;
    }

    public static AiProviderException transientFailure(String message, int statusCode, Throwable cause) {
        return new AiProviderException(message, true, statusCode, cause);
    }

    public static AiProviderException permanent(String message, int statusCode) {
        return new AiProviderException(message, false, statusCode, null);
    }

    public boolean isRetriable() {
        return retriable;
    }

    public boolean isRateLimited() {
        return statusCode == 429;
    }

    public int statusCode() {
        return statusCode;
    }
}
