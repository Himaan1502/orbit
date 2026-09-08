package com.orbit.platform.evidence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orbit.platform.support.AbstractApiIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Evidence domain through the API (Blueprint §13/§20, ADR-025). CRUD, ownership
 * 404, skill/project linking, filtering, and — critically — that creating
 * evidence never writes a {@code skill_assessments} row (ADR-025 §5).
 */
@AutoConfigureMockMvc
class EvidenceIntegrationTest extends AbstractApiIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    JdbcTemplate jdbc;

    private static final String KUBERNETES = "72382ac3-0de3-562e-8b97-ae94d4acbb04";

    private RequestPostProcessor user(UUID sub) {
        return jwt().jwt(j -> j.subject(sub.toString()).claim("email", sub + "@example.com")
                .claim("roles", java.util.List.of("USER")));
    }

    private String id(String body) throws Exception {
        return json.readTree(body).path("data").path("id").asText();
    }

    private String createProject(RequestPostProcessor u) throws Exception {
        return id(mockMvc.perform(post("/api/v1/projects").with(u).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Demo project\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    @Test
    void createEvidenceLinkedToASkillAndAProject() throws Exception {
        var u = user(UUID.randomUUID());
        String project = createProject(u);

        String body = mockMvc.perform(post("/api/v1/evidence").with(u).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"repository","title":"Prod k8s operator",
                                 "sourceUrl":"https://github.com/me/operator",
                                 "metadata":{"stars":42},
                                 "skills":[{"skillId":"%s","contributionWeight":0.8}],
                                 "projects":[{"projectId":"%s"}]}""".formatted(KUBERNETES, project)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.type").value("repository"))
                .andExpect(jsonPath("$.data.verificationStatus").value("self_attested"))
                .andExpect(jsonPath("$.data.metadata.stars").value(42))
                .andExpect(jsonPath("$.data.skills[0].skillId").value(KUBERNETES))
                .andExpect(jsonPath("$.data.skills[0].name").value("Kubernetes"))
                .andExpect(jsonPath("$.data.skills[0].contributionWeight").value(0.8))
                .andExpect(jsonPath("$.data.projects[0].projectId").value(project))
                .andReturn().getResponse().getContentAsString();

        String evId = id(body);

        // it shows up for the skill and for the project
        mockMvc.perform(get("/api/v1/skills/" + KUBERNETES + "/evidence").with(u))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(evId));
        mockMvc.perform(get("/api/v1/evidence").with(u).param("projectId", project))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));
        mockMvc.perform(get("/api/v1/evidence").with(u).param("type", "repository"))
                .andExpect(jsonPath("$.data.length()").value(1));
        mockMvc.perform(get("/api/v1/evidence").with(u).param("type", "document"))
                .andExpect(jsonPath("$.data.length()").value(0));

        // the project's evidence count reflects it
        mockMvc.perform(get("/api/v1/projects/" + project).with(u))
                .andExpect(jsonPath("$.data.evidenceCount").value(1));
    }

    @Test
    void creatingEvidenceNeverWritesASkillAssessmentRow() throws Exception {
        var u = user(UUID.randomUUID());
        long before = assessmentCount();

        mockMvc.perform(post("/api/v1/evidence").with(u).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"deployment\",\"title\":\"shipped it\",\"skills\":[{\"skillId\":\"" + KUBERNETES + "\"}]}"))
                .andExpect(status().isCreated());

        assertThat(assessmentCount()).as("ADR-025 §5 — evidence must not create an assessment").isEqualTo(before);
    }

    private long assessmentCount() {
        Long v = jdbc.queryForObject("SELECT count(*) FROM orbit.skill_assessments", Long.class);
        return v == null ? 0L : v;
    }

    @Test
    void unknownTypeSkillOrProjectIsRejected() throws Exception {
        var u = user(UUID.randomUUID());
        mockMvc.perform(post("/api/v1/evidence").with(u).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"nonsense\",\"title\":\"x\"}"))
                .andExpect(status().isUnprocessableEntity());
        mockMvc.perform(post("/api/v1/evidence").with(u).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"other\",\"title\":\"x\",\"skills\":[{\"skillId\":\"" + UUID.randomUUID() + "\"}]}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/evidence").with(u).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"other\",\"title\":\"x\",\"projects\":[{\"projectId\":\"" + UUID.randomUUID() + "\"}]}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void evidenceIsOwnerScopedAnd404ForEveryoneElse() throws Exception {
        var alice = user(UUID.randomUUID());
        var bob = user(UUID.randomUUID());
        String ev = id(mockMvc.perform(post("/api/v1/evidence").with(alice).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"document\",\"title\":\"Alice design doc\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());

        mockMvc.perform(get("/api/v1/evidence/" + ev).with(bob)).andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/v1/evidence/" + ev).with(bob)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/evidence").with(bob))
                .andExpect(jsonPath("$.data.length()").value(0));

        // bob cannot link his evidence to alice's project
        String aliceProject = createProject(alice);
        mockMvc.perform(post("/api/v1/evidence").with(bob).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"other\",\"title\":\"sneaky\",\"projects\":[{\"projectId\":\"" + aliceProject + "\"}]}"))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/v1/evidence/" + ev).with(alice)).andExpect(status().isNoContent());
    }
}
