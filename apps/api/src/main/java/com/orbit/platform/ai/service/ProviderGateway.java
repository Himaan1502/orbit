package com.orbit.platform.ai.service;

import com.orbit.platform.ai.AiProperties;
import com.orbit.platform.ai.provider.AiCompletion;
import com.orbit.platform.ai.provider.AiProvider;
import com.orbit.platform.ai.provider.AiProviderException;
import com.orbit.platform.ai.provider.AiRequest;
import com.orbit.platform.ai.provider.TokenSink;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Provider routing shared by the blocking ({@link AiService}) and streaming
 * ({@link OscarStreamOrchestrator}) turn loops (ADR-031 §2, ADR-033 §5):
 * primary → one retry on a transient 5xx/timeout (not on 429) → fail over to the
 * fallback → {@code null} (degraded). Also owns the {@code orbit_ai_*} metrics.
 *
 * <p>Streaming fail-over is <b>gated on the first token</b>: once
 * {@code TokenSink} has emitted, a provider failure is <b>not</b> retried or
 * failed over — the caller ends the stream with what arrived (ADR-033 §5).
 */
@Component
class ProviderGateway {

    private static final Logger log = LoggerFactory.getLogger(ProviderGateway.class);

    record Attempt(AiProvider provider, AiCompletion completion) {
    }

    private final AiProperties props;
    private final List<AiProvider> providers;
    private final MeterRegistry meters;

    ProviderGateway(AiProperties props, List<AiProvider> providers, MeterRegistry meters) {
        this.props = props;
        this.providers = providers;
        this.meters = meters;
    }

    boolean anyProviderConfigured() {
        return props.anyProviderConfigured();
    }

    /** Blocking call. {@code null} → both providers unavailable (degraded). */
    Attempt callBlocking(AiRequest request) {
        return route(request, null, new AtomicBoolean(false));
    }

    /**
     * Streaming call — pushes tokens to {@code sink} as they arrive.
     * {@code firstToken} is set by the caller's guarded sink on the first token;
     * once set, a failure here propagates (no retry/fail-over) so the caller can
     * end the stream cleanly.
     */
    Attempt callStreaming(AiRequest request, TokenSink sink, AtomicBoolean firstToken) {
        return route(request, sink, firstToken);
    }

    private Attempt route(AiRequest request, TokenSink sink, AtomicBoolean firstToken) {
        AiProvider primary = byName(props.primary());
        AiProvider fallback = byName(props.fallback());

        if (primary != null && primary.isConfigured()) {
            try {
                return new Attempt(primary, complete(primary, request, sink));
            } catch (AiProviderException e) {
                if (firstToken.get()) {
                    throw e;   // ADR-033 §5 — no switch mid-answer
                }
                recordFailure(primary, e);
                if (e.isRetriable() && !e.isRateLimited()) {
                    try {
                        return new Attempt(primary, complete(primary, request, sink));
                    } catch (AiProviderException retry) {
                        if (firstToken.get()) {
                            throw retry;
                        }
                        recordFailure(primary, retry);
                    }
                }
            }
        }

        if (fallback != null && fallback != primary && fallback.isConfigured()) {
            try {
                Attempt a = new Attempt(fallback, complete(fallback, request, sink));
                countRequest(fallback.name(), fallback.model(), "failed_over");
                return a;
            } catch (AiProviderException e) {
                if (firstToken.get()) {
                    throw e;
                }
                recordFailure(fallback, e);
            }
        }
        return null;
    }

    private AiCompletion complete(AiProvider p, AiRequest request, TokenSink sink) {
        AiCompletion c = sink == null ? p.complete(request) : p.completeStream(request, sink);
        if (!c.hasText() && !c.hasToolCalls()) {
            throw AiProviderException.transientFailure(
                    p.name() + " returned an empty completion (finishReason=" + c.finishReason() + ")", 0, null);
        }
        meters.counter("orbit_ai_tokens_total", "provider", p.name(), "kind", "prompt")
                .increment(c.usage().promptTokens());
        meters.counter("orbit_ai_tokens_total", "provider", p.name(), "kind", "completion")
                .increment(c.usage().completionTokens());
        return c;
    }

    void recordFailure(AiProvider p, AiProviderException e) {
        countRequest(p.name(), p.model(), e.isRateLimited() ? "rate_limited" : "failed");
        log.warn("event=oscar.provider.failure provider={} status={} retriable={} msg={}",
                p.name(), e.statusCode(), e.isRetriable(), e.getMessage());
    }

    void countRequest(String provider, String model, String outcome) {
        meters.counter("orbit_ai_requests_total", "provider", provider, "model", model, "outcome", outcome)
                .increment();
    }

    void countTool(String tool, String kind) {
        meters.counter("orbit_ai_tool_calls_total", "tool", tool, "kind", kind).increment();
    }

    AiProvider byName(String name) {
        return providers.stream().filter(p -> p.name().equals(name)).findFirst().orElse(null);
    }

    String modelOf(String name) {
        AiProvider p = byName(name);
        return p == null ? "-" : p.model();
    }
}
