package com.orbit.platform.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orbit.platform.ai.model.TokenUsage;
import com.orbit.platform.ai.provider.AiCompletion;
import com.orbit.platform.ai.provider.AiProvider;
import com.orbit.platform.ai.provider.AiProviderException;
import com.orbit.platform.ai.provider.AiRequest;
import com.orbit.platform.ai.provider.ChatItem;
import com.orbit.platform.ai.provider.ToolSpec;
import com.orbit.platform.ai.service.AiService;
import com.orbit.platform.ai.service.AiTurn;
import com.orbit.platform.ai.service.ContextBuilder;
import com.orbit.platform.ai.service.ContextBundle;
import com.orbit.platform.ai.service.MutationOutcome;
import com.orbit.platform.ai.service.MutationProposer;
import com.orbit.platform.ai.service.OscarResponse;
import com.orbit.platform.ai.service.ProposedAction;
import com.orbit.platform.ai.service.ResponseValidator;
import com.orbit.platform.ai.service.ToolRegistry;
import com.orbit.platform.ai.service.TurnResult;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link AiService} routing (ADR-031 §2) and the OSCAR-never-writes property
 * (ADR-032 §3/§4) — with fake providers, so no network and no real model.
 */
class AiServiceRoutingTest {

    private static final String ANSWER_JSON = "{\"kind\":\"answer\",\"message\":\"do the migration\"}";

    private FakeProvider gemini;
    private FakeProvider groq;
    private ContextBuilder contextBuilder;
    private ToolRegistry tools;
    private MutationProposer mutationProposer;

    @BeforeEach
    void setup() {
        gemini = new FakeProvider("gemini");
        groq = new FakeProvider("groq");
        contextBuilder = mock(ContextBuilder.class);
        when(contextBuilder.forTurn(any(), any())).thenReturn(new ContextBundle(Map.of(), List.of()));
        tools = mock(ToolRegistry.class);
        when(tools.specs()).thenReturn(List.of(
                new ToolSpec("get_today_plan", "d", "{}", ToolSpec.Kind.READ),
                new ToolSpec("create_task", "d", "{}", ToolSpec.Kind.MUTATION)));
        when(tools.isKnown(any())).thenReturn(true);
        when(tools.isMutation("create_task")).thenReturn(true);
        mutationProposer = mock(MutationProposer.class);
    }

    private AiService service(boolean geminiKey, boolean groqKey) {
        AiProperties props = new AiProperties("gemini", "groq", 50, 24000, 2000, 3, 2000, 5000,
                new AiProperties.Gemini(geminiKey ? "k" : "", "gemini-3.6-flash", "http://x"),
                new AiProperties.Groq(groqKey ? "k" : "", "openai/gpt-oss-120b", "http://x"));
        return new AiService(props, List.of(gemini, groq), contextBuilder, tools, mutationProposer,
                new ResponseValidator(new ObjectMapper()), new ObjectMapper(), new SimpleMeterRegistry(),
                new ByteArrayResource("system prompt".getBytes()));
    }

    private AiTurn turn() {
        return new AiTurn(UUID.randomUUID(), UUID.randomUUID(), List.of(), "what should I do?");
    }

    private static AiCompletion text(String t) {
        return new AiCompletion(t, List.of(), new TokenUsage(10, 5), "STOP");
    }

    @Test
    void primarySucceeds() {
        gemini.script(text(ANSWER_JSON));
        TurnResult r = service(true, true).respond(turn());
        assertThat(r.persistable()).isTrue();
        assertThat(r.provider()).isEqualTo("gemini");
        assertThat(r.response().kind()).isEqualTo(OscarResponse.Kind.answer);
        assertThat(gemini.calls).isEqualTo(1);
        assertThat(groq.calls).isZero();
    }

    @Test
    void retriesOnceThenFailsOverOnTransient5xx() {
        gemini.script(AiProviderException.transientFailure("500", 500, null));
        gemini.script(AiProviderException.transientFailure("500", 500, null));
        groq.script(text(ANSWER_JSON));
        TurnResult r = service(true, true).respond(turn());
        assertThat(r.provider()).isEqualTo("groq");
        assertThat(gemini.calls).isEqualTo(2);   // initial + one retry
        assertThat(groq.calls).isEqualTo(1);
    }

    @Test
    void rateLimitFailsOverWithNoRetry() {
        gemini.script(AiProviderException.transientFailure("429", 429, null));
        groq.script(text(ANSWER_JSON));
        TurnResult r = service(true, true).respond(turn());
        assertThat(r.provider()).isEqualTo("groq");
        assertThat(gemini.calls).isEqualTo(1);   // no retry on 429
    }

    @Test
    void bothProvidersDownDegradesGracefully() {
        gemini.script(AiProviderException.transientFailure("500", 500, null));
        gemini.script(AiProviderException.transientFailure("500", 500, null));
        groq.script(AiProviderException.transientFailure("503", 503, null));
        TurnResult r = service(true, true).respond(turn());
        assertThat(r.persistable()).isFalse();
        assertThat(r.response().kind()).isEqualTo(OscarResponse.Kind.unavailable);
    }

    @Test
    void featureFlagOffReturnsUnavailableWithoutCallingAProvider() {
        TurnResult r = service(false, false).respond(turn());
        assertThat(r.response().kind()).isEqualTo(OscarResponse.Kind.unavailable);
        assertThat(gemini.calls).isZero();
        assertThat(groq.calls).isZero();
    }

    @Test
    void unparsableModelOutputBecomesSafeFallbackAndIsNotPersisted() {
        gemini.script(text("here is some prose with no json object"));
        TurnResult r = service(true, true).respond(turn());
        assertThat(r.persistable()).isFalse();
        assertThat(r.response().message()).contains("couldn't");
    }

    @Test
    void aMutationToolCallOnlyEverProducesAProposedAction() {
        // round 1: the model asks to create a task
        gemini.script(new AiCompletion(null,
                List.of(new ChatItem.ToolCall("create_task#0", "create_task", "{\"title\":\"x\"}")),
                new TokenUsage(10, 5), "STOP"));
        // synthesis round: the model writes the narrative
        gemini.script(text("{\"kind\":\"recommendation\",\"message\":\"I've proposed a task.\"}"));

        when(mutationProposer.propose(any())).thenReturn(new MutationOutcome.Proposed(
                new ProposedAction("create_task", "Create task \"x\"",
                        new ProposedAction.ApiCall("POST", "/api/v1/tasks", Map.of("title", "x")),
                        "because")));

        TurnResult r = service(true, true).respond(turn());

        assertThat(r.response().proposedActions()).hasSize(1);
        assertThat(r.response().proposedActions().get(0).call().method()).isEqualTo("POST");
        verify(mutationProposer).propose(any());
        // the mutation was never dispatched as a read/execute call
        verify(tools, never()).dispatchRead(any());
    }

    // ---------------------------------------------------------------------

    private static final class FakeProvider implements AiProvider {
        private final String name;
        private final Deque<Object> scripted = new ArrayDeque<>();
        int calls = 0;

        FakeProvider(String name) {
            this.name = name;
        }

        void script(Object completionOrException) {
            scripted.add(completionOrException);
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public String model() {
            return name + "-model";
        }

        @Override
        public boolean isConfigured() {
            return true;   // AiService gates on AiProperties, not this, in these tests
        }

        @Override
        public AiCompletion complete(AiRequest request) {
            calls++;
            Object next = scripted.poll();
            if (next instanceof AiProviderException e) {
                throw e;
            }
            if (next instanceof AiCompletion c) {
                return c;
            }
            throw AiProviderException.transientFailure("no script", 0, null);
        }
    }
}
