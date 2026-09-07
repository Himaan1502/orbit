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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Milestone 3 objective 5 — the Priority Engine factor swap (ADR-014). With a
 * skill on the task and a career target on its objective, the four career/skill
 * factors are <em>computed</em> from real data instead of the Milestone 2
 * neutral / proxy defaults. The formula, the weight set and its fingerprint are
 * unchanged; {@code projectImpact} stays neutral until the Projects domain
 * exists.
 */
@AutoConfigureMockMvc
class PriorityFactorSwapIntegrationTest extends AbstractApiIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper json;

    // V5 seed — uuid5(NS_DNS, "orbit.skill:<name>")
    private static final String KUBERNETES = "72382ac3-0de3-562e-8b97-ae94d4acbb04";
    private static final String OBSERVABILITY = "325ba16e-f3a3-5cdd-b903-8f363be4ddc4";
    private static final String DOCKER = "15cc01cc-010c-59ad-92ed-ebb5732302f2";

    private RequestPostProcessor user(UUID sub) {
        return jwt().jwt(j -> j.subject(sub.toString()).claim("email", sub + "@example.com")
                .claim("roles", java.util.List.of("USER")));
    }

    private String dataId(String body) throws Exception {
        return json.readTree(body).path("data").path("id").asText();
    }

    private JsonNode factors(String taskBody) throws Exception {
        return json.readTree(taskBody).path("data").path("priorityReason").path("factors");
    }

    @Test
    void realSignalsReplaceTheNeutralDefaultsWithoutChangingTheFormula() throws Exception {
        var u = user(UUID.randomUUID());

        // A career target that weights Docker's downstream skills.
        String target = dataId(mockMvc.perform(post("/api/v1/career/targets").with(u)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"name":"Platform Engineer","priority":5,"skills":[
                                  {"skillId":"%s","importance":0.9,"interviewWeight":0.7},
                                  {"skillId":"%s","importance":0.6,"interviewWeight":0.3}]}"""
                                .formatted(KUBERNETES, OBSERVABILITY)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());

        // Baseline: objective with no career target, task with no skill -> M2 behaviour.
        String plainObjective = dataId(mockMvc.perform(post("/api/v1/objectives").with(u)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Plain\",\"priority\":3}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        String plainTask = mockMvc.perform(post("/api/v1/tasks").with(u).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"objectiveId\":\"" + plainObjective + "\",\"title\":\"no skill\",\"estimatedMinutes\":30}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        JsonNode plain = factors(plainTask);

        assertThat(plain.path("careerRelevance").path("source").asText()).isEqualTo("proxy");
        assertThat(plain.path("skillGap").path("source").asText()).isEqualTo("neutral-default");
        assertThat(plain.path("dependencyImpact").path("source").asText()).isEqualTo("neutral-default");
        assertThat(plain.path("interviewValue").path("source").asText()).isEqualTo("neutral-default");
        String plainFingerprint = json.readTree(plainTask).path("data").path("priorityReason")
                .path("weightsFingerprint").asText();

        // Swapped: objective linked to the target, task carries Docker (a prerequisite of Kubernetes).
        String linkedObjective = dataId(mockMvc.perform(post("/api/v1/objectives").with(u)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Toward PE\",\"priority\":3,\"careerTargetId\":\"" + target + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        String skilledTask = mockMvc.perform(post("/api/v1/tasks").with(u).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"objectiveId\":\"" + linkedObjective + "\",\"skillId\":\"" + DOCKER
                                + "\",\"title\":\"learn docker\",\"estimatedMinutes\":30}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        JsonNode swapped = factors(skilledTask);

        // Docker isn't itself a weighted target skill, but it sits on the path to one:
        // careerRelevance, skillGap and dependencyImpact are all computed from real data.
        assertThat(swapped.path("careerRelevance").path("source").asText()).isEqualTo("computed");
        assertThat(swapped.path("skillGap").path("source").asText()).isEqualTo("computed");
        assertThat(swapped.path("dependencyImpact").path("source").asText()).isEqualTo("computed");
        // interviewValue is defined per target skill; Docker has no interview weight here -> stays neutral.
        assertThat(swapped.path("interviewValue").path("source").asText()).isEqualTo("neutral-default");

        // Docker isn't itself a target skill -> careerRelevance is the mean target importance (0.75).
        assertThat(swapped.path("careerRelevance").path("value").asDouble()).isCloseTo(0.75, org.assertj.core.data.Offset.offset(1e-6));
        // Docker unblocks Kubernetes (1 of the 2 target skills) -> dependencyImpact 0.5.
        assertThat(swapped.path("dependencyImpact").path("value").asDouble()).isCloseTo(0.5, org.assertj.core.data.Offset.offset(1e-6));
        // Docker attached but never assessed -> the assumed-gap constant.
        assertThat(swapped.path("skillGap").path("value").asDouble()).isCloseTo(0.7, org.assertj.core.data.Offset.offset(1e-6));

        // projectImpact is still neutral; the weight set is byte-identical.
        assertThat(swapped.path("projectImpact").path("source").asText()).isEqualTo("neutral-default");
        assertThat(json.readTree(skilledTask).path("data").path("priorityReason").path("weightsFingerprint").asText())
                .isEqualTo(plainFingerprint);

        // Same factor keys, same order, in both.
        assertThat(fieldNames(swapped)).containsExactlyElementsOf(fieldNames(plain));
    }

    private static java.util.List<String> fieldNames(JsonNode node) {
        var names = new java.util.ArrayList<String>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }

    @Test
    void anAssessedSkillDrivesSkillGapFromTheRealScore() throws Exception {
        var u = user(UUID.randomUUID());
        String target = dataId(mockMvc.perform(post("/api/v1/career/targets").with(u)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"name":"PE","priority":5,"skills":[
                                  {"skillId":"%s","importance":0.8,"interviewWeight":0.5}]}""".formatted(KUBERNETES)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());

        mockMvc.perform(post("/api/v1/skills/" + KUBERNETES + "/assessments").with(u)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"knowledge":0.8,"practical":0.8,"evidence":0.8,"interview":0.8,"production":0.8}"""))
                .andExpect(status().isCreated());

        String objective = dataId(mockMvc.perform(post("/api/v1/objectives").with(u)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"o\",\"careerTargetId\":\"" + target + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        String task = mockMvc.perform(post("/api/v1/tasks").with(u).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"objectiveId\":\"" + objective + "\",\"skillId\":\"" + KUBERNETES
                                + "\",\"title\":\"deepen k8s\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();

        JsonNode f = factors(task);
        // overall = 0.8 across the board -> gap = 1 - 0.8 = 0.2
        assertThat(f.path("skillGap").path("value").asDouble()).isCloseTo(0.2, org.assertj.core.data.Offset.offset(1e-6));
        assertThat(f.path("skillGap").path("source").asText()).isEqualTo("computed");
        // Kubernetes IS the target skill -> careerRelevance is its importance exactly.
        assertThat(f.path("careerRelevance").path("value").asDouble()).isCloseTo(0.8, org.assertj.core.data.Offset.offset(1e-6));
        assertThat(f.path("interviewValue").path("value").asDouble()).isCloseTo(0.5, org.assertj.core.data.Offset.offset(1e-6));
    }
}
