package com.orbit.platform.career;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Career targets + readiness through the API (Blueprint §8/§24, ADR-018). Seed
 * skill ids are the deterministic {@code uuid5} values from V5 — stable by
 * construction (ADR-020 §2).
 */
@AutoConfigureMockMvc
class CareerIntegrationTest extends AbstractApiIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper json;

    // V5 seed — uuid5(NS_DNS, "orbit.skill:<name>")
    private static final String KUBERNETES = "72382ac3-0de3-562e-8b97-ae94d4acbb04";
    private static final String OBSERVABILITY = "325ba16e-f3a3-5cdd-b903-8f363be4ddc4";

    private RequestPostProcessor user(UUID sub) {
        return jwt().jwt(j -> j.subject(sub.toString()).claim("email", sub + "@example.com")
                .claim("roles", java.util.List.of("USER")));
    }

    private String id(String body) throws Exception {
        return json.readTree(body).path("data").path("id").asText();
    }

    private String createTarget(RequestPostProcessor u, int priority) throws Exception {
        return id(mockMvc.perform(post("/api/v1/career/targets").with(u).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Platform Engineer","roleFamily":"Infrastructure","priority":%d,
                                 "skills":[
                                   {"skillId":"%s","importance":0.9,"interviewWeight":0.6},
                                   {"skillId":"%s","importance":0.6,"interviewWeight":0.4}]}"""
                                .formatted(priority, KUBERNETES, OBSERVABILITY)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("Platform Engineer"))
                .andExpect(jsonPath("$.data.status").value("active"))
                .andExpect(jsonPath("$.data.skills.length()").value(2))
                .andReturn().getResponse().getContentAsString());
    }

    @Test
    void aTargetCarriesItsWeightedSkillSet() throws Exception {
        var u = user(UUID.randomUUID());
        String target = createTarget(u, 4);

        mockMvc.perform(get("/api/v1/career/targets/" + target).with(u))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.skillCount").value(2))
                .andExpect(jsonPath("$.data.skills[?(@.skillId == '" + KUBERNETES + "')].importance").value(0.9));

        // the list view carries each target's weighted skill set too
        mockMvc.perform(get("/api/v1/career/targets").with(u))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].skillCount").value(2))
                .andExpect(jsonPath("$.data[0].skills.length()").value(2));
    }

    @Test
    void targetsAreOwnerScopedAnd404ForEveryoneElse() throws Exception {
        var alice = user(UUID.randomUUID());
        var bob = user(UUID.randomUUID());
        String target = createTarget(alice, 3);

        mockMvc.perform(get("/api/v1/career/targets/" + target).with(bob))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
        mockMvc.perform(patch("/api/v1/career/targets/" + target).with(bob)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"name":"Hijacked"}"""))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/v1/career/targets/" + target).with(bob))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/career/targets").with(bob))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void readinessBlendsAssessedCapabilityAndFlagsUnassessedSkillsAsGaps() throws Exception {
        var u = user(UUID.randomUUID());
        createTarget(u, 5);

        // assess only Kubernetes; Observability stays a gap
        mockMvc.perform(post("/api/v1/skills/" + KUBERNETES + "/assessments").with(u)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"knowledge":0.7,"practical":0.6,"evidence":0.5,"interview":0.6,"production":0.4}"""))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/career/readiness").with(u))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.targetName").value("Platform Engineer"))
                .andExpect(jsonPath("$.data.confidence").value("self-reported"))
                .andExpect(jsonPath("$.data.assessedSkillCount").value(1))
                .andExpect(jsonPath("$.data.targetSkillCount").value(2))
                .andExpect(jsonPath("$.data.overall").value(org.hamcrest.Matchers.greaterThan(0.0)))
                .andExpect(jsonPath("$.data.componentBreakdown[?(@.component == 'evidenceCoverage')].status")
                        .value(org.hamcrest.Matchers.hasItem("not-tracked-until-evidence-engine")))
                .andExpect(jsonPath("$.data.trend").value(org.hamcrest.Matchers.nullValue()));

        mockMvc.perform(get("/api/v1/career/gaps").with(u))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.criticalGaps[0].skill").value("Observability"))
                .andExpect(jsonPath("$.data.criticalGaps[0].gap").value(org.hamcrest.Matchers.closeTo(0.6, 1e-6)));
    }

    @Test
    void readinessIs404UntilACareerTargetExists() throws Exception {
        mockMvc.perform(get("/api/v1/career/readiness").with(user(UUID.randomUUID())))
                .andExpect(status().isNotFound());
    }

    @Test
    void readinessIsReproducibleAcrossCalls() throws Exception {
        var u = user(UUID.randomUUID());
        createTarget(u, 5);
        mockMvc.perform(post("/api/v1/skills/" + KUBERNETES + "/assessments").with(u)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"knowledge":0.7,"practical":0.6,"evidence":0.5,"interview":0.6,"production":0.4}"""))
                .andExpect(status().isCreated());

        String first = mockMvc.perform(get("/api/v1/career/readiness").with(u))
                .andReturn().getResponse().getContentAsString();
        String second = mockMvc.perform(get("/api/v1/career/readiness").with(u))
                .andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(first).path("data").path("overall"))
                .isEqualTo(json.readTree(second).path("data").path("overall"));
    }

    @Test
    void anObjectiveCanOnlyLinkToACareerTargetTheCallerOwns() throws Exception {
        var alice = user(UUID.randomUUID());
        var bob = user(UUID.randomUUID());
        String aliceTarget = createTarget(alice, 3);

        // Alice links her objective — fine
        mockMvc.perform(post("/api/v1/objectives").with(alice).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Toward Platform Engineer\",\"careerTargetId\":\"" + aliceTarget + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.careerTargetId").value(aliceTarget));

        // Bob cannot link to Alice's target — 404, no existence disclosure
        mockMvc.perform(post("/api/v1/objectives").with(bob).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Sneaky\",\"careerTargetId\":\"" + aliceTarget + "\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void unknownSkillInATargetIs404() throws Exception {
        var u = user(UUID.randomUUID());
        mockMvc.perform(post("/api/v1/career/targets").with(u).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Bad\",\"skills\":[{\"skillId\":\"" + UUID.randomUUID() + "\"}]}"))
                .andExpect(status().isNotFound());
    }
}
