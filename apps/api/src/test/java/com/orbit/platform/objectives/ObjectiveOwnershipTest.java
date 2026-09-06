package com.orbit.platform.objectives;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proves server-side resource-ownership enforcement (Tech Arch §9, Blueprint §36
 * IDOR / cross-user access). A second authenticated user cannot read, modify, or
 * delete the first user's objective — the API answers {@code 404}, disclosing
 * nothing about the resource's existence.
 */
@AutoConfigureMockMvc
class ObjectiveOwnershipTest extends AbstractApiIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper json;

    private RequestPostProcessor user(UUID sub, String email) {
        return jwt().jwt(j -> j.subject(sub.toString())
                .claim("email", email)
                .claim("roles", java.util.List.of("USER")));
    }

    @Test
    void aSecondUserCannotAccessTheFirstUsersObjective() throws Exception {
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();

        String created = mockMvc.perform(post("/api/v1/objectives")
                        .with(user(alice, "alice@example.com"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Ship Milestone 1","description":"identity + auth"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.title").value("Ship Milestone 1"))
                .andReturn().getResponse().getContentAsString();
        String objectiveId = json.readTree(created).path("data").path("id").asText();

        // Alice sees it.
        mockMvc.perform(get("/api/v1/objectives/" + objectiveId).with(user(alice, "alice@example.com")))
                .andExpect(status().isOk());

        // Bob cannot read it.
        mockMvc.perform(get("/api/v1/objectives/" + objectiveId).with(user(bob, "bob@example.com")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));

        // Bob cannot update it.
        mockMvc.perform(patch("/api/v1/objectives/" + objectiveId)
                        .with(user(bob, "bob@example.com"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Hijacked"}"""))
                .andExpect(status().isNotFound());

        // Bob cannot delete it.
        mockMvc.perform(delete("/api/v1/objectives/" + objectiveId).with(user(bob, "bob@example.com")))
                .andExpect(status().isNotFound());

        // Bob's list is empty; Alice's still has the objective, unchanged.
        mockMvc.perform(get("/api/v1/objectives").with(user(bob, "bob@example.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));

        String aliceList = mockMvc.perform(get("/api/v1/objectives").with(user(alice, "alice@example.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andReturn().getResponse().getContentAsString();
        JsonNode first = json.readTree(aliceList).path("data").get(0);
        assertThat(first.path("title").asText()).isEqualTo("Ship Milestone 1");
    }

    @Test
    void unauthenticatedRequestsAreRejected() throws Exception {
        mockMvc.perform(get("/api/v1/objectives")).andExpect(status().isUnauthorized());
    }

    @Test
    void validationErrorsUseTheStandardShape() throws Exception {
        mockMvc.perform(post("/api/v1/objectives")
                        .with(user(UUID.randomUUID(), "val@example.com"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"   "}"""))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.details[0].field").value("title"));
    }
}
