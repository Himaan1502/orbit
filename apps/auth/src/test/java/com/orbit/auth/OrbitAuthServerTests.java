package com.orbit.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class OrbitAuthServerTests {

    @Autowired
    private RegisteredClientRepository registeredClientRepository;

    @Autowired
    private AuthorizationServerSettings authorizationServerSettings;

    @Test
    void contextLoads() {
        // Verifies the authorization-server context (filter chains, JWK source,
        // token/JWT decoder beans) initialises successfully.
    }

    @Test
    void webClientIsRegistered() {
        assertThat(registeredClientRepository.findByClientId("orbit-web")).isNotNull();
    }

    @Test
    void issuerIsConfigured() {
        assertThat(authorizationServerSettings.getIssuer()).isEqualTo("http://localhost:9000");
    }
}
