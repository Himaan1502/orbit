package com.orbit.platform.ai.service;

/**
 * The events {@link OscarStreamOrchestrator} emits during a streamed turn
 * (ADR-033 §4). The SSE controller adapts these to {@code event:} frames; the
 * terminal {@code done} frame is written by {@link ConversationService} after it
 * persists.
 */
public interface StreamSink {

    /** Pre-answer progress — a tool round, "thinking". */
    void status(String text);

    /** A chunk of the answer prose — the consumer appends. */
    void token(String delta);

    /** A confirmable action (0..n), emitted before/around the prose. OSCAR never executes it. */
    void proposedAction(ProposedAction action);

    /** The final structured extras (kind / sections / citations / provider / model), once. */
    void meta(OscarResponse response);

    /** Degraded. {@code recoverable} → the client may retry; the turn is not persisted. */
    void error(boolean recoverable, String note);
}
