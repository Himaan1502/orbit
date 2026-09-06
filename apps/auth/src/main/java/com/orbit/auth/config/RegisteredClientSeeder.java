package com.orbit.auth.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

/**
 * Keeps the {@code orbit-web} client in the JDBC store in sync with
 * configuration on every startup (ADR-011 §2). Configuration is the source of
 * truth for the client definition; the database makes it durable and lets
 * {@code oauth2_authorization} rows carry a stable {@code registered_client_id}.
 *
 * <p>{@code orbit-web} is a <strong>confidential</strong> client — the OAuth
 * client is the Next.js BFF server, not the browser (ADR-013). A confidential
 * client is required for Spring Authorization Server to issue rotating refresh
 * tokens (ADR-004 §1). PKCE stays mandatory as defence in depth.
 */
@Component
public class RegisteredClientSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(RegisteredClientSeeder.class);

    private final RegisteredClientRepository clients;
    private final PasswordEncoder passwordEncoder;
    private final String clientId;
    private final String clientSecret;
    private final String redirectUri;
    private final String postLogoutRedirectUri;

    public RegisteredClientSeeder(
            RegisteredClientRepository clients,
            PasswordEncoder passwordEncoder,
            @Value("${orbit.auth.web-client.client-id:orbit-web}") String clientId,
            @Value("${orbit.auth.web-client.client-secret}") String clientSecret,
            @Value("${orbit.auth.web-client.redirect-uri}") String redirectUri,
            @Value("${orbit.auth.web-client.post-logout-redirect-uri}") String postLogoutRedirectUri) {
        this.clients = clients;
        this.passwordEncoder = passwordEncoder;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.redirectUri = redirectUri;
        this.postLogoutRedirectUri = postLogoutRedirectUri;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (clientSecret == null || clientSecret.isBlank()) {
            throw new IllegalStateException(
                    "orbit.auth.web-client.client-secret (WEB_CLIENT_SECRET) must be set");
        }

        RegisteredClient existing = clients.findByClientId(clientId);
        String id = existing != null ? existing.getId() : UUID.randomUUID().toString();

        RegisteredClient webClient = RegisteredClient.withId(id)
                .clientId(clientId)
                // Confidential client: authenticates at the token endpoint with a
                // secret held only by the BFF server. PKCE also mandatory below.
                .clientSecret(passwordEncoder.encode(clientSecret))
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .redirectUri(redirectUri)
                .postLogoutRedirectUri(postLogoutRedirectUri)
                .scope(OidcScopes.OPENID)
                .scope(OidcScopes.PROFILE)
                .scope("orbit.read")
                .scope("orbit.write")
                .clientSettings(ClientSettings.builder()
                        .requireProofKey(true)
                        .requireAuthorizationConsent(false)
                        .build())
                .tokenSettings(TokenSettings.builder()
                        .accessTokenTimeToLive(Duration.ofMinutes(15))
                        .refreshTokenTimeToLive(Duration.ofDays(7))
                        .reuseRefreshTokens(false)
                        .build())
                .build();

        clients.save(webClient);
        log.info("Registered client '{}' {} (id={}, confidential, PKCE)",
                clientId, existing != null ? "updated" : "created", id);
    }
}
