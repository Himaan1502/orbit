package com.orbit.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orbit.auth.identity.RegistrationForm;
import com.orbit.auth.identity.RegistrationService;
import com.orbit.auth.signingkey.SigningKeyManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ADR-011 §3 — overlapping signing-key rotation. After a rotation the JWKS
 * publishes both keys, a token signed before the rotation still verifies, and
 * new tokens are signed by the new key.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SigningKeyRotationIntegrationTest extends AbstractAuthIntegrationTest {

    private static final String EMAIL = "rotation-user@example.com";
    private static final String PASSWORD = "another good long passphrase 7";
    private static final String CLIENT_ID = "orbit-web";
    private static final String CLIENT_SECRET = "test-only-web-client-secret";
    private static final String REDIRECT = "http://localhost:3000/auth/callback";

    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .cookieHandler(new java.net.CookieManager())
            .build();

    @LocalServerPort
    int port;

    @Autowired
    RegistrationService registrationService;

    @Autowired
    SigningKeyManager signingKeyManager;

    private static boolean registered;

    @Test
    void rotationOverlapsSoOldTokensStillVerify() throws Exception {
        ensureUser();

        String tokenBefore = fullFlowAccessToken();
        String kidBefore = header(tokenBefore).get("kid").asText();
        assertThat(jwksKids()).containsExactly(kidBefore);
        assertThat(signingKeyManager.activeKeyId()).isEqualTo(kidBefore);

        signingKeyManager.rotate();

        String kidAfter = signingKeyManager.activeKeyId();
        assertThat(kidAfter).isNotEqualTo(kidBefore);
        assertThat(jwksKids()).containsExactlyInAnyOrder(kidBefore, kidAfter);

        // The token minted before the rotation still validates against the new JWKS.
        NimbusJwtDecoder decoder =
                NimbusJwtDecoder.withJwkSetUri(base() + "/oauth2/jwks").build();
        Jwt stillValid = decoder.decode(tokenBefore);
        assertThat(stillValid.getHeaders().get("kid")).isEqualTo(kidBefore);

        // A fresh token is signed by the new active key.
        String tokenAfter = fullFlowAccessToken();
        assertThat(header(tokenAfter).get("kid").asText()).isEqualTo(kidAfter);
        assertThat(decoder.decode(tokenAfter).getSubject()).isNotBlank();
    }

    private void ensureUser() {
        if (registered) {
            return;
        }
        RegistrationForm form = new RegistrationForm();
        form.setEmail(EMAIL);
        form.setPassword(PASSWORD);
        form.setConfirmPassword(PASSWORD);
        registrationService.register(form, new MockHttpServletRequest());
        registered = true;
    }

    private String base() {
        return "http://localhost:" + port;
    }

    private java.util.List<String> jwksKids() throws Exception {
        JsonNode keys = json.readTree(http.send(
                HttpRequest.newBuilder(URI.create(base() + "/oauth2/jwks")).GET().build(),
                HttpResponse.BodyHandlers.ofString()).body()).get("keys");
        java.util.List<String> kids = new java.util.ArrayList<>();
        keys.forEach(k -> kids.add(k.get("kid").asText()));
        return kids;
    }

    private JsonNode header(String jwt) throws Exception {
        return json.readTree(Base64.getUrlDecoder().decode(jwt.split("\\.")[0]));
    }

    /** Runs login + PKCE authorize + confidential token exchange, returns the access token. */
    private String fullFlowAccessToken() throws Exception {
        HttpResponse<String> loginPage = http.send(
                HttpRequest.newBuilder(URI.create(base() + "/login")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        Matcher m = Pattern.compile("name=\"_csrf\"\\s+value=\"([^\"]+)\"").matcher(loginPage.body());
        m.find();
        String csrf = m.group(1);

        http.send(HttpRequest.newBuilder(URI.create(base() + "/login"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(
                        "username=" + EMAIL + "&password=" + enc(PASSWORD) + "&_csrf=" + csrf))
                .build(), HttpResponse.BodyHandlers.ofString());

        String verifier = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(("v-" + System.nanoTime()).getBytes(StandardCharsets.UTF_8));
        String challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(
                MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.UTF_8)));
        String authorize = base() + "/oauth2/authorize?response_type=code&client_id=" + CLIENT_ID
                + "&redirect_uri=" + enc(REDIRECT) + "&scope=" + enc("openid orbit.read")
                + "&code_challenge=" + challenge + "&code_challenge_method=S256&state=s";
        String location = http.send(
                HttpRequest.newBuilder(URI.create(authorize)).GET().build(),
                HttpResponse.BodyHandlers.ofString()).headers().firstValue("location").orElseThrow();
        Matcher c = Pattern.compile("[?&]code=([^&]+)").matcher(location);
        c.find();

        HttpResponse<String> token = http.send(HttpRequest.newBuilder(URI.create(base() + "/oauth2/token"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Authorization", "Basic " + Base64.getEncoder()
                        .encodeToString((CLIENT_ID + ":" + CLIENT_SECRET).getBytes(StandardCharsets.UTF_8)))
                .POST(HttpRequest.BodyPublishers.ofString(
                        "grant_type=authorization_code&code=" + c.group(1) + "&redirect_uri=" + enc(REDIRECT)
                                + "&client_id=" + CLIENT_ID + "&code_verifier=" + verifier))
                .build(), HttpResponse.BodyHandlers.ofString());
        return json.readTree(token.body()).get("access_token").asText();
    }

    private static String enc(String s) {
        return java.net.URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
