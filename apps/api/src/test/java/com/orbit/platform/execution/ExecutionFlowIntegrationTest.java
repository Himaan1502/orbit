package com.orbit.platform.execution;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orbit.platform.support.AbstractApiIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The Milestone 2 critical path through the API: objective → task → complete →
 * progress, and objective → tasks → generate daily plan. Plus task ownership
 * (404) and priority-reason population.
 */
@AutoConfigureMockMvc
class ExecutionFlowIntegrationTest extends AbstractApiIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper json;

    private RequestPostProcessor user(UUID sub) {
        return jwt().jwt(j -> j.subject(sub.toString()).claim("email", sub + "@example.com")
                .claim("roles", java.util.List.of("USER")));
    }

    private String id(String body) throws Exception {
        return json.readTree(body).path("data").path("id").asText();
    }

    private String createObjective(RequestPostProcessor u, int priority) throws Exception {
        return id(mockMvc.perform(post("/api/v1/objectives").with(u).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Ship the platform\",\"priority\":" + priority + "}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    private String createTask(RequestPostProcessor u, String objectiveId, String title, int minutes)
            throws Exception {
        return id(mockMvc.perform(post("/api/v1/tasks").with(u).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"objectiveId\":\"" + objectiveId + "\",\"title\":\"" + title
                                + "\",\"estimatedMinutes\":" + minutes + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("todo"))
                .andExpect(jsonPath("$.data.priorityScore").isNumber())
                .andExpect(jsonPath("$.data.priorityReason.engineVersion").value("v1"))
                .andExpect(jsonPath("$.data.priorityReason.factors.urgency.source").value("computed"))
                .andReturn().getResponse().getContentAsString());
    }

    @Test
    void completingTasksMovesObjectiveProgress() throws Exception {
        var u = user(UUID.randomUUID());
        String obj = createObjective(u, 4);
        String t1 = createTask(u, obj, "Design", 60);
        String t2 = createTask(u, obj, "Build", 90);

        mockMvc.perform(get("/api/v1/objectives/" + obj).with(u))
                .andExpect(jsonPath("$.data.progress").value(0));

        mockMvc.perform(post("/api/v1/tasks/" + t1 + "/complete").with(u))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("done"))
                .andExpect(jsonPath("$.data.completedAt").isNotEmpty());

        mockMvc.perform(get("/api/v1/objectives/" + obj).with(u))
                .andExpect(jsonPath("$.data.progress").value(0.5));   // 1 of 2 tasks resolved

        mockMvc.perform(post("/api/v1/tasks/" + t2 + "/complete").with(u)).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/objectives/" + obj).with(u))
                .andExpect(jsonPath("$.data.progress").value(1.0))
                .andExpect(jsonPath("$.data.status").value("completed"));
    }

    @Test
    void generateDailyPlanIsIdempotentAndReflectsPriority() throws Exception {
        var u = user(UUID.randomUUID());
        String obj = createObjective(u, 5);
        createTask(u, obj, "High value small", 30);
        createTask(u, obj, "Also small", 30);
        createTask(u, obj, "Huge", 400);

        String planBody = mockMvc.perform(post("/api/v1/daily-plans/generate").with(u)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"availableMinutes\":75}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.availableMinutes").value(75))
                .andExpect(jsonPath("$.data.generatedBy").value("system"))
                .andReturn().getResponse().getContentAsString();
        JsonNode plan = json.readTree(planBody).path("data");
        String planId = plan.path("id").asText();
        assertThat(plan.path("tasks").size()).isGreaterThanOrEqualTo(2);
        assertThat(plan.path("allocatedMinutes").asInt()).isLessThanOrEqualTo(75);

        // Idempotent: same date, no force -> same plan id, unchanged
        mockMvc.perform(post("/api/v1/daily-plans/generate").with(u)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(planId));

        // Retrievable by date
        mockMvc.perform(get("/api/v1/daily-plans/" + LocalDate.now()).with(u))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(planId));

        // Regenerate works
        mockMvc.perform(post("/api/v1/daily-plans/" + planId + "/regenerate").with(u))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(planId));
    }

    @Test
    void taskOwnershipIsEnforcedWith404() throws Exception {
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        var au = user(alice);
        String obj = createObjective(au, 3);
        String task = createTask(au, obj, "Alice only", 45);

        var bu = user(bob);
        mockMvc.perform(get("/api/v1/tasks/" + task).with(bu)).andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/tasks/" + task + "/complete").with(bu)).andExpect(status().isNotFound());
        // Bob cannot attach a task to Alice's objective either
        mockMvc.perform(post("/api/v1/tasks").with(bu).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"objectiveId\":\"" + obj + "\",\"title\":\"sneaky\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/tasks").with(bu))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void getPlanForADateWithNoPlanIs404() throws Exception {
        mockMvc.perform(get("/api/v1/daily-plans/2020-01-01").with(user(UUID.randomUUID())))
                .andExpect(status().isNotFound());
    }
}
