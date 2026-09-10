package com.orbit.platform.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orbit.platform.ai.model.TokenUsage;
import com.orbit.platform.ai.provider.AiCompletion;
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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code GET /api/v1/oscar/nudge} (ADR-034) with a stubbed provider + real Redis
 * (Testcontainers): a ready card, the fingerprint cache (2nd identical call
 * makes no provider call), ownership-404 → none, and SKIP → none.
 */
@AutoConfigureMockMvc
@TestPropertySource(properties = "orbit.ai.gemini.api-key=itest-key")
class OscarNudgeIntegrationTest extends AbstractApiIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @MockBean
    GeminiProvider gemini;

    @BeforeEach
    void stubProvider() {
        when(gemini.name()).thenReturn("gemini");
        when(gemini.model()).thenReturn("gemini-3.6-flash");
        when(gemini.isConfigured()).thenReturn(true);
    }

    private RequestPostProcessor user(UUID sub) {
        return jwt().jwt(j -> j.subject(sub.toString()).claim("email", sub + "@example.com")
                .claim("roles", List.of("USER")));
    }

    private static AiCompletion text(String t) {
        return new AiCompletion(t, List.of(), new TokenUsage(60, 20), "STOP");
    }

    private String id(String body) throws Exception {
        return json.readTree(body).at("/data/id").asText();
    }

    @Test
    void todayNudgeReturnsAReadyCardThenServesItFromCache() throws Exception {
        when(gemini.complete(any())).thenReturn(text(
                "Lock in your top task\nYou have no plan yet — generate one and start the reconcile loop.\n"
                        + "Why: no daily plan for today"));
        var u = user(UUID.randomUUID());

        mvc.perform(get("/api/v1/oscar/nudge").param("surface", "today").with(u))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ready"))
                .andExpect(jsonPath("$.data.card.headline").value("Lock in your top task"))
                .andExpect(jsonPath("$.data.card.why").value("no daily plan for today"));

        // identical inputs → served from the fingerprint cache, no second provider call
        mvc.perform(get("/api/v1/oscar/nudge").param("surface", "today").with(u))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ready"));

        verify(gemini, times(1)).complete(any());
    }

    @Test
    void skipReplyProducesNone() throws Exception {
        when(gemini.complete(any())).thenReturn(text("SKIP"));
        mvc.perform(get("/api/v1/oscar/nudge").param("surface", "today").with(user(UUID.randomUUID())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("none"))
                .andExpect(jsonPath("$.data.card").doesNotExist());
    }

    @Test
    void anotherUsersObjectiveNudgeIsNone() throws Exception {
        var alice = user(UUID.randomUUID());
        var bob = user(UUID.randomUUID());
        when(gemini.complete(any())).thenReturn(text("h\nb\nWhy: x"));
        String objectiveId = id(mvc.perform(post("/api/v1/objectives").with(alice)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Alice only\",\"priority\":3}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());

        mvc.perform(get("/api/v1/oscar/nudge").param("surface", "objective")
                        .param("objectiveId", objectiveId).with(bob))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("none"));
    }
}
