package com.orbit.platform.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * OSCAR / AI provider configuration (ADR-031). Validated at startup
 * ({@link AiConfig}). API keys come from the environment only
 * ({@code GEMINI_API_KEY} / {@code GROQ_API_KEY}) — never logged, returned, or
 * persisted (ADR-031 §4). OSCAR is <b>enabled only when at least one provider
 * key is present</b>; with none, {@code /oscar} shows the unavailable state and
 * nothing else in ORBIT changes.
 */
@ConfigurationProperties(prefix = "orbit.ai")
public record AiProperties(
        /** Provider tried first — must match a provider {@code name()}. */
        @DefaultValue("gemini") String primary,
        /** Provider tried when the primary fails / is unconfigured. */
        @DefaultValue("groq") String fallback,
        /** Per-user OSCAR turns allowed per rolling 24h (PolicyGuard, ADR-031 §3). */
        @DefaultValue("50") int dailyRequestCap,
        /** Per-turn prompt-token budget — context bundle + history are trimmed toward this. */
        @DefaultValue("24000") int promptTokenBudget,
        /** Per-turn model output-token cap. */
        @DefaultValue("2000") int outputTokenBudget,
        /** Max read-tool rounds before the model must produce a final answer (ADR-031 §2). */
        @DefaultValue("3") int maxToolRounds,
        /** Contextual-card generations allowed per user per rolling hour (ADR-034 §4). */
        @DefaultValue("12") int nudgeHourlyCap,
        /** HTTP connect timeout for a provider call (ms). */
        @DefaultValue("5000") int connectTimeoutMs,
        /** HTTP read timeout for a provider call (ms) — a reasoning flash model can take ~30-60s. */
        @DefaultValue("60000") int readTimeoutMs,
        @DefaultValue Gemini gemini,
        @DefaultValue Groq groq) {

    public record Gemini(
            @DefaultValue("") String apiKey,
            @DefaultValue("gemini-3.6-flash") String model,
            @DefaultValue("https://generativelanguage.googleapis.com") String baseUrl) {
    }

    public record Groq(
            @DefaultValue("") String apiKey,
            @DefaultValue("openai/gpt-oss-120b") String model,
            @DefaultValue("https://api.groq.com/openai/v1") String baseUrl) {
    }

    public boolean geminiConfigured() {
        return gemini != null && gemini.apiKey() != null && !gemini.apiKey().isBlank();
    }

    public boolean groqConfigured() {
        return groq != null && groq.apiKey() != null && !groq.apiKey().isBlank();
    }

    /** OSCAR is available iff at least one provider has a key (ADR-031 §4). */
    public boolean anyProviderConfigured() {
        return geminiConfigured() || groqConfigured();
    }

    public void validate() {
        if (dailyRequestCap < 1) {
            throw new IllegalStateException("orbit.ai.daily-request-cap must be >= 1 (got " + dailyRequestCap + ")");
        }
        if (promptTokenBudget < 1000) {
            throw new IllegalStateException("orbit.ai.prompt-token-budget must be >= 1000 (got " + promptTokenBudget + ")");
        }
        if (outputTokenBudget < 256) {
            throw new IllegalStateException("orbit.ai.output-token-budget must be >= 256 (got " + outputTokenBudget + ")");
        }
        if (maxToolRounds < 0 || maxToolRounds > 8) {
            throw new IllegalStateException("orbit.ai.max-tool-rounds must be within [0,8] (got " + maxToolRounds + ")");
        }
        if (nudgeHourlyCap < 0) {
            throw new IllegalStateException("orbit.ai.nudge-hourly-cap must be >= 0 (got " + nudgeHourlyCap + ")");
        }
        if (!primary.equals("gemini") && !primary.equals("groq")) {
            throw new IllegalStateException("orbit.ai.primary must be 'gemini' or 'groq' (got '" + primary + "')");
        }
        if (!fallback.equals("gemini") && !fallback.equals("groq")) {
            throw new IllegalStateException("orbit.ai.fallback must be 'gemini' or 'groq' (got '" + fallback + "')");
        }
    }
}
