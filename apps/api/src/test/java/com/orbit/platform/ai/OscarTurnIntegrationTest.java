package com.orbit.platform.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orbit.platform.ai.model.TokenUsage;
import com.orbit.platform.ai.provider.AiCompletion;
import com.orbit.platform.ai.provider.ChatItem;
import com.orbit.platform.ai.provider.GeminiProvider;
import com.orbit.platform.support.AbstractApiIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A full OSCAR turn with a stubbed provider (no network). Proves the persistence
 * contract (ADR-032 §1) and — the property the product owner most wants held —
 * that a mutation tool call <b>only ever produces a {@code ProposedAction}</b>
 * and writes nothing (ADR-032 §3/§4).
 */
@AutoConfigureMockMvc
@TestPropertySource(properties = "orbit.ai.gemini.api-key=itest-key")
class OscarTurnIntegrationTest extends AbstractApiIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @MockBean
    GeminiProvider gemini;

    @BeforeEach
    void stubProviderIdentity() {
        when(gemini.name()).thenReturn("gemini");
        when(gemini.model()).thenReturn("gemini-3.6-flash");
        when(gemini.isConfigured()).thenReturn(true);
    }

    private RequestPostProcessor user(UUID sub) {
        return jwt().jwt(j -> j.subject(sub.toString()).claim("email", sub + "@example.com")
                .claim("roles", List.of("USER")));
    }

    private static AiCompletion answer(String jsonText) {
        return new AiCompletion(jsonText, List.of(), new TokenUsage(120, 20), "STOP");
    }

    private String data(String body, String path) throws Exception {
        return json.readTree(body).at("/data" + path).asText();
    }

    @Test
    void statusReportsEnabled() throws Exception {
        mvc.perform(get("/api/v1/oscar/status").with(user(UUID.randomUUID())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabled").value(true));
    }

    @Test
    void aPlainAnswerTurnPersistsExactlyTheUserMessageAndTheFinalText() throws Exception {
        when(gemini.complete(any())).thenReturn(
                answer("{\"kind\":\"answer\",\"message\":\"Start with the reconcile loop.\"}"));
        var u = user(UUID.randomUUID());

        String body = mvc.perform(post("/api/v1/oscar/conversations").with(u)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"what should I do first?\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.response.kind").value("answer"))
                .andExpect(jsonPath("$.data.response.message").value("Start with the reconcile loop."))
                .andExpect(jsonPath("$.data.newMessages.length()").value(2))
                .andReturn().getResponse().getContentAsString();

        String convId = data(body, "/conversation/id");
        mvc.perform(get("/api/v1/oscar/conversations/" + convId).with(u))
                .andExpect(jsonPath("$.data.conversation.title").value("what should I do first?"))
                .andExpect(jsonPath("$.data.messages.length()").value(2))
                .andExpect(jsonPath("$.data.messages[0].role").value("user"))
                .andExpect(jsonPath("$.data.messages[1].role").value("assistant"))
                .andExpect(jsonPath("$.data.messages[1].provider").value("gemini"));
    }

    @Test
    void aMutationToolCallProducesAProposedActionAndWritesNothing() throws Exception {
        var u = user(UUID.randomUUID());
        String objectiveId = data(mvc.perform(post("/api/v1/objectives").with(u)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Ship the operator\",\"priority\":4}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "/id");

        String args = "{\"objectiveId\":\"" + objectiveId + "\",\"title\":\"Write the reconcile loop\"}";
        when(gemini.complete(any()))
                .thenReturn(new AiCompletion(null,
                        List.of(new ChatItem.ToolCall("create_task#0", "create_task", args)),
                        new TokenUsage(80, 12), "STOP"))
                .thenReturn(answer("{\"kind\":\"recommendation\",\"message\":\"I've proposed a task.\"}"));

        mvc.perform(post("/api/v1/oscar/conversations").with(u)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"add a task for the reconcile loop\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.response.proposedActions.length()").value(1))
                .andExpect(jsonPath("$.data.response.proposedActions[0].tool").value("create_task"))
                .andExpect(jsonPath("$.data.response.proposedActions[0].call.method").value("POST"))
                .andExpect(jsonPath("$.data.response.proposedActions[0].call.path").value("/api/v1/tasks"))
                .andExpect(jsonPath("$.data.response.proposedActions[0].call.body.objectiveId")
                        .value(objectiveId));

        // OSCAR wrote nothing — the task list is still empty
        mvc.perform(get("/api/v1/tasks").with(u))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());

        // and the proposed call is a real, working endpoint when the user confirms it
        mvc.perform(post("/api/v1/tasks").with(u).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"objectiveId\":\"" + objectiveId + "\",\"title\":\"Write the reconcile loop\"}"))
                .andExpect(status().isCreated());
        mvc.perform(get("/api/v1/tasks").with(u))
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    void aMutationReferencingAnotherUsersObjectiveIsRejectedNotProposed() throws Exception {
        var alice = user(UUID.randomUUID());
        var bob = user(UUID.randomUUID());
        String aliceObjective = data(mvc.perform(post("/api/v1/objectives").with(alice)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Alice only\",\"priority\":3}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "/id");

        String args = "{\"objectiveId\":\"" + aliceObjective + "\",\"title\":\"sneaky\"}";
        when(gemini.complete(any()))
                .thenReturn(new AiCompletion(null,
                        List.of(new ChatItem.ToolCall("create_task#0", "create_task", args)),
                        new TokenUsage(40, 8), "STOP"))
                .thenReturn(answer("{\"kind\":\"answer\",\"message\":\"I couldn't add that.\"}"));

        mvc.perform(post("/api/v1/oscar/conversations").with(bob)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"add a task to Alice's objective\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.response.proposedActions").doesNotExist());
    }
}
