package com.orbit.platform.skills;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orbit.platform.support.AbstractApiIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class SkillsIntegrationTest extends AbstractApiIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper json;

    private RequestPostProcessor user(UUID sub) {
        return jwt().jwt(j -> j.subject(sub.toString()).claim("email", sub + "@example.com")
                .claim("roles", java.util.List.of("USER")));
    }

    private String skillIdByName(RequestPostProcessor u, String name) throws Exception {
        JsonNode list = json.readTree(mockMvc.perform(get("/api/v1/skills").with(u))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("data");
        for (JsonNode s : list) {
            if (s.path("canonicalName").asText().equals(name)) {
                return s.path("id").asText();
            }
        }
        throw new AssertionError("seed skill not found: " + name);
    }

    @Test
    void theSeededCatalogueIsAvailable() throws Exception {
        mockMvc.perform(get("/api/v1/skills").with(user(UUID.randomUUID())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(30)))
                .andExpect(jsonPath("$.data[?(@.canonicalName == 'Kubernetes')]").exists());
    }

    @Test
    void selfAssessmentComputesTheBlueprintSection23Overall() throws Exception {
        var u = user(UUID.randomUUID());
        String kubernetes = skillIdByName(u, "Kubernetes");

        String body = mockMvc.perform(post("/api/v1/skills/" + kubernetes + "/assessments").with(u)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"knowledge":0.8,"practical":0.6,"evidence":0.4,"interview":0.5,"production":0.3}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.confidence").value("self-reported"))
                .andExpect(jsonPath("$.data.assessmentSource").value("self"))
                .andReturn().getResponse().getContentAsString();
        // 0.20*.8 + 0.25*.6 + 0.20*.4 + 0.15*.5 + 0.20*.3 = 0.525
        assertThat(json.readTree(body).path("data").path("overall").asDouble()).isCloseTo(0.525, org.assertj.core.data.Offset.offset(1e-3));

        mockMvc.perform(get("/api/v1/skills/" + kubernetes).with(u))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.capability").value(org.hamcrest.Matchers.closeTo(0.525, 1e-3)))
                .andExpect(jsonPath("$.data.dimensions.practical").value(0.6));
    }

    @Test
    void dependenciesAreNavigable() throws Exception {
        var u = user(UUID.randomUUID());
        String kubernetes = skillIdByName(u, "Kubernetes");
        mockMvc.perform(get("/api/v1/skills/" + kubernetes + "/dependencies").with(u))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.prerequisites[?(@.name == 'Docker')]").exists())
                .andExpect(jsonPath("$.data.dependents[?(@.name == 'Kubernetes Production')]").exists());
    }

    @Test
    void unknownSkillIs404AndBadScoresAreRejected() throws Exception {
        var u = user(UUID.randomUUID());
        mockMvc.perform(get("/api/v1/skills/" + UUID.randomUUID()).with(u)).andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/skills/" + skillIdByName(u, "Docker") + "/assessments").with(u)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"knowledge":1.5,"practical":0.6,"evidence":0.4,"interview":0.5,"production":0.3}"""))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }
}
