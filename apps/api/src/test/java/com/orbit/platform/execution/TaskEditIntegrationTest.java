package com.orbit.platform.execution;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code PATCH /api/v1/tasks/{id}} — editing every field after creation, the
 * clear-a-link flags, and that tagging a skill flips the Priority Engine's
 * skill/career factors from their neutral defaults to {@code computed}
 * (the M6 follow-up — the web UI now exposes this).
 */
@AutoConfigureMockMvc
class TaskEditIntegrationTest extends AbstractApiIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    // V5 seed
    private static final String DOCKER = "15cc01cc-010c-59ad-92ed-ebb5732302f2";

    private RequestPostProcessor user(UUID sub) {
        return jwt().jwt(j -> j.subject(sub.toString()).claim("email", sub + "@example.com")
                .claim("roles", List.of("USER")));
    }

    private String id(String body) throws Exception {
        return json.readTree(body).path("data").path("id").asText();
    }

    private String createTask(RequestPostProcessor u, String objectiveId) throws Exception {
        return id(mvc.perform(post("/api/v1/tasks").with(u).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"objectiveId\":\"" + objectiveId + "\",\"title\":\"draft\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    private String objective(RequestPostProcessor u) throws Exception {
        return id(mvc.perform(post("/api/v1/objectives").with(u).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"o\",\"priority\":3}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    @Test
    void everyDescriptiveFieldIsEditable() throws Exception {
        var u = user(UUID.randomUUID());
        String task = createTask(u, objective(u));

        mvc.perform(patch("/api/v1/tasks/" + task).with(u).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Write the reconcile loop","description":"handle the not-found case",
                                 "estimatedMinutes":90,"dueAt":"2026-10-01T17:00:00Z"}"""))
                .andExpect(status().isOk());

        mvc.perform(get("/api/v1/tasks/" + task).with(u))
                .andExpect(jsonPath("$.data.title").value("Write the reconcile loop"))
                .andExpect(jsonPath("$.data.description").value("handle the not-found case"))
                .andExpect(jsonPath("$.data.estimatedMinutes").value(90))
                .andExpect(jsonPath("$.data.dueAt").value("2026-10-01T17:00:00Z"));
    }

    @Test
    void taggingASkillFlipsTheSkillFactorsToComputedAndClearingReverts() throws Exception {
        var u = user(UUID.randomUUID());
        String task = createTask(u, objective(u));

        // baseline — no skill -> skillGap sits on its neutral default
        mvc.perform(get("/api/v1/tasks/" + task).with(u))
                .andExpect(jsonPath("$.data.priorityReason.factors.skillGap.source").value("neutral-default"));

        // tag Docker -> skillGap is now computed from the (assumed) gap
        mvc.perform(patch("/api/v1/tasks/" + task).with(u).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"skillId\":\"" + DOCKER + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.skillId").value(DOCKER))
                .andExpect(jsonPath("$.data.priorityReason.factors.skillGap.source").value("computed"));

        // clear it -> back to null and back to the neutral default
        mvc.perform(patch("/api/v1/tasks/" + task).with(u).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clearSkill\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.skillId").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.data.priorityReason.factors.skillGap.source").value("neutral-default"));
    }

    @Test
    void projectLinkCanBeSetAndCleared() throws Exception {
        var u = user(UUID.randomUUID());
        String obj = objective(u);
        String task = createTask(u, obj);
        String project = id(mvc.perform(post("/api/v1/projects").with(u).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Operator\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());

        mvc.perform(patch("/api/v1/tasks/" + task).with(u).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectId\":\"" + project + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.projectId").value(project));

        mvc.perform(patch("/api/v1/tasks/" + task).with(u).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clearProject\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.projectId").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void anotherUserCannotEditTheTask() throws Exception {
        var alice = user(UUID.randomUUID());
        var bob = user(UUID.randomUUID());
        String task = createTask(alice, objective(alice));

        mvc.perform(patch("/api/v1/tasks/" + task).with(bob).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"hijacked\"}"))
                .andExpect(status().isNotFound());
    }
}
