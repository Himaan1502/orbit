package com.orbit.platform.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orbit.platform.support.AbstractApiIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code /api/v1/oscar} with <b>no provider key</b> (the default test profile):
 * V10 schema applies, conversation CRUD works, ownership is 404, and a turn
 * degrades gracefully instead of erroring (PRD §10, UX §25.5).
 */
@AutoConfigureMockMvc
class OscarConversationIntegrationTest extends AbstractApiIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    private RequestPostProcessor user(UUID sub) {
        return jwt().jwt(j -> j.subject(sub.toString()).claim("email", sub + "@example.com")
                .claim("roles", List.of("USER")));
    }

    private String id(String body) throws Exception {
        return json.readTree(body).path("data").path("conversation").path("id").asText();
    }

    @Test
    void statusReportsDisabledWhenNoKeyIsConfigured() throws Exception {
        mvc.perform(get("/api/v1/oscar/status").with(user(UUID.randomUUID())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabled").value(false))
                .andExpect(jsonPath("$.data.primary").value("gemini"))
                .andExpect(jsonPath("$.data.fallback").value("groq"));
    }

    @Test
    void contextualNudgeIsUnavailableWithNoKey() throws Exception {
        mvc.perform(get("/api/v1/oscar/nudge").param("surface", "today").with(user(UUID.randomUUID())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("unavailable"))
                .andExpect(jsonPath("$.data.card").doesNotExist());
    }

    @Test
    void aTurnDegradesGracefullyAndPersistsNothingWhenOscarIsDisabled() throws Exception {
        var u = user(UUID.randomUUID());
        String body = mvc.perform(post("/api/v1/oscar/conversations").with(u)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"what should I do today?\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.response.kind").value("unavailable"))
                .andExpect(jsonPath("$.data.newMessages").isEmpty())
                .andReturn().getResponse().getContentAsString();

        // conversation row exists, but no messages were persisted (ADR-032 §1/§6)
        mvc.perform(get("/api/v1/oscar/conversations/" + id(body)).with(u))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.messages").isEmpty());
    }

    @Test
    void conversationsAreOwnershipScopedTo404() throws Exception {
        var alice = user(UUID.randomUUID());
        var bob = user(UUID.randomUUID());
        String body = mvc.perform(post("/api/v1/oscar/conversations").with(alice)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Alice plan\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String convId = id(body);

        mvc.perform(get("/api/v1/oscar/conversations/" + convId).with(bob))
                .andExpect(status().isNotFound());
        mvc.perform(patch("/api/v1/oscar/conversations/" + convId).with(bob)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"hijack\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/oscar/conversations/" + convId).with(bob))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/oscar/conversations/" + convId + "/turns").with(bob)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"hi\"}"))
                .andExpect(status().isNotFound());

        // owner still sees it
        mvc.perform(get("/api/v1/oscar/conversations/" + convId).with(alice))
                .andExpect(status().isOk());
    }

    @Test
    void listOnlyReturnsTheCallersConversations() throws Exception {
        var alice = user(UUID.randomUUID());
        var bob = user(UUID.randomUUID());
        mvc.perform(post("/api/v1/oscar/conversations").with(alice)
                .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"A\"}"));
        mvc.perform(get("/api/v1/oscar/conversations").with(bob))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());
    }
}
