package com.orbit.platform.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orbit.platform.ai.model.TokenUsage;
import com.orbit.platform.ai.provider.AiCompletion;
import com.orbit.platform.ai.provider.AiRequest;
import com.orbit.platform.ai.provider.ChatItem;
import com.orbit.platform.ai.provider.GeminiProvider;
import com.orbit.platform.ai.provider.TokenSink;
import com.orbit.platform.support.AbstractApiIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The streamed turn ({@code /turns/stream}) runs on the {@code oscarStreamExecutor},
 * not the request thread. This proves that with {@code DelegatingSecurityContextRunnable}
 * the async turn still resolves the caller's identity — so a streamed turn's
 * tool execution is ownership-scoped exactly like the blocking path
 * ({@code OscarTurnIntegrationTest}), a streamed mutation referencing another
 * user's data is rejected and writes nothing, and a streamed read tool only ever
 * returns the caller's own rows.
 */
@AutoConfigureMockMvc
@TestPropertySource(properties = "orbit.ai.gemini.api-key=itest-key")
class OscarStreamIntegrationTest extends AbstractApiIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @MockBean
    GeminiProvider gemini;

    @BeforeEach
    void stubIdentity() {
        when(gemini.name()).thenReturn("gemini");
        when(gemini.model()).thenReturn("gemini-3.6-flash");
        when(gemini.isConfigured()).thenReturn(true);
    }

    private RequestPostProcessor user(UUID sub) {
        return jwt().jwt(j -> j.subject(sub.toString()).claim("email", sub + "@example.com")
                .claim("roles", List.of("USER")));
    }

    private String at(String body, String ptr) throws Exception {
        return json.readTree(body).at(ptr).asText();
    }

    private String newConversation(RequestPostProcessor u) throws Exception {
        return at(mvc.perform(post("/api/v1/oscar/conversations").with(u)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(),
                "/data/conversation/id");
    }

    private String stream(RequestPostProcessor u, String conversationId, String message) throws Exception {
        MvcResult started = mvc.perform(post("/api/v1/oscar/conversations/" + conversationId + "/turns/stream")
                        .with(u).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"" + message + "\"}"))
                .andExpect(request().asyncStarted())
                .andReturn();
        return mvc.perform(asyncDispatch(started))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private static AiCompletion textStreaming(String t, TokenSink sink) {
        sink.onToken(t);
        return new AiCompletion(t, List.of(), new TokenUsage(120, 20), "STOP");
    }

    // ---------------------------------------------------------------------

    @Test
    void aStreamedTurnPersistsForTheCallerAndIsOwnership404ToEveryoneElse() throws Exception {
        var alice = user(UUID.randomUUID());
        var bob = user(UUID.randomUUID());
        when(gemini.completeStream(any(), any())).thenAnswer(inv ->
                textStreaming("Start with the reconcile loop.", inv.getArgument(1)));

        String conv = newConversation(bob);
        String sse = stream(bob, conv, "what should I do first?");

        assertThat(sse).contains("event:token").contains("Start with the reconcile loop.");
        assertThat(sse).contains("event:done").contains("\"persisted\":true");

        mvc.perform(get("/api/v1/oscar/conversations/" + conv).with(bob))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.messages.length()").value(2))
                .andExpect(jsonPath("$.data.messages[1].content").value("Start with the reconcile loop."));

        mvc.perform(get("/api/v1/oscar/conversations/" + conv).with(alice))
                .andExpect(status().isNotFound());
    }

    @Test
    void aStreamedMutationReferencingAnotherUsersObjectiveIsRejectedAndWritesNothing() throws Exception {
        var alice = user(UUID.randomUUID());
        var bob = user(UUID.randomUUID());
        String aliceObjective = at(mvc.perform(post("/api/v1/objectives").with(alice)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Alice only\",\"priority\":3}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "/data/id");

        String args = "{\"objectiveId\":\"" + aliceObjective + "\",\"title\":\"sneaky\"}";
        when(gemini.completeStream(any(), any()))
                .thenAnswer(inv -> new AiCompletion(null,
                        List.of(new ChatItem.ToolCall("create_task#0", "create_task", args)),
                        new TokenUsage(40, 8), "STOP"))
                .thenAnswer(inv -> textStreaming("I couldn't add that to that objective.", inv.getArgument(1)));

        String conv = newConversation(bob);
        String sse = stream(bob, conv, "add a task to that objective");

        // no ProposedAction surfaced — the ownership check on the async thread rejected it
        assertThat(sse).doesNotContain("event:proposedAction");
        // nothing written for anyone
        mvc.perform(get("/api/v1/tasks").with(bob)).andExpect(jsonPath("$.data").isEmpty());
        mvc.perform(get("/api/v1/tasks").with(alice)).andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void aStreamedReadToolReturnsOnlyTheCallersRows() throws Exception {
        var alice = user(UUID.randomUUID());
        var bob = user(UUID.randomUUID());
        mvc.perform(post("/api/v1/objectives").with(alice).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"ALICE_SECRET_OBJECTIVE\",\"priority\":3}")).andExpect(status().isCreated());
        mvc.perform(post("/api/v1/objectives").with(bob).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"BOB_OWN_OBJECTIVE\",\"priority\":3}")).andExpect(status().isCreated());

        when(gemini.completeStream(any(), any()))
                .thenAnswer(inv -> new AiCompletion(null,
                        List.of(new ChatItem.ToolCall("get_current_objectives#0", "get_current_objectives", "{}")),
                        new TokenUsage(30, 5), "STOP"))
                .thenAnswer(inv -> textStreaming("Here's what I see.", inv.getArgument(1)));

        String conv = newConversation(bob);
        stream(bob, conv, "list my objectives");

        // the read tool ran on the async thread; the 2nd (synthesis) request's history
        // carries the tool result — it must contain only Bob's objective
        ArgumentCaptor<AiRequest> captor = ArgumentCaptor.forClass(AiRequest.class);
        verify(gemini, times(2)).completeStream(captor.capture(), any());
        String synthesisHistory = captor.getAllValues().get(1).history().stream()
                .map(Object::toString).reduce("", (a, b) -> a + "\n" + b);

        assertThat(synthesisHistory).contains("BOB_OWN_OBJECTIVE");
        assertThat(synthesisHistory).doesNotContain("ALICE_SECRET_OBJECTIVE");
    }
}
