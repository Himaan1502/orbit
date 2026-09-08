package com.orbit.platform.projects;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Projects domain through the API (Blueprint §14/§20, ADR-024). CRUD, ownership
 * 404, objective + skill linkage, task-based progress.
 */
@AutoConfigureMockMvc
class ProjectIntegrationTest extends AbstractApiIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper json;

    // V5 seed skill ids (uuid5)
    private static final String KUBERNETES = "72382ac3-0de3-562e-8b97-ae94d4acbb04";
    private static final String TERRAFORM = "586a6973-cfc0-5ccb-8466-511d1dcdde39";

    private RequestPostProcessor user(UUID sub) {
        return jwt().jwt(j -> j.subject(sub.toString()).claim("email", sub + "@example.com")
                .claim("roles", java.util.List.of("USER")));
    }

    private String id(String body) throws Exception {
        return json.readTree(body).path("data").path("id").asText();
    }

    private String createObjective(RequestPostProcessor u) throws Exception {
        return id(mockMvc.perform(post("/api/v1/objectives").with(u).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Ship the platform\",\"priority\":4}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    @Test
    void createProjectWithSkillsAndAnObjectiveLink() throws Exception {
        var u = user(UUID.randomUUID());
        String obj = createObjective(u);

        String body = mockMvc.perform(post("/api/v1/projects").with(u).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"ORBIT Platform","description":"the thing",
                                 "objectiveId":"%s","repositoryUrl":"https://github.com/me/orbit",
                                 "skills":[
                                   {"skillId":"%s","expectedContribution":0.9},
                                   {"skillId":"%s","expectedContribution":0.6}]}"""
                                .formatted(obj, KUBERNETES, TERRAFORM)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("ORBIT Platform"))
                .andExpect(jsonPath("$.data.status").value("planned"))
                .andExpect(jsonPath("$.data.objectiveTitle").value("Ship the platform"))
                .andExpect(jsonPath("$.data.skillCount").value(2))
                .andExpect(jsonPath("$.data.skills[?(@.skillId == '" + KUBERNETES + "')].expectedContribution")
                        .value(0.9))
                .andExpect(jsonPath("$.data.skills[?(@.skillId == '" + KUBERNETES + "')].demonstratedContribution")
                        .value(0.0))
                .andReturn().getResponse().getContentAsString();

        String projectId = id(body);
        mockMvc.perform(get("/api/v1/projects").with(u))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(projectId));
    }

    @Test
    void statusTransitionsSetTheTimestamps() throws Exception {
        var u = user(UUID.randomUUID());
        String p = id(mockMvc.perform(post("/api/v1/projects").with(u).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Spike\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());

        mockMvc.perform(patch("/api/v1/projects/" + p).with(u).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"active\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("active"))
                .andExpect(jsonPath("$.data.startedAt").isNotEmpty())
                .andExpect(jsonPath("$.data.completedAt").isEmpty());

        mockMvc.perform(patch("/api/v1/projects/" + p).with(u).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"completed\"}"))
                .andExpect(jsonPath("$.data.completedAt").isNotEmpty());

        // re-open clears completed_at
        mockMvc.perform(patch("/api/v1/projects/" + p).with(u).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"active\"}"))
                .andExpect(jsonPath("$.data.completedAt").isEmpty());
    }

    @Test
    void taskProgressReflectsTheProjectsTasks() throws Exception {
        var u = user(UUID.randomUUID());
        String obj = createObjective(u);
        String p = id(mockMvc.perform(post("/api/v1/projects").with(u).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Build it\",\"objectiveId\":\"" + obj + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());

        String t1 = id(mockMvc.perform(post("/api/v1/tasks").with(u).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"objectiveId\":\"" + obj + "\",\"projectId\":\"" + p + "\",\"title\":\"A\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.projectId").value(p))
                .andReturn().getResponse().getContentAsString());
        mockMvc.perform(post("/api/v1/tasks").with(u).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"objectiveId\":\"" + obj + "\",\"projectId\":\"" + p + "\",\"title\":\"B\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/projects/" + p).with(u))
                .andExpect(jsonPath("$.data.taskProgress").value(0.0));

        mockMvc.perform(post("/api/v1/tasks/" + t1 + "/complete").with(u)).andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/projects/" + p).with(u))
                .andExpect(jsonPath("$.data.taskProgress").value(0.5));
    }

    @Test
    void ownershipIs404AndLinksAreValidated() throws Exception {
        var alice = user(UUID.randomUUID());
        var bob = user(UUID.randomUUID());
        String p = id(mockMvc.perform(post("/api/v1/projects").with(alice).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Alice only\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());

        mockMvc.perform(get("/api/v1/projects/" + p).with(bob)).andExpect(status().isNotFound());
        mockMvc.perform(patch("/api/v1/projects/" + p).with(bob).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"hijacked\"}")).andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/v1/projects/" + p).with(bob)).andExpect(status().isNotFound());

        // unknown skill / unknown objective -> 404
        mockMvc.perform(post("/api/v1/projects").with(alice).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"bad\",\"skills\":[{\"skillId\":\"" + UUID.randomUUID() + "\"}]}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/projects").with(alice).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"bad\",\"objectiveId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isNotFound());

        // bob can't attach a task to alice's project
        String bobObj = createObjective(bob);
        mockMvc.perform(post("/api/v1/tasks").with(bob).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"objectiveId\":\"" + bobObj + "\",\"projectId\":\"" + p + "\",\"title\":\"x\"}"))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/v1/projects/" + p).with(alice)).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/projects/" + p).with(alice)).andExpect(status().isNotFound());
    }

    @Test
    void validationRejectsABlankName() throws Exception {
        mockMvc.perform(post("/api/v1/projects").with(user(UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"  \"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }
}
