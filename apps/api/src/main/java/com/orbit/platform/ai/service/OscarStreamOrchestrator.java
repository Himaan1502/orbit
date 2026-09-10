package com.orbit.platform.ai.service;

import com.orbit.platform.ai.AiProperties;
import com.orbit.platform.ai.model.TokenUsage;
import com.orbit.platform.ai.provider.AiCompletion;
import com.orbit.platform.ai.provider.AiProviderException;
import com.orbit.platform.ai.provider.AiRequest;
import com.orbit.platform.ai.provider.ChatItem;
import com.orbit.platform.ai.provider.TokenSink;
import com.orbit.platform.ai.provider.ToolSpec;
import com.orbit.platform.ai.service.ProviderGateway.Attempt;
import com.orbit.platform.ai.service.ResponseValidator.Meta;
import com.orbit.platform.ai.service.ResponseValidator.Split;
import com.orbit.platform.ai.service.ToolRounds.Classified;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Streaming OSCAR turn (ADR-033). Same tool-round loop as {@link AiService} but
 * every provider call streams answer prose to a {@link StreamSink} via a
 * {@link ProseFilter} that stops forwarding at the {@code ===OSCAR-META===}
 * sentinel. Fail-over is gated on the first token ({@link ProviderGateway}); a
 * drop after tokens have flowed ends the stream with what arrived, unpersisted.
 */
@Service
public class OscarStreamOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(OscarStreamOrchestrator.class);
    private static final String UNAVAILABLE = "OSCAR is temporarily unavailable.";
    private static final Map<String, String> STATUS_PHRASE = Map.of(
            "get_current_objectives", "your objectives",
            "get_active_career_target", "your career target",
            "get_skill_gap", "your skill gaps",
            "get_skill_dependencies", "the skill graph",
            "get_project_status", "your projects",
            "get_today_plan", "today's plan",
            "get_recent_execution", "your recent execution",
            "get_weekly_metrics", "this week's metrics",
            "get_roadmap_intersection", "your roadmaps");

    private final AiProperties props;
    private final ProviderGateway gateway;
    private final ToolRounds toolRounds;
    private final ToolRegistry toolRegistry;
    private final OscarPrompt prompt;
    private final MutationProposer mutationProposer;
    private final ResponseValidator validator;

    public OscarStreamOrchestrator(AiProperties props, ProviderGateway gateway, ToolRounds toolRounds,
                                   ToolRegistry toolRegistry, OscarPrompt prompt,
                                   MutationProposer mutationProposer, ResponseValidator validator) {
        this.props = props;
        this.gateway = gateway;
        this.toolRounds = toolRounds;
        this.toolRegistry = toolRegistry;
        this.prompt = prompt;
        this.mutationProposer = mutationProposer;
        this.validator = validator;
    }

    public boolean isEnabled() {
        return gateway.anyProviderConfigured();
    }

    public TurnResult stream(AiTurn turn, StreamSink sink) {
        if (!isEnabled()) {
            sink.error(false, UNAVAILABLE);
            gateway.countRequest("none", "-", "degraded");
            return TurnResult.notPersisted(OscarResponse.unavailable("no-provider-key"), OscarPrompt.VERSION);
        }

        OscarPrompt.Opening opening = prompt.open(turn);
        List<ChatItem> history = opening.history();
        List<String> citations = opening.citationLayers();
        AtomicBoolean firstToken = new AtomicBoolean(false);
        ProseFilter filter = new ProseFilter(sink, firstToken);
        TokenUsage usage = TokenUsage.ZERO;
        int round = 0;

        try {
            Attempt attempt = gateway.callStreaming(req(history, tools()), filter, firstToken);
            if (attempt == null) {
                sink.error(true, UNAVAILABLE);
                return degraded();
            }
            usage = usage.plus(attempt.completion().usage());

            while (true) {
                AiCompletion c = attempt.completion();
                if (!c.hasToolCalls()) {
                    filter.flush();
                    return finalise(attempt, filter, null, usage, citations, sink);
                }
                Classified split = toolRounds.classify(c.toolCalls());
                if (!split.mutation().isEmpty()) {
                    return proposeAndStream(attempt, history, split, filter, usage, citations, sink, firstToken);
                }
                sink.status("Checking " + phrase(split.read()) + "…");
                history.add(ToolRounds.callsAsText(split.read()));
                history.add(ToolRounds.resultsAsText(toolRounds.executeReads(split.read())));
                round++;
                boolean last = round >= props.maxToolRounds();
                attempt = gateway.callStreaming(req(history, last ? List.of() : tools()), filter, firstToken);
                if (attempt == null) {
                    sink.error(true, UNAVAILABLE);
                    return degraded();
                }
                usage = usage.plus(attempt.completion().usage());
                if (last && attempt.completion().hasToolCalls()) {
                    filter.flush();
                    return finalise(attempt, filter, null, usage, citations, sink);
                }
            }
        } catch (AiProviderException droppedMidAnswer) {
            log.warn("event=oscar.stream.dropped msg={}", droppedMidAnswer.getMessage());
            filter.flush();   // release whatever prose had arrived
            sink.error(true, "The connection dropped — ask again to retry.");
            gateway.countRequest("none", "-", "degraded");
            return TurnResult.notPersisted(new OscarResponse(OscarResponse.Kind.answer,
                    filter.proseSoFar(), null, null, null, null), OscarPrompt.VERSION);
        } catch (RuntimeException unexpected) {
            log.error("event=oscar.stream.unexpected user_id={}", turn.userId(), unexpected);
            sink.error(true, UNAVAILABLE);
            gateway.countRequest("none", "-", "degraded");
            return TurnResult.notPersisted(OscarResponse.unavailable("internal-error"), OscarPrompt.VERSION);
        }
    }

    private TurnResult proposeAndStream(Attempt round, List<ChatItem> history, Classified split,
                                        ProseFilter filter, TokenUsage usage, List<String> citations,
                                        StreamSink sink, AtomicBoolean firstToken) {
        List<ProposedAction> proposed = new ArrayList<>();
        List<String> rejected = new ArrayList<>();
        for (ChatItem.ToolCall call : split.mutation()) {
            MutationOutcome outcome = mutationProposer.propose(call);
            if (outcome instanceof MutationOutcome.Proposed p) {
                proposed.add(p.action());
                sink.proposedAction(p.action());
            } else if (outcome instanceof MutationOutcome.Rejected r) {
                rejected.add(r.tool() + ": " + r.reason());
            }
        }
        sink.status("Preparing a recommendation…");

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

        Attempt synth = gateway.callStreaming(req(history, List.of()), filter, firstToken);
        filter.flush();
        Attempt effective = synth != null ? synth : round;
        TokenUsage total = synth != null ? usage.plus(synth.completion().usage()) : usage;
        return finalise(effective, filter, proposed, total, citations, sink);
    }

    private TurnResult finalise(Attempt attempt, ProseFilter filter, List<ProposedAction> actions,
                                TokenUsage usage, List<String> citations, StreamSink sink) {
        String prov = attempt.provider().name();
        String model = attempt.provider().model();
        Split s = validator.split(filter.fullText());
        Meta meta = validator.parseMeta(s.metaJson()).orElse(null);
        String prose = s.prose();

        if (prose.isBlank()) {
            OscarResponse fb = OscarResponse.safeFallback();
            sink.token(fb.message());
            sink.meta(fb.withMeta(new OscarResponse.Meta(prov, model, OscarPrompt.VERSION, "degraded", true)));
            gateway.countRequest(prov, model, "degraded");
            return new TurnResult(fb, false, fb.message(), prov, model, OscarPrompt.VERSION, usage);
        }

        OscarResponse.Sections sections = meta != null ? meta.sections() : null;
        OscarResponse.Kind kind = sections != null ? OscarResponse.Kind.recommendation
                : (meta != null && meta.kind() != null ? meta.kind() : OscarResponse.Kind.answer);
        List<String> cites = meta != null && meta.citations() != null && !meta.citations().isEmpty()
                ? meta.citations() : (citations.isEmpty() ? null : citations);

        OscarResponse response = new OscarResponse(kind, prose, sections,
                actions == null || actions.isEmpty() ? null : actions, cites,
                new OscarResponse.Meta(prov, model, OscarPrompt.VERSION, "ok", false));
        sink.meta(response);
        gateway.countRequest(prov, model, "ok");
        return new TurnResult(response, true, prose, prov, model, OscarPrompt.VERSION, usage);
    }

    private TurnResult degraded() {
        gateway.countRequest("none", "-", "degraded");
        return TurnResult.notPersisted(OscarResponse.unavailable("all-providers-failed"), OscarPrompt.VERSION);
    }

    private AiRequest req(List<ChatItem> history, List<ToolSpec> tools) {
        return new AiRequest(prompt.systemPrompt(), history, tools, props.outputTokenBudget(), 0.2);
    }

    private List<ToolSpec> tools() {
        return toolRegistry.specs();
    }

    private String phrase(List<ChatItem.ToolCall> calls) {
        if (calls.isEmpty()) {
            return "your data";
        }
        return calls.stream()
                .map(c -> STATUS_PHRASE.getOrDefault(c.name(), c.name().replace('_', ' ')))
                .distinct()
                .reduce((a, b) -> a + " and " + b)
                .orElse("your data");
    }

    /**
     * A {@link TokenSink} that forwards answer prose to a {@link StreamSink} and
     * stops at the {@code ===OSCAR-META===} sentinel, holding back a
     * partial-sentinel tail across chunk boundaries. Accumulates the whole text
     * for the final split.
     */
    static final class ProseFilter implements TokenSink {

        private static final int TAIL = ResponseValidator.SENTINEL.length() - 1;

        private final StreamSink out;
        private final AtomicBoolean firstToken;
        private final StringBuilder full = new StringBuilder();
        private int emitted;
        private boolean sentinelHit;

        ProseFilter(StreamSink out, AtomicBoolean firstToken) {
            this.out = out;
            this.firstToken = firstToken;
        }

        @Override
        public void onToken(String delta) {
            full.append(delta);
            if (sentinelHit) {
                return;
            }
            String s = full.toString();
            int idx = s.indexOf(ResponseValidator.SENTINEL);
            if (idx >= 0) {
                emit(s.substring(emitted, idx));
                sentinelHit = true;
                return;
            }
            int safe = s.length() - TAIL;
            if (safe > emitted) {
                emit(s.substring(emitted, safe));
            }
        }

        /** Stream ended with no sentinel — release the held tail. */
        void flush() {
            if (sentinelHit) {
                return;
            }
            String s = full.toString();
            if (s.length() > emitted) {
                emit(s.substring(emitted));
            }
        }

        String fullText() {
            return full.toString();
        }

        String proseSoFar() {
            String s = full.toString();
            int idx = s.indexOf(ResponseValidator.SENTINEL);
            return (idx >= 0 ? s.substring(0, idx) : s).strip();
        }

        private void emit(String chunk) {
            if (chunk.isEmpty()) {
                return;
            }
            out.token(chunk);
            emitted += chunk.length();
            firstToken.set(true);
        }
    }
}
