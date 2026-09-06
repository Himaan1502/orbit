package com.orbit.auth;

import com.orbit.auth.signingkey.SigningKeyRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class OrbitAuthServerTests extends AbstractAuthIntegrationTest {

    @Autowired
    private RegisteredClientRepository registeredClientRepository;

    @Autowired
    private AuthorizationServerSettings authorizationServerSettings;

    @Autowired
    private SigningKeyRepository signingKeyRepository;

    @Test
    void contextLoads() {
        // Verifies the authorization-server context (JDBC stores, persistent JWK
        // source, token/JWT decoder beans, Flyway migrations) initialises.
    }

    @Test
    void webClientIsPersistedAndRegistered() {
        assertThat(registeredClientRepository.findByClientId("orbit-web")).isNotNull();
    }

    @Test
    void issuerIsConfigured() {
        assertThat(authorizationServerSettings.getIssuer()).isEqualTo("http://localhost:9000");
    }

    @Test
    void aPersistentSigningKeyExists() {
        assertThat(signingKeyRepository.findByActiveTrue()).isPresent();
    }
}
