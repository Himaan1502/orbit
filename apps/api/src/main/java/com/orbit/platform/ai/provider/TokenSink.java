package com.orbit.platform.ai.provider;

/**
 * Receives answer text as it streams from a provider (ADR-033 §6). One call per
 * chunk — the consumer appends. Deltas are answer prose only; tool-call and
 * usage state are assembled by the adapter and returned in the final
 * {@link AiCompletion}.
 */
@FunctionalInterface
public interface TokenSink {

    void onToken(String delta);

    /** A sink that discards everything — used when a caller wants the assembled result only. */
    TokenSink DISCARD = delta -> { };
}
