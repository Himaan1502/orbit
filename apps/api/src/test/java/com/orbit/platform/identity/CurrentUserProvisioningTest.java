package com.orbit.platform.identity;

import com.orbit.platform.identity.repository.UserProfileRepository;
import com.orbit.platform.identity.repository.UserRepository;
import com.orbit.platform.support.AbstractApiIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ADR-012 §3 — the API provisions a local {@code orbit.users} + empty
 * {@code orbit.user_profiles} row keyed by the token {@code sub} on first
 * authenticated request, from the token claims only.
 */
@AutoConfigureMockMvc
class CurrentUserProvisioningTest extends AbstractApiIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    UserRepository users;

    @Autowired
    UserProfileRepository profiles;

    @Test
    void firstCallToMeProvisionsUserAndProfileFromTokenClaims() throws Exception {
        UUID sub = UUID.randomUUID();
        assertThat(users.findById(sub)).isEmpty();

        mockMvc.perform(get("/api/v1/me").with(jwt().jwt(j -> j
                        .subject(sub.toString())
                        .claim("email", "Provisioned@Example.com")
                        .claim("name", "Provisioned Person")
                        .claim("roles", java.util.List.of("USER")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(sub.toString()))
                .andExpect(jsonPath("$.data.email").value("provisioned@example.com"))
                .andExpect(jsonPath("$.data.displayName").value("Provisioned Person"));

        assertThat(users.findById(sub)).isPresent();
        assertThat(profiles.findById(sub)).isPresent();
        assertThat(profiles.findById(sub).orElseThrow().getTimezone()).isEqualTo("UTC");

        // Second call must not create a duplicate / must not fail.
        mockMvc.perform(get("/api/v1/me").with(jwt().jwt(j -> j
                        .subject(sub.toString())
                        .claim("email", "provisioned@example.com"))))
                .andExpect(status().isOk());
        assertThat(users.count()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void tokenWithoutEmailClaimIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/me").with(jwt().jwt(j -> j
                        .subject(UUID.randomUUID().toString()))))
                .andExpect(status().isUnauthorized());
    }
}
