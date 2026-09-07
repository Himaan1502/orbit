package com.orbit.platform.objectives;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class MilestoneIntegrationTest extends AbstractApiIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper json;

    private RequestPostProcessor user(UUID sub) {
        return jwt().jwt(j -> j.subject(sub.toString()).claim("email", sub + "@example.com")
                .claim("roles", java.util.List.of("USER")));
    }

    private String createObjective(RequestPostProcessor u, int priority) throws Exception {
        String body = mockMvc.perform(post("/api/v1/objectives").with(u)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Learn Kubernetes\",\"priority\":" + priority + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.priority").value(priority))
                .andExpect(jsonPath("$.data.progress").value(0))
                .andExpect(jsonPath("$.data.status").value("active"))
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).path("data").path("id").asText();
    }

    @Test
    void milestoneCompletionRollsUpToObjectiveProgressAndAutoCompletes() throws Exception {
        UUID alice = UUID.randomUUID();
        var u = user(alice);
        String objId = createObjective(u, 4);

        String m1 = milestoneId(mockMvc.perform(post("/api/v1/objectives/" + objId + "/milestones").with(u)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Networking\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        String m2 = milestoneId(mockMvc.perform(post("/api/v1/objectives/" + objId + "/milestones").with(u)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Storage\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());

        // Two milestones, none complete -> progress 0
        mockMvc.perform(get("/api/v1/objectives/" + objId).with(u))
                .andExpect(jsonPath("$.data.progress").value(0));

        mockMvc.perform(patch("/api/v1/objectives/" + objId + "/milestones/" + m1).with(u)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"completed\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/objectives/" + objId).with(u))
                .andExpect(jsonPath("$.data.progress").value(0.5));

        mockMvc.perform(patch("/api/v1/objectives/" + objId + "/milestones/" + m2).with(u)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"completed\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/objectives/" + objId).with(u))
                .andExpect(jsonPath("$.data.progress").value(1.0))
                .andExpect(jsonPath("$.data.status").value("completed"));
    }

    @Test
    void aSecondUserCannotSeeOrModifyMilestones() throws Exception {
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        var au = user(alice);
        String objId = createObjective(au, 3);
        String mId = milestoneId(mockMvc.perform(post("/api/v1/objectives/" + objId + "/milestones").with(au)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Secret milestone\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());

        var bu = user(bob);
        mockMvc.perform(get("/api/v1/objectives/" + objId + "/milestones").with(bu))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/objectives/" + objId + "/milestones/" + mId).with(bu))
                .andExpect(status().isNotFound());
        mockMvc.perform(patch("/api/v1/objectives/" + objId + "/milestones/" + mId).with(bu)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Hijacked\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void objectiveStatusCannotBeSetToCompletedManually() throws Exception {
        var u = user(UUID.randomUUID());
        String objId = createObjective(u, 3);
        mockMvc.perform(patch("/api/v1/objectives/" + objId).with(u)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"completed\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(patch("/api/v1/objectives/" + objId).with(u)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"archived\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("archived"));
    }

    private String milestoneId(String responseBody) throws Exception {
        return json.readTree(responseBody).path("data").path("id").asText();
    }
}
