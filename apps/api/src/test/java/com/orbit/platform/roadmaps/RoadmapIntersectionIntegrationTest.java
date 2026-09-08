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

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Roadmap Intersection (Blueprint §32, PRD §5, ADR-023 §2) — the signature view. */
@AutoConfigureMockMvc
class RoadmapIntersectionIntegrationTest extends AbstractApiIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper json;

    private RequestPostProcessor user(UUID sub) {
        return jwt().jwt(j -> j.subject(sub.toString()).claim("email", sub + "@example.com")
                .claim("roles", java.util.List.of("USER")));
    }

    private String idByName(RequestPostProcessor u, String name) throws Exception {
        JsonNode data = json.readTree(mockMvc.perform(get("/api/v1/roadmaps").with(u))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("data");
        for (JsonNode r : data) {
            if (r.path("name").asText().equals(name)) {
                return r.path("id").asText();
            }
        }
        throw new AssertionError("roadmap not found: " + name);
    }

    @Test
    void devopsMlopsAndDevsecopsShareTheContainerPlatformSkills() throws Exception {
        var u = user(UUID.randomUUID());
        String devops = idByName(u, "DevOps");
        String mlops = idByName(u, "MLOps");
        String devsecops = idByName(u, "DevSecOps");

        String body = mockMvc.perform(get("/api/v1/roadmaps/intersections").with(u)
                        .param("roadmapIds", devops, mlops, devsecops))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roadmaps.length()").value(3))
                .andReturn().getResponse().getContentAsString();
        JsonNode shared = json.readTree(body).path("data").path("sharedSkills");
        assertThat(shared.size()).isGreaterThan(0);

        List<String> names = new ArrayList<>();
        for (JsonNode s : shared) {
            names.add(s.path("name").asText());
            // every shared skill is in >= 2 of the selected roadmaps
            assertThat(s.path("roadmapCount").asInt()).isGreaterThanOrEqualTo(2);
            assertThat(s.path("recommendedAction").asText()).isNotBlank();
        }
        assertThat(names).contains("Kubernetes", "Docker", "CI/CD");

        // ranked: a skill in all 3 outranks one in 2
        int firstCount = shared.get(0).path("roadmapCount").asInt();
        int lastCount = shared.get(shared.size() - 1).path("roadmapCount").asInt();
        assertThat(firstCount).isGreaterThanOrEqualTo(lastCount);
    }

    @Test
    void fewerThanTwoRoadmapsIsRejected() throws Exception {
        var u = user(UUID.randomUUID());
        String devops = idByName(u, "DevOps");
        mockMvc.perform(get("/api/v1/roadmaps/intersections").with(u).param("roadmapIds", devops))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"));
        // no ids and nothing adopted -> also a 400
        mockMvc.perform(get("/api/v1/roadmaps/intersections").with(u))
                .andExpect(status().isBadRequest());
    }

    @Test
    void anUnknownRoadmapIdIsRejected() throws Exception {
        var u = user(UUID.randomUUID());
        String devops = idByName(u, "DevOps");
        mockMvc.perform(get("/api/v1/roadmaps/intersections").with(u)
                        .param("roadmapIds", devops, UUID.randomUUID().toString()))
                .andExpect(status().isNotFound());
    }

    @Test
    void withNoIdsTheIntersectionUsesTheCallersAdoptedRoadmaps() throws Exception {
        var u = user(UUID.randomUUID());
        String frontend = idByName(u, "Frontend");
        String fullStack = idByName(u, "Full Stack");
        mockMvc.perform(put("/api/v1/roadmaps/" + frontend + "/adoption").with(u)
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isOk());
        mockMvc.perform(put("/api/v1/roadmaps/" + fullStack + "/adoption").with(u)
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isOk());

        String body = mockMvc.perform(get("/api/v1/roadmaps/intersections").with(u))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roadmaps.length()").value(2))
                .andReturn().getResponse().getContentAsString();
        JsonNode shared = json.readTree(body).path("data").path("sharedSkills");
        List<String> names = new ArrayList<>();
        shared.forEach(s -> names.add(s.path("name").asText()));
        assertThat(names).contains("React");   // both Frontend and Full Stack map React
    }
}
