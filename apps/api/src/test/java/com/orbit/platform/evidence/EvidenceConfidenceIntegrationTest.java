package com.orbit.platform.evidence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orbit.platform.support.AbstractApiIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ADR-026 §1 — logging evidence for an assessed skill raises its {@code confidence}
 * from {@code self-reported} to {@code partially-verified} to {@code verified};
 * it never changes a dimension score, and an un-assessed skill stays "not assessed".
 */
@AutoConfigureMockMvc
class EvidenceConfidenceIntegrationTest extends AbstractApiIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper json;

    private static final String KUBERNETES = "72382ac3-0de3-562e-8b97-ae94d4acbb04";

    private RequestPostProcessor user(UUID sub) {
        return jwt().jwt(j -> j.subject(sub.toString()).claim("email", sub + "@example.com")
                .claim("roles", java.util.List.of("USER")));
    }

    private void addEvidence(RequestPostProcessor u, String type, double weight) throws Exception {
        mockMvc.perform(post("/api/v1/evidence").with(u).contentType(MediaType.APPLICATION_JSON)
                        .content(("{\"type\":\"%s\",\"title\":\"%s work\",\"skills\":[{\"skillId\":\"%s\",\"contributionWeight\":%s}]}")
                                .formatted(type, type, KUBERNETES, weight)))
                .andExpect(status().isCreated());
    }

    @Test
    void evidenceLiftsConfidenceButNotTheScore() throws Exception {
        var u = user(UUID.randomUUID());

        // 1. self-assess -> self-reported
        String assessed = mockMvc.perform(post("/api/v1/skills/" + KUBERNETES + "/assessments").with(u)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"knowledge":0.7,"practical":0.6,"evidence":0.5,"interview":0.5,"production":0.4}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.confidence").value("self-reported"))
                .andReturn().getResponse().getContentAsString();
        double overall = json.readTree(assessed).path("data").path("overall").asDouble();

        // 2. one repository evidence at weight 0.5 -> partially-verified, score unchanged
        addEvidence(u, "repository", 0.5);
        mockMvc.perform(get("/api/v1/skills/" + KUBERNETES).with(u))
                .andExpect(jsonPath("$.data.confidence").value("partially-verified"))
                .andExpect(jsonPath("$.data.capability").value(overall));

        // 3. add a deployment at 0.6 -> total 1.1 across 2 types -> verified
        addEvidence(u, "deployment", 0.6);
        mockMvc.perform(get("/api/v1/skills/" + KUBERNETES).with(u))
                .andExpect(jsonPath("$.data.confidence").value("verified"))
                .andExpect(jsonPath("$.data.capability").value(overall));

        // the /skills list carries the same confidence
        mockMvc.perform(get("/api/v1/skills").with(u))
                .andExpect(jsonPath("$.data[?(@.canonicalName == 'Kubernetes')].confidence")
                        .value(org.hamcrest.Matchers.hasItem("verified")));
    }

    @Test
    void evidenceWithoutASelfAssessmentLeavesTheSkillNotAssessed() throws Exception {
        var u = user(UUID.randomUUID());
        addEvidence(u, "repository", 0.9);
        mockMvc.perform(get("/api/v1/skills/" + KUBERNETES).with(u))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.capability").doesNotExist())
                .andExpect(jsonPath("$.data.confidence").doesNotExist());
    }
}
