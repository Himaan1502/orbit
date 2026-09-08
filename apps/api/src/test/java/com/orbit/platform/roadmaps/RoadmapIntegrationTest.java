package com.orbit.platform.roadmaps;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Roadmap catalog, derived progress and adoption through the API (Blueprint §11
 * / §20, ADR-021/022/023). The 13 roadmaps come from the V7 seed.
 */
@AutoConfigureMockMvc
class RoadmapIntegrationTest extends AbstractApiIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper json;

    // V5 seed skill ids (uuid5) — augmented, not replaced, by V7.
    private static final String KUBERNETES = "72382ac3-0de3-562e-8b97-ae94d4acbb04";
    private static final String DOCKER = "15cc01cc-010c-59ad-92ed-ebb5732302f2";

    private RequestPostProcessor user(UUID sub) {
        return jwt().jwt(j -> j.subject(sub.toString()).claim("email", sub + "@example.com")
                .claim("roles", java.util.List.of("USER")));
    }

    private JsonNode list(RequestPostProcessor u) throws Exception {
        return json.readTree(mockMvc.perform(get("/api/v1/roadmaps").with(u))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("data");
    }

    private String idByName(RequestPostProcessor u, String name) throws Exception {
        for (JsonNode r : list(u)) {
            if (r.path("name").asText().equals(name)) {
                return r.path("id").asText();
            }
        }
        throw new AssertionError("seed roadmap not found: " + name);
    }

    private void assess(RequestPostProcessor u, String skillId, double all) throws Exception {
        mockMvc.perform(post("/api/v1/skills/" + skillId + "/assessments").with(u)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(("{\"knowledge\":%s,\"practical\":%s,\"evidence\":%s,\"interview\":%s,\"production\":%s}")
                                .formatted(all, all, all, all, all)))
                .andExpect(status().isCreated());
    }

    @Test
    void theThirteenSeededRoadmapsAreServedTierOrdered() throws Exception {
        JsonNode data = list(user(UUID.randomUUID()));
        assertThat(data).hasSize(13);
        assertThat(data.get(0).path("priorityTier").asInt()).isEqualTo(1);      // P1 first
        assertThat(data.get(12).path("priorityTier").asInt()).isEqualTo(3);     // P3 last
        boolean sorted = true;
        for (int i = 1; i < data.size(); i++) {
            if (data.get(i - 1).path("priorityTier").asInt() > data.get(i).path("priorityTier").asInt()) {
                sorted = false;
            }
        }
        assertThat(sorted).as("roadmaps ordered by priority tier").isTrue();

        JsonNode mlops = null;
        for (JsonNode r : data) {
            if (r.path("name").asText().equals("MLOps")) {
                mlops = r;
            }
        }
        assertThat(mlops).isNotNull();
        assertThat(mlops.path("tier").asText()).isEqualTo("P1");
        assertThat(mlops.path("sourceName").asText()).isEqualTo("roadmap.sh");
        assertThat(mlops.path("sourceUrl").asText()).isEqualTo("https://roadmap.sh/mlops");
        assertThat(mlops.path("skillCount").asInt()).isGreaterThan(5);
    }

    @Test
    void ingestionAugmentedTheSkillCatalog() throws Exception {
        // V5 seeded 34 skills; V7 adds ~65 more (React, Solidity, RAG, …).
        String body = mockMvc.perform(get("/api/v1/skills").with(user(UUID.randomUUID())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode skills = json.readTree(body).path("data");
        assertThat(skills.size()).isGreaterThanOrEqualTo(95);
        assertThat(skills).anyMatch(s -> s.path("canonicalName").asText().equals("React"));
        assertThat(skills).anyMatch(s -> s.path("canonicalName").asText().equals("Retrieval-Augmented Generation (RAG)"));
    }

    @Test
    void roadmapDetailCarriesTheTopicTreeAndDerivedProgress() throws Exception {
        var u = user(UUID.randomUUID());
        String devops = idByName(u, "DevOps");

        mockMvc.perform(get("/api/v1/roadmaps/" + devops).with(u))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("DevOps"))
                .andExpect(jsonPath("$.data.coverage").value(0.0))                 // nothing assessed yet
                .andExpect(jsonPath("$.data.topics.length()").value(org.hamcrest.Matchers.greaterThan(5)))
                .andExpect(jsonPath("$.data.topics[?(@.name == 'Containers')]").exists())
                .andExpect(jsonPath("$.data.recommendedNext.length()").value(org.hamcrest.Matchers.greaterThan(0)));

        mockMvc.perform(get("/api/v1/roadmaps/" + devops + "/topics").with(u))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.name == 'Containers')].skills").exists());

        // assess the container skills -> the "Containers" topic and roadmap coverage move
        assess(u, DOCKER, 0.9);
        assess(u, KUBERNETES, 0.9);
        String after = mockMvc.perform(get("/api/v1/roadmaps/" + devops).with(u))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode d = json.readTree(after).path("data");
        assertThat(d.path("coverage").asDouble()).isGreaterThan(0.0);
        JsonNode containers = null;
        for (JsonNode t : d.path("topics")) {
            if (t.path("name").asText().equals("Containers")) {
                containers = t;
            }
        }
        assertThat(containers).isNotNull();
        assertThat(containers.path("status").asText()).isIn("in_progress", "covered");
    }

    @Test
    void unknownRoadmapIs404() throws Exception {
        mockMvc.perform(get("/api/v1/roadmaps/" + UUID.randomUUID()).with(user(UUID.randomUUID())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    void adoptionIsIdempotentOwnerScopedAnd404WhenAbsent() throws Exception {
        var alice = user(UUID.randomUUID());
        var bob = user(UUID.randomUUID());
        String mlops = idByName(alice, "MLOps");

        mockMvc.perform(put("/api/v1/roadmaps/" + mlops + "/adoption").with(alice)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"targetDate\":\"2026-12-31\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("active"))
                .andExpect(jsonPath("$.data.targetDate").value("2026-12-31"));

        // idempotent — second PUT still 200, still one row
        mockMvc.perform(put("/api/v1/roadmaps/" + mlops + "/adoption").with(alice)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());

        // Alice's library shows it adopted; Bob's does not
        assertThat(adopted(alice, "MLOps")).isTrue();
        assertThat(adopted(bob, "MLOps")).isFalse();

        // Bob cannot abandon what he never adopted
        mockMvc.perform(delete("/api/v1/roadmaps/" + mlops + "/adoption").with(bob))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/v1/roadmaps/" + mlops + "/adoption").with(alice))
                .andExpect(status().isNoContent());
        assertThat(adopted(alice, "MLOps")).isFalse();
    }

    @Test
    void adoptingAnUnknownRoadmapIs404() throws Exception {
        mockMvc.perform(put("/api/v1/roadmaps/" + UUID.randomUUID() + "/adoption").with(user(UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isNotFound());
    }

    private boolean adopted(RequestPostProcessor u, String roadmapName) throws Exception {
        for (JsonNode r : list(u)) {
            if (r.path("name").asText().equals(roadmapName)) {
                return r.path("adopted").asBoolean();
            }
        }
        throw new AssertionError("roadmap not found: " + roadmapName);
    }
}
