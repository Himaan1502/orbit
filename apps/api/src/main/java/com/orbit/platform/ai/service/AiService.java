package com.orbit.platform.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orbit.platform.ai.AiProperties;
import com.orbit.platform.ai.model.TokenUsage;
import com.orbit.platform.ai.provider.AiCompletion;
import com.orbit.platform.ai.provider.AiProvider;
import com.orbit.platform.ai.provider.AiProviderException;
import com.orbit.platform.ai.provider.AiRequest;
import com.orbit.platform.ai.provider.ChatItem;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Orchestration + routing + graceful degradation for OSCAR (Blueprint §26,
 * Tech Arch §12/§13, ADR-031 §2). The loop:
 *
 * <ol>
 *   <li>feature flag off → degraded {@link OscarResponse#unavailable};</li>
 *   <li>build the context bundle ({@link ContextBuilder});</li>
 *   <li>call the primary provider → one retry on a transient 5xx/timeout →
 *       fail over to the fallback → degraded response. <b>No exception
 *       propagates</b>;</li>
 *   <li>read-tool calls execute ({@link ToolRegistry}) for up to
 *       {@code maxToolRounds}; a mutation tool call is terminal — it becomes a
 *       {@link ProposedAction} and one final tool-free call produces the
 *       narrative. <b>OSCAR never executes a mutation.</b></li>
 *   <li>{@link ResponseValidator} parses the final text; unparsable → safe
 *       fallback, not persisted.</li>
 * </ol>
 */
@Service
public class AiService {

    private static final Logger log = LoggerFactory.getLogger(AiService.class);
    public static final String PROMPT_VERSION = "oscar-v1";

    private final AiProperties props;
    private final List<AiProvider> providers;
    private final ContextBuilder contextBuilder;
    private final ToolRegistry tools;
    private final MutationProposer mutationProposer;
    private final ResponseValidator validator;
    private final ObjectMapper json;
    private final MeterRegistry meters;
    private final String systemPrompt;

    public AiService(AiProperties props, List<AiProvider> providers, ContextBuilder contextBuilder,
                     ToolRegistry tools, MutationProposer mutationProposer, ResponseValidator validator,
                     ObjectMapper json, MeterRegistry meters,
                     @Value("classpath:oscar/system-prompt-v1.md") Resource promptResource) {
        this.props = props;
        this.providers = providers;
        this.contextBuilder = contextBuilder;
        this.tools = tools;
        this.mutationProposer = mutationProposer;
        this.validator = validator;
        this.json = json;
        this.meters = meters;
        this.systemPrompt = read(promptResource);
    }

    public boolean isEnabled() {
        return props.anyProviderConfigured();
    }

    public TurnResult respond(AiTurn turn) {
        if (!isEnabled()) {
            countRequest("none", "-", "degraded");
            return TurnResult.notPersisted(OscarResponse.unavailable("no-provider-key"), PROMPT_VERSION);
        }

        ContextBundle context;
        try {
            context = contextBuilder.forTurn(turn.userId(), turn.userMessage());
        } catch (RuntimeException e) {
            log.warn("event=oscar.context.failed user_id={} error={}", turn.userId(), e.toString());
            context = new ContextBundle(Map.of("status", "context-unavailable"), List.of());
        }

        List<ChatItem> history = new ArrayList<>(turn.priorTurns());
        history.add(new ChatItem.UserText(context.render(json)
                + "\n\n---\n\nUser question:\n" + turn.userMessage()));

        Timer.Sample sample = Timer.start(meters);
        try {
            TurnResult result = runLoop(history, context.layerNames());
            sample.stop(meters.timer("orbit_ai_request_duration_seconds",
                    "provider", result.provider() == null ? "none" : result.provider()));
            return result;
        } catch (RuntimeException unexpected) {
            // last-resort guard — OSCAR being down never breaks the request (PRD §10, UX §25.5)
            log.error("event=oscar.turn.unexpected user_id={}", turn.userId(), unexpected);
            sample.stop(meters.timer("orbit_ai_request_duration_seconds", "provider", "none"));
            countRequest("none", "-", "degraded");
            return TurnResult.notPersisted(OscarResponse.unavailable("internal-error"), PROMPT_VERSION);
        }
    }

    // ---------------------------------------------------------------------

    private TurnResult runLoop(List<ChatItem> history, List<String> citationLayers) {
        TokenUsage totalUsage = TokenUsage.ZERO;
        int round = 0;

        Attempt attempt = callProviders(new AiRequest(systemPrompt, history, tools.specs(),
                props.outputTokenBudget(), 0.2));
        if (attempt == null) {
            countRequest("none", "-", "degraded");
            return TurnResult.notPersisted(OscarResponse.unavailable("all-providers-failed"), PROMPT_VERSION);
        }
        totalUsage = totalUsage.plus(attempt.completion().usage());

        while (true) {
            AiCompletion c = attempt.completion();

            if (!c.hasToolCalls()) {
                return finalise(attempt.provider(), c.text(), null, totalUsage, citationLayers, "ok");
            }

            List<ChatItem.ToolCall> readCalls = new ArrayList<>();
            List<ChatItem.ToolCall> mutationCalls = new ArrayList<>();
            for (ChatItem.ToolCall call : normalise(c.toolCalls())) {
                if (!tools.isKnown(call.name())) {
                    // allow-list: an unknown tool is dropped, reported back to the model
                    readCalls.add(call);
                } else if (tools.isMutation(call.name())) {
                    mutationCalls.add(call);
                } else {
                    readCalls.add(call);
                }
            }

            if (!mutationCalls.isEmpty()) {
                return proposeAndSynthesise(attempt.provider(), history, readCalls, mutationCalls,
                        totalUsage, citationLayers);
            }

            // read-only round
            List<ChatItem.ToolResult> results = executeReads(readCalls);
            history.add(new ChatItem.ModelToolCalls(readCalls));
            history.add(new ChatItem.ToolResults(results));
            round++;

            boolean lastRound = round >= props.maxToolRounds();
            AiRequest next = new AiRequest(systemPrompt, history,
                    lastRound ? List.of() : tools.specs(), props.outputTokenBudget(), 0.2);
            attempt = callProviders(next);
            if (attempt == null) {
                countRequest("none", "-", "degraded");
                return TurnResult.notPersisted(OscarResponse.unavailable("all-providers-failed"), PROMPT_VERSION);
            }
            totalUsage = totalUsage.plus(attempt.completion().usage());

            if (lastRound && attempt.completion().hasToolCalls()) {
                // budget exhausted and the model still wants tools — answer with what we have
                return finalise(attempt.provider(),
                        attempt.completion().hasText() ? attempt.completion().text() : null,
                        null, totalUsage, citationLayers, "ok");
            }
        }
    }

    private TurnResult proposeAndSynthesise(AiProvider provider, List<ChatItem> history,
                                            List<ChatItem.ToolCall> readCalls,
                                            List<ChatItem.ToolCall> mutationCalls,
                                            TokenUsage usageSoFar, List<String> citationLayers) {
        List<ProposedAction> proposed = new ArrayList<>();
        List<String> rejected = new ArrayList<>();
        for (ChatItem.ToolCall call : mutationCalls) {
            MutationOutcome outcome = mutationProposer.propose(call);
            if (outcome instanceof MutationOutcome.Proposed p) {
                proposed.add(p.action());
            } else if (outcome instanceof MutationOutcome.Rejected r) {
                rejected.add(r.tool() + ": " + r.reason());
            }
        }

        List<ChatItem.ToolCall> allCalls = new ArrayList<>(readCalls);
        allCalls.addAll(mutationCalls);
        List<ChatItem.ToolResult> results = new ArrayList<>(executeReads(readCalls));
        for (ChatItem.ToolCall call : mutationCalls) {
            String note = rejected.stream().anyMatch(s -> s.startsWith(call.name() + ":"))
                    ? "REJECTED — " + rejected.stream().filter(s -> s.startsWith(call.name() + ":"))
                            .findFirst().orElse("invalid")
                    : "PROPOSED — surfaced to the user as a confirmable action. Do NOT call it again.";
            results.add(new ChatItem.ToolResult(call.id(), call.name(),
                    "{\"status\":\"" + note.replace("\"", "'") + "\"}"));
        }
        history.add(new ChatItem.ModelToolCalls(allCalls));
        history.add(new ChatItem.ToolResults(results));
        history.add(new ChatItem.UserText(
                "Produce your final JSON response now. The proposed action(s) are already shown to "
                        + "the user with a confirm button — describe the recommendation, do not claim it is done."));

        Attempt synth = callProviders(new AiRequest(systemPrompt, history, List.of(),
                props.outputTokenBudget(), 0.2));
        TokenUsage usage = usageSoFar;
        String text = null;
        String prov = provider.name();
        if (synth != null) {
            usage = usage.plus(synth.completion().usage());
            text = synth.completion().text();
            prov = synth.provider().name();
        }

        Optional<OscarResponse> parsed = validator.parse(text);
        OscarResponse base = parsed.orElseGet(() -> new OscarResponse(OscarResponse.Kind.recommendation,
                proposed.isEmpty()
                        ? "I couldn't put that together — try rephrasing."
                        : "Here's what I'd suggest — review the proposed action below and confirm if you want it.",
                null, null, null, null));
        OscarResponse response = base
                .withActions(proposed)
                .withMeta(new OscarResponse.Meta(prov, providerModel(prov), PROMPT_VERSION, "ok", false));
        if (!citationLayers.isEmpty() && response.citations() == null) {
            response = new OscarResponse(response.kind(), response.message(), response.sections(),
                    response.proposedActions(), citationLayers, response.meta());
        }
        countRequest(prov, providerModel(prov), "ok");
        // a recommendation with proposed actions is always persistable — the narrative + proposals are the value
        return new TurnResult(response, parsed.isPresent() || !proposed.isEmpty(),
                response.message(), prov, providerModel(prov), PROMPT_VERSION, usage);
    }

    private TurnResult finalise(AiProvider provider, String rawText, List<ProposedAction> actions,
                                TokenUsage usage, List<String> citationLayers, String outcome) {
        Optional<OscarResponse> parsed = validator.parse(rawText);
        if (parsed.isEmpty()) {
            countRequest(provider.name(), provider.model(), "degraded");
            return new TurnResult(OscarResponse.safeFallback(), false,
                    OscarResponse.safeFallback().message(), provider.name(), provider.model(),
                    PROMPT_VERSION, usage);
        }
        OscarResponse response = parsed.get();
        if (actions != null && !actions.isEmpty()) {
            response = response.withActions(actions);
        }
        if (!citationLayers.isEmpty() && response.citations() == null) {
            response = new OscarResponse(response.kind(), response.message(), response.sections(),
                    response.proposedActions(), citationLayers, response.meta());
        }
        response = response.withMeta(new OscarResponse.Meta(provider.name(), provider.model(),
                PROMPT_VERSION, outcome, false));
        countRequest(provider.name(), provider.model(), outcome);
        return new TurnResult(response, true, response.message(), provider.name(), provider.model(),
                PROMPT_VERSION, usage);
    }

    /**
     * Drop explicit {@code null} argument fields from each tool call before it
     * enters the history. Some providers (Groq) strictly re-validate the
     * assistant's prior {@code tool_calls} against the tool schema and reject a
     * {@code null} where the schema says {@code integer}/{@code string}.
     */
    private List<ChatItem.ToolCall> normalise(List<ChatItem.ToolCall> calls) {
        List<ChatItem.ToolCall> out = new ArrayList<>();
        for (ChatItem.ToolCall call : calls) {
            String args = call.argumentsJson();
            try {
                com.fasterxml.jackson.databind.JsonNode node =
                        json.readTree(args == null || args.isBlank() ? "{}" : args);
                if (node.isObject()) {
                    com.fasterxml.jackson.databind.node.ObjectNode obj =
                            (com.fasterxml.jackson.databind.node.ObjectNode) node;
                    List<String> nullKeys = new ArrayList<>();
                    obj.fieldNames().forEachRemaining(k -> {
                        if (obj.get(k).isNull()) {
                            nullKeys.add(k);
                        }
                    });
                    nullKeys.forEach(obj::remove);
                    args = obj.toString();
                }
            } catch (com.fasterxml.jackson.core.JacksonException ignored) {
                // leave the raw args as-is
            }
            out.add(new ChatItem.ToolCall(call.id(), call.name(), args));
        }
        return out;
    }

    private List<ChatItem.ToolResult> executeReads(List<ChatItem.ToolCall> readCalls) {
        List<ChatItem.ToolResult> out = new ArrayList<>();
        for (ChatItem.ToolCall call : readCalls) {
            String result = tools.isKnown(call.name())
                    ? tools.dispatchRead(call)
                    : "{\"error\":\"unknown tool '" + call.name() + "'\"}";
            meters.counter("orbit_ai_tool_calls_total", "tool", call.name(),
                    "kind", tools.isKnown(call.name()) ? "read" : "unknown").increment();
            out.add(new ChatItem.ToolResult(call.id(), call.name(), result));
        }
        return out;
    }

    // ---------------------------------------------------------------------
    // provider routing: primary → retry once (transient, non-429) → fallback
    // ---------------------------------------------------------------------

    private record Attempt(AiProvider provider, AiCompletion completion) {
    }

    Attempt callProviders(AiRequest request) {
        AiProvider primary = byName(props.primary());
        AiProvider fallback = byName(props.fallback());

        if (primary != null && primary.isConfigured()) {
            try {
                return new Attempt(primary, requireNonEmpty(primary, primary.complete(request)));
            } catch (AiProviderException e) {
                recordFailure(primary, e);
                if (e.isRetriable() && !e.isRateLimited()) {
                    try {
                        return new Attempt(primary, requireNonEmpty(primary, primary.complete(request)));
                    } catch (AiProviderException retry) {
                        recordFailure(primary, retry);
                    }
                }
            }
        }

        if (fallback != null && fallback != primary && fallback.isConfigured()) {
            try {
                Attempt a = new Attempt(fallback, requireNonEmpty(fallback, fallback.complete(request)));
                countRequest(fallback.name(), fallback.model(), "failed_over");
                return a;
            } catch (AiProviderException e) {
                recordFailure(fallback, e);
            }
        }
        return null;
    }

    private AiCompletion requireNonEmpty(AiProvider p, AiCompletion c) {
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

    private void recordFailure(AiProvider p, AiProviderException e) {
        String outcome = e.isRateLimited() ? "rate_limited" : "failed";
        countRequest(p.name(), p.model(), outcome);
        log.warn("event=oscar.provider.failure provider={} status={} retriable={} msg={}",
                p.name(), e.statusCode(), e.isRetriable(), e.getMessage());
    }

    private void countRequest(String provider, String model, String outcome) {
        meters.counter("orbit_ai_requests_total", "provider", provider, "model", model, "outcome", outcome)
                .increment();
    }

    private AiProvider byName(String name) {
        return providers.stream().filter(p -> p.name().equals(name)).findFirst().orElse(null);
    }

    private String providerModel(String name) {
        AiProvider p = byName(name);
        return p == null ? "-" : p.model();
    }

    private static String read(Resource r) {
        try {
            return new String(r.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("OSCAR system prompt resource missing: " + r, e);
        }
    }
}
