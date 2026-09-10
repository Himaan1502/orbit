package com.orbit.platform.ai.provider;

/**
 * The vendor-neutral seam (Blueprint §28, Tech Arch §12, ADR-031 §1). One
 * implementation per vendor; {@code AiService} picks primary/fallback by
 * {@link #name()}. An implementation returns an {@link AiCompletion} or throws
 * {@link AiProviderException} — it never returns {@code null} and never blocks
 * beyond its configured timeout.
 */
public interface AiProvider {

    /** Stable id used in config ({@code orbit.ai.primary}) and metrics — e.g. {@code "gemini"}. */
    String name();

    /** The model this adapter calls — recorded on every assistant message. */
    String model();

    /** Whether a usable API key is configured. A provider with no key is skipped. */
    boolean isConfigured();

    /** Blocking call — returns the whole completion at once. Used by the nudge path and as a fallback. */
    AiCompletion complete(AiRequest request);

    /**
     * Streaming call (ADR-033) — pushes answer-text deltas to {@code sink} as
     * they arrive and returns the assembled {@link AiCompletion} (full text +
     * tool calls + usage) when the stream ends. Throws {@link AiProviderException}
     * exactly like {@link #complete}; if it throws before the first
     * {@link TokenSink#onToken} call, {@code AiService} may still fail over.
     */
    AiCompletion completeStream(AiRequest request, TokenSink sink);
}
