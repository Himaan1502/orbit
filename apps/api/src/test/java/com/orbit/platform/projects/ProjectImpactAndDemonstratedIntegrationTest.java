package com.orbit.platform.projects;

import com.fasterxml.jackson.databind.JsonNode;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ADR-026 §3–4 — {@code demonstrated_contribution} is gated on project
 * completion + evidence, and the Priority Engine's {@code projectImpact} factor
 * is computed for a task on a project (the final ADR-014 factor swap).
 */
@AutoConfigureMockMvc
class ProjectImpactAndDemonstratedIntegrationTest extends AbstractApiIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper json;

    // V5 seed — uuid5(NS_DNS, "orbit.skill:kubernetes")
    private static final String KUBERNETES = "72382ac3-0de3-562e-8b97-ae94d4acbb04";

    private RequestPostProcessor user(UUID sub) {
        return jwt().jwt(j -> j.subject(sub.toString()).claim("email", sub + "@example.com")
                .claim("roles", List.of("USER")));
    }

    private String id(String body) throws Exception {
        return json.readTree(body).path("data").path("id").asText();
    }

    @Test
    void projectImpactIsComputedForATaskOnAnActiveProjectAndNeutralWithout() throws Exception {
        var u = user(UUID.randomUUID());
        String obj = id(mockMvc.perform(post("/api/v1/objectives").with(u).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"o\",\"priority\":3}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        String project = id(mockMvc.perform(post("/api/v1/projects").with(u).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Operator\",\"skills\":[{\"skillId\":\"" + KUBERNETES
                                + "\",\"expectedContribution\":0.8}]}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        mockMvc.perform(patch("/api/v1/projects/" + project).with(u).contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"active\"}")).andExpect(status().isOk());

        // task ON the active project, carrying the project's skill -> projectImpact = expected (0.8), computed
        String onProject = mockMvc.perform(post("/api/v1/tasks").with(u).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"objectiveId\":\"" + obj + "\",\"projectId\":\"" + project + "\",\"skillId\":\""
                                + KUBERNETES + "\",\"title\":\"build operator\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        JsonNode f = json.readTree(onProject).path("data").path("priorityReason").path("factors");
        assertThat(f.path("projectImpact").path("source").asText()).isEqualTo("computed");
        assertThat(f.path("projectImpact").path("value").asDouble()).isEqualTo(0.8);

        // task with NO project -> neutral, unchanged from M2
        String offProject = mockMvc.perform(post("/api/v1/tasks").with(u).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"objectiveId\":\"" + obj + "\",\"title\":\"unrelated\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(offProject).path("data").path("priorityReason").path("factors")
                .path("projectImpact").path("source").asText()).isEqualTo("neutral-default");
    }

    @Test
    void demonstratedContributionOnlyMovesOnCompletionPlusEvidence() throws Exception {
        var u = user(UUID.randomUUID());
        String project = id(mockMvc.perform(post("/api/v1/projects").with(u).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Ship it\",\"skills\":[{\"skillId\":\"" + KUBERNETES
                                + "\",\"expectedContribution\":0.7}]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.skills[0].demonstratedContribution").value(0.0))
                .andReturn().getResponse().getContentAsString());

        // evidence linked to the skill AND the project, but project still open -> demonstrated stays 0
        mockMvc.perform(post("/api/v1/evidence").with(u).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"deployment\",\"title\":\"deployed\",\"skills\":[{\"skillId\":\"" + KUBERNETES
                                + "\",\"contributionWeight\":0.9}],\"projects\":[{\"projectId\":\"" + project + "\"}]}"))
                .andExpect(status().isCreated());
        mockMvc.perform(get("/api/v1/projects/" + project).with(u))
                .andExpect(jsonPath("$.data.skills[0].demonstratedContribution").value(0.0));

        // complete the project -> demonstrated = min(expected 0.7, evidence 0.9) = 0.7
        mockMvc.perform(patch("/api/v1/projects/" + project).with(u).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"completed\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.skills[0].demonstratedContribution").value(0.7));

        // re-open -> back to 0
        mockMvc.perform(patch("/api/v1/projects/" + project).with(u).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"active\"}"))
                .andExpect(jsonPath("$.data.skills[0].demonstratedContribution").value(0.0));
    }

    @Test
    void deletingTheEvidenceReversesTheDemonstratedContribution() throws Exception {
        var u = user(UUID.randomUUID());
        String project = id(mockMvc.perform(post("/api/v1/projects").with(u).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Prove it\",\"skills\":[{\"skillId\":\"" + KUBERNETES
                                + "\",\"expectedContribution\":0.6}]}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        String evidence = id(mockMvc.perform(post("/api/v1/evidence").with(u).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"benchmark\",\"title\":\"bench\",\"skills\":[{\"skillId\":\"" + KUBERNETES
                                + "\",\"contributionWeight\":0.8}],\"projects\":[{\"projectId\":\"" + project + "\"}]}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        mockMvc.perform(patch("/api/v1/projects/" + project).with(u).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"completed\"}"))
                .andExpect(jsonPath("$.data.skills[0].demonstratedContribution").value(0.6));

        mockMvc.perform(delete("/api/v1/evidence/" + evidence).with(u)).andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/projects/" + project).with(u))
                .andExpect(jsonPath("$.data.skills[0].demonstratedContribution").value(0.0))
                .andExpect(jsonPath("$.data.evidenceCount").value(0));
    }

    @Test
    void completedProjectWithEvidenceFeedsCareerReadinessProjectCoverage() throws Exception {
        var u = user(UUID.randomUUID());
        mockMvc.perform(post("/api/v1/career/targets").with(u).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"PE\",\"priority\":5,\"skills\":[{\"skillId\":\"" + KUBERNETES
                                + "\",\"importance\":1.0,\"interviewWeight\":0.5}]}"))
                .andExpect(status().isCreated());

        String before = mockMvc.perform(get("/api/v1/career/readiness").with(u))
                .andReturn().getResponse().getContentAsString();
        assertThat(componentValue(before, "projectCoverage")).isZero();

        String project = id(mockMvc.perform(post("/api/v1/projects").with(u).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"K8s platform\",\"skills\":[{\"skillId\":\"" + KUBERNETES
                                + "\",\"expectedContribution\":0.8}]}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        mockMvc.perform(post("/api/v1/evidence").with(u).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"repository\",\"title\":\"the repo\",\"skills\":[{\"skillId\":\"" + KUBERNETES
                                + "\",\"contributionWeight\":0.6}],\"projects\":[{\"projectId\":\"" + project + "\"}]}"))
                .andExpect(status().isCreated());
        mockMvc.perform(patch("/api/v1/projects/" + project).with(u).contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"completed\"}")).andExpect(status().isOk());

        String after = mockMvc.perform(get("/api/v1/career/readiness").with(u))
                .andExpect(jsonPath("$.data.projectGaps.length()").value(0))
                .andReturn().getResponse().getContentAsString();
        // demonstrated = min(0.8, 0.6) = 0.6 -> projectCoverage (single importance-weighted skill) = 0.6
        assertThat(componentValue(after, "projectCoverage")).isEqualTo(0.6);
    }

    private double componentValue(String readinessBody, String component) throws Exception {
        for (JsonNode c : json.readTree(readinessBody).path("data").path("componentBreakdown")) {
            if (c.path("component").asText().equals(component)) {
                return c.path("value").asDouble();
            }
        }
        throw new AssertionError("component not found: " + component);
    }
}
