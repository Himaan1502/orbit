package com.orbit.platform.dashboard;

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

@AutoConfigureMockMvc
class DashboardIntegrationTest extends AbstractApiIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper json;

    private RequestPostProcessor user(UUID sub) {
        return jwt().jwt(j -> j.subject(sub.toString()).claim("email", sub + "@example.com")
                .claim("name", "Dash Tester").claim("roles", java.util.List.of("USER")));
    }

    @Test
    void dashboardAggregatesObjectivesTasksAndTodaysPlan() throws Exception {
        var u = user(UUID.randomUUID());

        // Empty dashboard for a brand-new user
        mockMvc.perform(get("/api/v1/dashboard").with(u))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.displayName").value("Dash Tester"))
                .andExpect(jsonPath("$.data.objectiveHealth.active").value(0))
                .andExpect(jsonPath("$.data.primaryObjective").doesNotExist())
                .andExpect(jsonPath("$.data.today.planExists").value(false));

        String obj = json.readTree(mockMvc.perform(post("/api/v1/objectives").with(u)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Primary goal\",\"priority\":5}"))
                .andReturn().getResponse().getContentAsString()).path("data").path("id").asText();
        mockMvc.perform(post("/api/v1/tasks").with(u).contentType(MediaType.APPLICATION_JSON)
                .content("{\"objectiveId\":\"" + obj + "\",\"title\":\"Do the thing\",\"estimatedMinutes\":30}"));
        mockMvc.perform(post("/api/v1/daily-plans/generate").with(u)
                .contentType(MediaType.APPLICATION_JSON).content("{}"));

        mockMvc.perform(get("/api/v1/dashboard").with(u))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.objectiveHealth.active").value(1))
                .andExpect(jsonPath("$.data.primaryObjective.title").value("Primary goal"))
                .andExpect(jsonPath("$.data.nextBestAction.title").value("Do the thing"))
                .andExpect(jsonPath("$.data.nextBestAction.why.factors.urgency").exists())
                .andExpect(jsonPath("$.data.today.planExists").value(true))
                .andExpect(jsonPath("$.data.today.taskCount").value(1))
                .andExpect(jsonPath("$.data.executionHealth.openTasks").value(1));
    }

    @Test
    void dashboardRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard")).andExpect(status().isUnauthorized());
    }

    @Test
    void preferencesUpdateChangesTheDailyBudget() throws Exception {
        var u = user(UUID.randomUUID());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/api/v1/me/preferences").with(u).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"availableDailyMinutes\":200,\"timezone\":\"Europe/London\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.availableDailyMinutes").value(200))
                .andExpect(jsonPath("$.data.timezone").value("Europe/London"));
    }
}
