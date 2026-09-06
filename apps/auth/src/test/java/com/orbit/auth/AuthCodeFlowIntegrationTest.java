package com.orbit.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orbit.auth.identity.RegistrationForm;
import com.orbit.auth.identity.RegistrationService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.mock.web.MockHttpServletRequest;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end Authorization Code + PKCE against the running server (Milestone 1,
 * objective 7 — this path is exercised by the hermetic suite, not only by a
 * manual script). Proves: confidential-client token exchange, the ORBIT token
 * claims ({@code sub} = UUID, {@code email}, {@code roles}), and rotating
 * refresh tokens.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthCodeFlowIntegrationTest extends AbstractAuthIntegrationTest {

    private static final String EMAIL = "flow-user@example.com";
    private static final String PASSWORD = "a properly long passphrase 42";
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

    private static boolean registered;

    @BeforeAll
    static void resetRegistered() {
        registered = false;
    }

    private String base() {
        return "http://localhost:" + port;
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

    @Test
    void fullAuthorizationCodeFlowIssuesOrbitShapedTokens() throws Exception {
        ensureUser();

        // 1. login page -> CSRF token + session cookie
        HttpResponse<String> loginPage = http.send(
                HttpRequest.newBuilder(URI.create(base() + "/login")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        Matcher m = Pattern.compile("name=\"_csrf\"\\s+value=\"([^\"]+)\"").matcher(loginPage.body());
        assertThat(m.find()).isTrue();
        String csrf = m.group(1);

        // 2. authenticate
        HttpResponse<String> login = http.send(
                HttpRequest.newBuilder(URI.create(base() + "/login"))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(form("username=" + EMAIL, "password=" + enc(PASSWORD), "_csrf=" + csrf))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(login.statusCode()).isEqualTo(302);
        assertThat(login.headers().firstValue("location").orElse("")).doesNotContain("error");

        // 3. PKCE authorize
        String verifier = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("verifier-".concat(Long.toString(System.nanoTime())).getBytes(StandardCharsets.UTF_8));
        String challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(
                MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.UTF_8)));
        String authorize = base() + "/oauth2/authorize?response_type=code&client_id=" + CLIENT_ID
                + "&redirect_uri=" + enc(REDIRECT) + "&scope=" + enc("openid profile orbit.read orbit.write")
                + "&code_challenge=" + challenge + "&code_challenge_method=S256&state=xyz";
        HttpResponse<String> authResp = http.send(
                HttpRequest.newBuilder(URI.create(authorize)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(authResp.statusCode()).isEqualTo(302);
        String location = authResp.headers().firstValue("location").orElseThrow();
        Matcher codeMatcher = Pattern.compile("[?&]code=([^&]+)").matcher(location);
        assertThat(codeMatcher.find()).as("authorize returned a code: %s", location).isTrue();
        String code = codeMatcher.group(1);

        // 4. token exchange (confidential client -> HTTP Basic)
        HttpResponse<String> tokenResp = http.send(
                HttpRequest.newBuilder(URI.create(base() + "/oauth2/token"))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .header("Authorization", basic())
                        .POST(form("grant_type=authorization_code", "code=" + code,
                                "redirect_uri=" + enc(REDIRECT), "client_id=" + CLIENT_ID,
                                "code_verifier=" + verifier))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(tokenResp.statusCode()).as(tokenResp.body()).isEqualTo(200);
        JsonNode tokens = json.readTree(tokenResp.body());
        assertThat(tokens.hasNonNull("access_token")).isTrue();
        assertThat(tokens.hasNonNull("refresh_token")).as("confidential client gets a refresh token").isTrue();

        JsonNode claims = decode(tokens.get("access_token").asText());
        assertThat(claims.get("sub").asText()).matches("[0-9a-f\\-]{36}");   // UUID, not the email
        assertThat(claims.get("email").asText()).isEqualTo(EMAIL);
        assertThat(claims.get("roles").isArray()).isTrue();
        assertThat(claims.get("roles").get(0).asText()).isEqualTo("USER");
        assertThat(claims.get("iss").asText()).isEqualTo("http://localhost:9000");

        // 5. refresh rotates
        HttpResponse<String> refreshResp = http.send(
                HttpRequest.newBuilder(URI.create(base() + "/oauth2/token"))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .header("Authorization", basic())
                        .POST(form("grant_type=refresh_token",
                                "refresh_token=" + tokens.get("refresh_token").asText()))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(refreshResp.statusCode()).as(refreshResp.body()).isEqualTo(200);
        JsonNode refreshed = json.readTree(refreshResp.body());
        assertThat(refreshed.get("refresh_token").asText())
                .isNotEqualTo(tokens.get("refresh_token").asText());
    }

    private static HttpRequest.BodyPublisher form(String... pairs) {
        return HttpRequest.BodyPublishers.ofString(String.join("&", List.of(pairs)));
    }

    private static String enc(String s) {
        return java.net.URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static String basic() {
        return "Basic " + Base64.getEncoder()
                .encodeToString((CLIENT_ID + ":" + CLIENT_SECRET).getBytes(StandardCharsets.UTF_8));
    }

    private JsonNode decode(String jwt) throws Exception {
        String payload = jwt.split("\\.")[1];
        return json.readTree(Base64.getUrlDecoder().decode(payload));
    }
}
