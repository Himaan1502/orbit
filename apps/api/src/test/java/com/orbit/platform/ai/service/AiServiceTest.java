package com.orbit.platform.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orbit.platform.ai.AiProperties;
import com.orbit.platform.ai.FakeProvider;
import com.orbit.platform.ai.model.TokenUsage;
import com.orbit.platform.ai.provider.AiCompletion;
import com.orbit.platform.ai.provider.ToolSpec;
import com.orbit.platform.ai.service.ProviderGateway.Attempt;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Blocking {@link AiService} — v2 parsing + the OSCAR-never-writes property, with a mocked gateway. */
class AiServiceTest {

    private final ObjectMapper json = new ObjectMapper();
    private final FakeProvider provider = new FakeProvider("gemini");
    private ProviderGateway gateway;
    private ToolRegistry toolRegistry;
    private MutationProposer mutationProposer;
    private AiService service;

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
        service = new AiService(props(), gateway, toolRounds, toolRegistry, prompt, mutationProposer,
                new ResponseValidator(json), new SimpleMeterRegistry());
    }

    private static AiProperties props() {
        return new AiProperties("gemini", "groq", 50, 24000, 2000, 3, 12, 2000, 5000,
                new AiProperties.Gemini("k", "gemini-3.6-flash", "http://x"),
                new AiProperties.Groq("", "openai/gpt-oss-120b", "http://x"));
    }

    private Attempt attempt(AiCompletion c) {
        return new Attempt(provider, c);
    }

    private AiTurn turn() {
        return new AiTurn(UUID.randomUUID(), UUID.randomUUID(), List.of(), "what should I do?");
    }

    @Test
    void featureFlagOffDegradesWithoutCallingAProvider() {
        when(gateway.anyProviderConfigured()).thenReturn(false);
        TurnResult r = service.respond(turn());
        assertThat(r.response().kind()).isEqualTo(OscarResponse.Kind.unavailable);
        assertThat(r.persistable()).isFalse();
    }

    @Test
    void plainProseIsTheAnswer() {
        when(gateway.callBlocking(any())).thenReturn(attempt(FakeProvider.text("Do the migration first.")));
        TurnResult r = service.respond(turn());
        assertThat(r.response().kind()).isEqualTo(OscarResponse.Kind.answer);
        assertThat(r.response().message()).isEqualTo("Do the migration first.");
        assertThat(r.persistable()).isTrue();
    }

    @Test
    void proseThenMetaSentinelYieldsARecommendation() {
        String reply = "Focus on Kubernetes this week.\n===OSCAR-META===\n"
                + "{\"kind\":\"recommendation\",\"sections\":{\"action\":\"Assess Kubernetes\","
                + "\"why\":\"largest gap\",\"impact\":\"readiness +4\"},\"citations\":[\"careerTarget\"]}";
        when(gateway.callBlocking(any())).thenReturn(attempt(FakeProvider.text(reply)));
        TurnResult r = service.respond(turn());
        assertThat(r.response().kind()).isEqualTo(OscarResponse.Kind.recommendation);
        assertThat(r.response().message()).isEqualTo("Focus on Kubernetes this week.");
        assertThat(r.response().sections().action()).isEqualTo("Assess Kubernetes");
        assertThat(r.response().citations()).contains("careerTarget");
    }

    @Test
    void aMutationToolCallOnlyProducesAProposedActionNeverADispatch() {
        when(gateway.callBlocking(any()))
                .thenReturn(attempt(FakeProvider.toolCall("create_task", "{\"title\":\"x\"}")))
                .thenReturn(attempt(FakeProvider.text("I've proposed a task.")));
        when(mutationProposer.propose(any())).thenReturn(new MutationOutcome.Proposed(
                new ProposedAction("create_task", "Create task \"x\"",
                        new ProposedAction.ApiCall("POST", "/api/v1/tasks", Map.of("title", "x")), "because")));

        TurnResult r = service.respond(turn());

        assertThat(r.response().proposedActions()).hasSize(1);
        verify(mutationProposer).propose(any());
        verify(toolRegistry, never()).dispatchRead(any());
    }

    @Test
    void noProseAtAllIsASafeFallbackUnpersisted() {
        when(gateway.callBlocking(any()))
                .thenReturn(attempt(new AiCompletion("", List.of(), TokenUsage.ZERO, "STOP")));
        TurnResult r = service.respond(turn());
        assertThat(r.persistable()).isFalse();
        assertThat(r.response().message()).contains("couldn't");
    }
}
