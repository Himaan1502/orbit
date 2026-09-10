package com.orbit.platform.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orbit.platform.ai.AiProperties;
import com.orbit.platform.ai.FakeProvider;
import com.orbit.platform.ai.provider.AiProviderException;
import com.orbit.platform.ai.provider.TokenSink;
import com.orbit.platform.ai.provider.ToolSpec;
import com.orbit.platform.ai.service.ProviderGateway.Attempt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** {@link OscarStreamOrchestrator} — token streaming, sentinel filtering, mid-drop (ADR-033). */
class OscarStreamOrchestratorTest {

    private final ObjectMapper json = new ObjectMapper();
    private final FakeProvider provider = new FakeProvider("gemini");
    private ProviderGateway gateway;
    private ToolRegistry toolRegistry;
    private MutationProposer mutationProposer;
    private OscarStreamOrchestrator orchestrator;

    @BeforeEach
    void setup() {
        gateway = mock(ProviderGateway.class);
        toolRegistry = mock(ToolRegistry.class);
        mutationProposer = mock(MutationProposer.class);
        ContextBuilder contextBuilder = mock(ContextBuilder.class);
        when(contextBuilder.forTurn(any(), any())).thenReturn(new ContextBundle(Map.of(), List.of()));
        when(gateway.anyProviderConfigured()).thenReturn(true);
        when(toolRegistry.specs()).thenReturn(List.of(
                new ToolSpec("get_today_plan", "d", "{}", ToolSpec.Kind.READ),
                new ToolSpec("create_task", "d", "{}", ToolSpec.Kind.MUTATION)));
        when(toolRegistry.isKnown(any())).thenReturn(true);
        when(toolRegistry.isMutation("create_task")).thenReturn(true);

        ToolRounds toolRounds = new ToolRounds(json, toolRegistry, gateway);
        OscarPrompt prompt = new OscarPrompt(contextBuilder, json,
                new ClassPathResource("oscar/system-prompt-v2.md"));
        orchestrator = new OscarStreamOrchestrator(props(), gateway, toolRounds, toolRegistry, prompt,
                mutationProposer, new ResponseValidator(json));
    }

    private static AiProperties props() {
        return new AiProperties("gemini", "groq", 50, 24000, 2000, 3, 12, 2000, 5000,
                new AiProperties.Gemini("k", "gemini-3.6-flash", "http://x"),
                new AiProperties.Groq("", "openai/gpt-oss-120b", "http://x"));
    }

    private AiTurn turn() {
        return new AiTurn(UUID.randomUUID(), UUID.randomUUID(), List.of(), "what now?");
    }

    /** Make {@code callStreaming} feed {@code text} to the sink then return a completion carrying it. */
    private void streamsOnce(String text) {
        when(gateway.callStreaming(any(), any(), any())).thenAnswer(inv -> {
            TokenSink sink = inv.getArgument(1);
            sink.onToken(text);
            return new Attempt(provider, FakeProvider.text(text));
        });
    }

    @Test
    void streamsProseAndEmitsMeta() {
        streamsOnce("Start with the reconcile loop.");
        CapturingSink sink = new CapturingSink();
        TurnResult r = orchestrator.stream(turn(), sink);
        assertThat(sink.text()).isEqualTo("Start with the reconcile loop.");
        assertThat(sink.metas).hasSize(1);
        assertThat(r.persistable()).isTrue();
        assertThat(r.response().message()).isEqualTo("Start with the reconcile loop.");
    }

    @Test
    void theSentinelAndMetaBlockAreNeverStreamedToTheUser() {
        streamsOnce("Assess Kubernetes.\n===OSCAR-META===\n{\"kind\":\"recommendation\","
                + "\"sections\":{\"action\":\"assess\",\"why\":\"gap\",\"impact\":\"+4\"}}");
        CapturingSink sink = new CapturingSink();
        TurnResult r = orchestrator.stream(turn(), sink);
        assertThat(sink.text()).isEqualTo("Assess Kubernetes.");
        assertThat(sink.text()).doesNotContain("OSCAR-META").doesNotContain("{");
        assertThat(r.response().kind()).isEqualTo(OscarResponse.Kind.recommendation);
        assertThat(r.response().sections().action()).isEqualTo("assess");
    }

    @Test
    void aMutationCallEmitsAProposedActionEvent() {
        when(gateway.callStreaming(any(), any(), any()))
                .thenAnswer(inv -> new Attempt(provider,
                        FakeProvider.toolCall("create_task", "{\"title\":\"x\"}")))
                .thenAnswer(inv -> {
                    TokenSink sink = inv.getArgument(1);
                    sink.onToken("I've proposed a task.");
                    return new Attempt(provider, FakeProvider.text("I've proposed a task."));
                });
        when(mutationProposer.propose(any())).thenReturn(new MutationOutcome.Proposed(
                new ProposedAction("create_task", "Create task \"x\"",
                        new ProposedAction.ApiCall("POST", "/api/v1/tasks", Map.of("title", "x")), "because")));

        CapturingSink sink = new CapturingSink();
        TurnResult r = orchestrator.stream(turn(), sink);
        assertThat(sink.actions).hasSize(1);
        assertThat(r.response().proposedActions()).hasSize(1);
    }

    @Test
    void bothProvidersDownEmitsAnErrorEvent() {
        when(gateway.callStreaming(any(), any(), any())).thenReturn(null);
        CapturingSink sink = new CapturingSink();
        TurnResult r = orchestrator.stream(turn(), sink);
        assertThat(sink.errors).isNotEmpty();
        assertThat(r.persistable()).isFalse();
    }

    @Test
    void aDropAfterTokensEndsWithAnErrorAndKeepsWhatStreamed() {
        when(gateway.callStreaming(any(), any(), any())).thenAnswer(inv -> {
            TokenSink sink = inv.getArgument(1);
            sink.onToken("Partial answer");
            throw AiProviderException.transientFailure("mid-stream drop", 0, null);
        });
        CapturingSink sink = new CapturingSink();
        TurnResult r = orchestrator.stream(turn(), sink);
        assertThat(sink.text()).isEqualTo("Partial answer");
        assertThat(sink.errors).anySatisfy(e -> assertThat(e).contains("dropped"));
        assertThat(r.persistable()).isFalse();
    }

    // ---------------------------------------------------------------------

    private static final class CapturingSink implements StreamSink {
        final StringBuilder tokens = new StringBuilder();
        final List<String> statuses = new ArrayList<>();
        final List<ProposedAction> actions = new ArrayList<>();
        final List<OscarResponse> metas = new ArrayList<>();
        final List<String> errors = new ArrayList<>();
        final AtomicReference<Boolean> lastDone = new AtomicReference<>();

        String text() {
            return tokens.toString().strip();
        }

        @Override
        public void status(String t) {
            statuses.add(t);
        }

        @Override
        public void token(String delta) {
            tokens.append(delta);
        }

        @Override
        public void proposedAction(ProposedAction action) {
            actions.add(action);
        }

        @Override
        public void meta(OscarResponse response) {
            metas.add(response);
        }

        @Override
        public void error(boolean recoverable, String note) {
            errors.add(note);
        }
    }
}
