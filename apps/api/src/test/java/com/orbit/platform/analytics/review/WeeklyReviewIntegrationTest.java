package com.orbit.platform.analytics.review;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orbit.platform.common.jobs.JobRunner;
import com.orbit.platform.support.AbstractApiIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Weekly Review — generation, idempotency, the scheduled job, ownership (ADR-028/029). */
@AutoConfigureMockMvc
class WeeklyReviewIntegrationTest extends AbstractApiIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    WeeklyReviewGenerationJob job;

    private RequestPostProcessor user(UUID sub) {
        return jwt().jwt(j -> j.subject(sub.toString()).claim("email", sub + "@example.com")
                .claim("roles", List.of("USER")));
    }

    private String id(String body) throws Exception {
        return json.readTree(body).path("data").path("id").asText();
    }

    private LocalDate lastWeekMonday() {
        return LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks(1);
    }

    @Test
    void generateThenRegeneratePreservesUserNotesAndStatus() throws Exception {
        var u = user(UUID.randomUUID());
        String obj = id(mvc.perform(post("/api/v1/objectives").with(u).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"o\"}")).andReturn().getResponse().getContentAsString());
        String task = id(mvc.perform(post("/api/v1/tasks").with(u).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"objectiveId\":\"" + obj + "\",\"title\":\"did it\"}"))
                .andReturn().getResponse().getContentAsString());
        mvc.perform(post("/api/v1/tasks/" + task + "/complete").with(u)).andExpect(status().isOk());

        LocalDate wk = lastWeekMonday();
        String created = mvc.perform(post("/api/v1/weekly-reviews/generate").with(u)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"weekStart\":\"" + wk + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.weekStart").value(wk.toString()))
                .andExpect(jsonPath("$.data.status").value("generated"))
                .andExpect(jsonPath("$.data.generatedBy").value("system"))
                .andReturn().getResponse().getContentAsString();
        String reviewId = id(created);

        mvc.perform(patch("/api/v1/weekly-reviews/" + reviewId).with(u).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userNotes\":\"felt slow\",\"status\":\"reviewed\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userNotes").value("felt slow"))
                .andExpect(jsonPath("$.data.status").value("reviewed"));

        // regenerate — same (user, week) row, notes + status kept
        mvc.perform(post("/api/v1/weekly-reviews/generate").with(u).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"weekStart\":\"" + wk + "\"}"))
                .andExpect(jsonPath("$.data.id").value(reviewId))
                .andExpect(jsonPath("$.data.userNotes").value("felt slow"))
                .andExpect(jsonPath("$.data.status").value("reviewed"));

        mvc.perform(get("/api/v1/weekly-reviews").with(u))
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    void getForAFutureWeekIs404AndAPastWeekLazilyGenerates() throws Exception {
        var u = user(UUID.randomUUID());
        // activity so the user is "active"
        String obj = id(mvc.perform(post("/api/v1/objectives").with(u).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"o\"}")).andReturn().getResponse().getContentAsString());
        String task = id(mvc.perform(post("/api/v1/tasks").with(u).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"objectiveId\":\"" + obj + "\",\"title\":\"t\"}"))
                .andReturn().getResponse().getContentAsString());
        mvc.perform(post("/api/v1/tasks/" + task + "/complete").with(u)).andExpect(status().isOk());

        LocalDate future = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY)).plusWeeks(1);
        mvc.perform(get("/api/v1/weekly-reviews/" + future).with(u)).andExpect(status().isNotFound());

        LocalDate past = lastWeekMonday();
        mvc.perform(get("/api/v1/weekly-reviews/" + past).with(u))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.weekStart").value(past.toString()));
    }

    @Test
    void theScheduledJobIsIdempotentPerIsoWeek() throws Exception {
        var u = user(UUID.randomUUID());
        String obj = id(mvc.perform(post("/api/v1/objectives").with(u).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"o\"}")).andReturn().getResponse().getContentAsString());
        String task = id(mvc.perform(post("/api/v1/tasks").with(u).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"objectiveId\":\"" + obj + "\",\"title\":\"t\"}"))
                .andReturn().getResponse().getContentAsString());
        mvc.perform(post("/api/v1/tasks/" + task + "/complete").with(u)).andExpect(status().isOk());

        // run the job for the CURRENT week — where this test's activity lives
        LocalDate today = LocalDate.now();
        LocalDate thisMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));

        var first = job.runForWeekEnding(today);
        var second = job.runForWeekEnding(today);

        assertThat(first.outcome()).isEqualTo(JobRunner.Outcome.SUCCEEDED);
        assertThat(first.rowsAffected()).isGreaterThanOrEqualTo(1);
        assertThat(second.outcome()).isEqualTo(JobRunner.Outcome.SKIPPED);

        String key = WeeklyReviewService.isoWeekKey(today);
        Long runs = jdbc.queryForObject(
                "select count(*) from orbit.job_runs where job_name = 'weekly-review-generation' and run_key = ?",
                Long.class, key);
        assertThat(runs).isEqualTo(1L);

        Long reviews = jdbc.queryForObject(
                "select count(*) from orbit.weekly_reviews where user_id = ? and week_start = ?",
                Long.class, UUID.fromString(json.readTree(
                        mvc.perform(get("/api/v1/me").with(u)).andReturn().getResponse().getContentAsString())
                        .path("data").path("id").asText()),
                java.sql.Date.valueOf(thisMonday));
        assertThat(reviews).isEqualTo(1L);
    }

    @Test
    void oneUsersReviewIsNeverVisibleToAnother() throws Exception {
        var alice = user(UUID.randomUUID());
        var bob = user(UUID.randomUUID());
        String review = id(mvc.perform(post("/api/v1/weekly-reviews/generate").with(alice)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"weekStart\":\"" + lastWeekMonday() + "\"}"))
                .andReturn().getResponse().getContentAsString());

        mvc.perform(patch("/api/v1/weekly-reviews/" + review).with(bob).contentType(MediaType.APPLICATION_JSON)
                .content("{\"userNotes\":\"sneaky\"}")).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/weekly-reviews").with(bob))
                .andExpect(jsonPath("$.data.length()").value(0));
    }
}
