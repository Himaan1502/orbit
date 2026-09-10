package com.orbit.platform.ai.service;

import com.orbit.platform.ai.model.TokenUsage;
import com.orbit.platform.ai.provider.AiCompletion;
import com.orbit.platform.ai.provider.AiRequest;
import com.orbit.platform.ai.provider.ChatItem;
import com.orbit.platform.ai.service.ProviderGateway.Attempt;
import com.orbit.platform.ai.service.ToolRounds.Classified;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Blocking OSCAR turn (ADR-031 §2, ADR-032) — used by the non-streaming
 * {@code /turns} endpoint, the tests, and as the client's fallback. Streaming
 * lives in {@link OscarStreamOrchestrator}; both share {@link ProviderGateway},
 * {@link ToolRounds} and {@link OscarPrompt}.
 *
 * <p>primary → retry once on a transient 5xx → fail over → degraded response,
 * <b>no exception propagates</b>. Read tools run up to {@code maxToolRounds}; a
 * mutation call becomes a {@link ProposedAction} — OSCAR never writes.
 */
@Service
public class AiService {

    public static final String PROMPT_VERSION = OscarPrompt.VERSION;
    private static final Logger log = LoggerFactory.getLogger(AiService.class);

    private final com.orbit.platform.ai.AiProperties props;
    private final ProviderGateway gateway;
    private final ToolRounds toolRounds;
    private final ToolRegistry toolRegistry;
    private final OscarPrompt prompt;
    private final MutationProposer mutationProposer;
    private final ResponseValidator validator;
    private final MeterRegistry meters;

    public AiService(com.orbit.platform.ai.AiProperties props, ProviderGateway gateway,
                     ToolRounds toolRounds, ToolRegistry toolRegistry, OscarPrompt prompt,
                     MutationProposer mutationProposer, ResponseValidator validator, MeterRegistry meters) {
        this.props = props;
        this.gateway = gateway;
        this.toolRounds = toolRounds;
        this.toolRegistry = toolRegistry;
        this.prompt = prompt;
        this.mutationProposer = mutationProposer;
        this.validator = validator;
        this.meters = meters;
    }

    public boolean isEnabled() {
        return gateway.anyProviderConfigured();
    }

    public TurnResult respond(AiTurn turn) {
        if (!isEnabled()) {
            gateway.countRequest("none", "-", "degraded");
            return TurnResult.notPersisted(OscarResponse.unavailable("no-provider-key"), PROMPT_VERSION);
        }
        OscarPrompt.Opening opening = prompt.open(turn);
        Timer.Sample sample = Timer.start(meters);
        try {
            TurnResult result = runLoop(opening.history(), opening.citationLayers());
            sample.stop(meters.timer("orbit_ai_request_duration_seconds",
                    "provider", result.provider() == null ? "none" : result.provider()));
            return result;
        } catch (RuntimeException unexpected) {
            log.error("event=oscar.turn.unexpected user_id={}", turn.userId(), unexpected);
            sample.stop(meters.timer("orbit_ai_request_duration_seconds", "provider", "none"));
            gateway.countRequest("none", "-", "degraded");
            return TurnResult.notPersisted(OscarResponse.unavailable("internal-error"), PROMPT_VERSION);
        }
    }

    private TurnResult runLoop(List<ChatItem> history, List<String> citations) {
        TokenUsage usage = TokenUsage.ZERO;
        int round = 0;

        Attempt attempt = gateway.callBlocking(request(history, tools()));
        if (attempt == null) {
            return degraded();
        }
        usage = usage.plus(attempt.completion().usage());

        while (true) {
            AiCompletion c = attempt.completion();
            if (!c.hasToolCalls()) {
                return finalise(attempt, c.text(), null, usage, citations);
            }

            Classified split = toolRounds.classify(c.toolCalls());
            if (!split.mutation().isEmpty()) {
                return proposeAndSynthesise(attempt, history, split, usage, citations);
            }

            history.add(ToolRounds.callsAsText(split.read()));
            history.add(ToolRounds.resultsAsText(toolRounds.executeReads(split.read())));
            round++;
            boolean last = round >= props.maxToolRounds();

            attempt = gateway.callBlocking(request(history, last ? List.of() : tools()));
            if (attempt == null) {
                return degraded();
            }
            usage = usage.plus(attempt.completion().usage());
            if (last && attempt.completion().hasToolCalls()) {
                return finalise(attempt, attempt.completion().text(), null, usage, citations);
            }
        }
    }

    private TurnResult proposeAndSynthesise(Attempt round, List<ChatItem> history, Classified split,
                                            TokenUsage usage, List<String> citations) {
        List<ProposedAction> proposed = new ArrayList<>();
        List<String> rejected = new ArrayList<>();
        for (ChatItem.ToolCall call : split.mutation()) {
            MutationOutcome outcome = mutationProposer.propose(call);
            if (outcome instanceof MutationOutcome.Proposed p) {
                proposed.add(p.action());
            } else if (outcome instanceof MutationOutcome.Rejected r) {
                rejected.add(r.tool() + ": " + r.reason());
            }
        }

        List<ChatItem.ToolCall> allCalls = new ArrayList<>(split.read());
        allCalls.addAll(split.mutation());
        List<ChatItem.ToolResult> results = new ArrayList<>(toolRounds.executeReads(split.read()));
        for (ChatItem.ToolCall call : split.mutation()) {
            results.add(new ChatItem.ToolResult(call.id(), call.name(),
                    "{\"status\":\"" + ToolRounds.mutationNote(call.name(), rejected).replace("\"", "'") + "\"}"));
        }
        history.add(ToolRounds.callsAsText(allCalls));
        history.add(ToolRounds.resultsAsText(results));
        history.add(new ChatItem.UserText(
                "Produce your final answer now. The proposed action(s) are already shown to the user "
                        + "with a confirm button — describe the recommendation, do not claim it is done."));

        Attempt synth = gateway.callBlocking(request(history, List.of()));
        String prov = synth != null ? synth.provider().name() : round.provider().name();
        TokenUsage total = synth != null ? usage.plus(synth.completion().usage()) : usage;
        String text = synth != null ? synth.completion().text() : null;

        Optional<OscarResponse> parsed = validator.parse(text);
        OscarResponse base = parsed.orElseGet(() -> new OscarResponse(OscarResponse.Kind.recommendation,
                proposed.isEmpty() ? "I couldn't put that together — try rephrasing."
                        : "Here's what I'd suggest — review the proposed action below and confirm if you want it.",
                null, null, null, null));
        OscarResponse response = withCitations(base.withActions(proposed), citations)
                .withMeta(new OscarResponse.Meta(prov, gateway.modelOf(prov), PROMPT_VERSION, "ok", false));
        gateway.countRequest(prov, gateway.modelOf(prov), "ok");
        return new TurnResult(response, parsed.isPresent() || !proposed.isEmpty(),
                response.message(), prov, gateway.modelOf(prov), PROMPT_VERSION, total);
    }

    private TurnResult finalise(Attempt attempt, String rawText, List<ProposedAction> actions,
                                TokenUsage usage, List<String> citations) {
        String prov = attempt.provider().name();
        String model = attempt.provider().model();
        Optional<OscarResponse> parsed = validator.parse(rawText);
        if (parsed.isEmpty()) {
            gateway.countRequest(prov, model, "degraded");
            return new TurnResult(OscarResponse.safeFallback(), false,
                    OscarResponse.safeFallback().message(), prov, model, PROMPT_VERSION, usage);
        }
        OscarResponse response = withCitations(parsed.get(), citations);
        if (actions != null && !actions.isEmpty()) {
            response = response.withActions(actions);
        }
        response = response.withMeta(new OscarResponse.Meta(prov, model, PROMPT_VERSION, "ok", false));
        gateway.countRequest(prov, model, "ok");
        return new TurnResult(response, true, response.message(), prov, model, PROMPT_VERSION, usage);
    }

    private TurnResult degraded() {
        gateway.countRequest("none", "-", "degraded");
        return TurnResult.notPersisted(OscarResponse.unavailable("all-providers-failed"), PROMPT_VERSION);
    }

    private AiRequest request(List<ChatItem> history, List<com.orbit.platform.ai.provider.ToolSpec> tools) {
        return new AiRequest(prompt.systemPrompt(), history, tools, props.outputTokenBudget(), 0.2);
    }

    private List<com.orbit.platform.ai.provider.ToolSpec> tools() {
        return toolRegistry.specs();
    }

    static OscarResponse withCitations(OscarResponse r, List<String> citations) {
        if (citations == null || citations.isEmpty() || r.citations() != null) {
            return r;
        }
        return new OscarResponse(r.kind(), r.message(), r.sections(), r.proposedActions(), citations, r.meta());
    }
}
