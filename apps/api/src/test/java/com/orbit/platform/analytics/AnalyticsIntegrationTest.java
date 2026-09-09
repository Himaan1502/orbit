package com.orbit.platform.analytics;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orbit.platform.support.AbstractApiIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** {@code /api/v1/analytics} — derived-at-read-time series, ownership-scoped, empty-is-explicit (ADR-027). */
@AutoConfigureMockMvc
class AnalyticsIntegrationTest extends AbstractApiIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    JdbcTemplate jdbc;

    private static final String KUBERNETES = "72382ac3-0de3-562e-8b97-ae94d4acbb04";
    private static final String DOCKER = "15cc01cc-010c-59ad-92ed-ebb5732302f2";

    private RequestPostProcessor user(UUID sub) {
        return jwt().jwt(j -> j.subject(sub.toString()).claim("email", sub + "@example.com")
                .claim("roles", List.of("USER")));
    }

    private String id(String body) throws Exception {
        return json.readTree(body).path("data").path("id").asText();
    }

    @Test
    void executionAnalyticsBucketsCompletionsAndSurfacesBlockers() throws Exception {
        var u = user(UUID.randomUUID());
        String obj = id(mvc.perform(post("/api/v1/objectives").with(u).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"o\",\"priority\":3}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());

        String t1 = id(mvc.perform(post("/api/v1/tasks").with(u).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"objectiveId\":\"" + obj + "\",\"title\":\"done one\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        String t2 = id(mvc.perform(post("/api/v1/tasks").with(u).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"objectiveId\":\"" + obj + "\",\"title\":\"blocked one\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());

        mvc.perform(post("/api/v1/tasks/" + t1 + "/complete").with(u)).andExpect(status().isOk());
        mvc.perform(patch("/api/v1/tasks/" + t2).with(u).contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"blocked\"}")).andExpect(status().isOk());

        String body = mvc.perform(get("/api/v1/analytics/execution").with(u))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ok"))
                .andExpect(jsonPath("$.data.statusCounts.done").value(1))
                .andExpect(jsonPath("$.data.statusCounts.blocked").value(1))
                .andExpect(jsonPath("$.data.blockers.length()").value(1))
                .andExpect(jsonPath("$.data.blockers[0].taskId").value(t2))
                // completing t1 rolled the objective's progress -> an objective_progress_events row (ADR-027 §4)
                .andExpect(jsonPath("$.data.objectiveProgress[0].objectiveId").value(obj))
                .andExpect(jsonPath("$.data.objectiveProgress[0].points.length()").value(
                        org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
                .andReturn().getResponse().getContentAsString();

        JsonNode weekly = json.readTree(body).path("data").path("weekly");
        assertThat(weekly.isArray()).isTrue();
        int completed = 0;
        for (JsonNode w : weekly) {
            completed += w.path("completed").asInt();
        }
        assertThat(completed).isEqualTo(1);

        // un-block -> blocked_since cleared, blocker gone
        mvc.perform(patch("/api/v1/tasks/" + t2).with(u).contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"todo\"}")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/analytics/execution").with(u))
                .andExpect(jsonPath("$.data.blockers.length()").value(0));
    }

    @Test
    void executionAnalyticsIsExplicitlyEmptyForANewUser() throws Exception {
        mvc.perform(get("/api/v1/analytics/execution").with(user(UUID.randomUUID())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("no-data-yet"))
                .andExpect(jsonPath("$.data.weekly.length()").value(0));
    }

    @Test
    void careerAnalyticsReturnsAReadinessTrendThatMovesWithAssessments() throws Exception {
        var u = user(UUID.randomUUID());
        mvc.perform(post("/api/v1/career/targets").with(u).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"PE\",\"priority\":5,\"skills\":[{\"skillId\":\"" + KUBERNETES
                                + "\",\"importance\":1.0,\"interviewWeight\":0.5}]}"))
                .andExpect(status().isCreated());

        // backdate one assessment so the trend has two distinct instants
        String meBody = mvc.perform(get("/api/v1/me").with(u)).andReturn().getResponse().getContentAsString();
        UUID sub = UUID.fromString(json.readTree(meBody).path("data").path("id").asText());
        jdbc.update("""
                insert into orbit.skill_assessments
                  (id, user_id, skill_id, knowledge_score, practical_score, evidence_score,
                   interview_score, production_score, overall_score, assessment_source, assessed_at, created_at)
                values (?, ?, ?, 0.3,0.3,0.3,0.3,0.3, 0.3, 'self', now() - interval '20 days', now() - interval '20 days')
                """, UUID.randomUUID(), sub, UUID.fromString(KUBERNETES));

        mvc.perform(post("/api/v1/skills/" + KUBERNETES + "/assessments").with(u)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"knowledge\":0.9,\"practical\":0.9,\"evidence\":0.9,\"interview\":0.9,\"production\":0.9}"))
                .andExpect(status().isCreated());

        String body = mvc.perform(get("/api/v1/analytics/career").with(u))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ok"))
                .andReturn().getResponse().getContentAsString();

        JsonNode trend = json.readTree(body).path("data").path("readinessTrend");
        assertThat(trend.size()).isGreaterThanOrEqualTo(2);
        double first = trend.get(0).path("overall").asDouble();
        double last = trend.get(trend.size() - 1).path("overall").asDouble();
        assertThat(last).isGreaterThan(first);   // readiness rose as capability rose
    }

    @Test
    void skillsAnalyticsReturnsProgressionAndBottlenecks() throws Exception {
        var u = user(UUID.randomUUID());
        // assess Kubernetes (which has Docker as a prerequisite) but not Docker
        mvc.perform(post("/api/v1/skills/" + KUBERNETES + "/assessments").with(u)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"knowledge\":0.6,\"practical\":0.6,\"evidence\":0.6,\"interview\":0.6,\"production\":0.6}"))
                .andExpect(status().isCreated());

        mvc.perform(get("/api/v1/analytics/skills").with(u))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ok"))
                .andExpect(jsonPath("$.data.progression[0].skillId").value(KUBERNETES))
                .andExpect(jsonPath("$.data.bottlenecks[?(@.name == 'Docker')]").exists());
    }

    @Test
    void skillsAnalyticsIsExplicitlyEmptyForANewUser() throws Exception {
        mvc.perform(get("/api/v1/analytics/skills").with(user(UUID.randomUUID())))
                .andExpect(jsonPath("$.data.status").value("no-data-yet"));
    }

    @Test
    void oneUsersAnalyticsNeverIncludeAnothers() throws Exception {
        var alice = user(UUID.randomUUID());
        var bob = user(UUID.randomUUID());
        String obj = id(mvc.perform(post("/api/v1/objectives").with(alice).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"a\"}")).andReturn().getResponse().getContentAsString());
        String tk = id(mvc.perform(post("/api/v1/tasks").with(alice).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"objectiveId\":\"" + obj + "\",\"title\":\"alice task\"}"))
                .andReturn().getResponse().getContentAsString());
        mvc.perform(post("/api/v1/tasks/" + tk + "/complete").with(alice)).andExpect(status().isOk());

        mvc.perform(get("/api/v1/analytics/execution").with(bob))
                .andExpect(jsonPath("$.data.status").value("no-data-yet"));
    }
}
