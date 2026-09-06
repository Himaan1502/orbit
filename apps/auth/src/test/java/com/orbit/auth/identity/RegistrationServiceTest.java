package com.orbit.auth.identity;

import com.orbit.auth.AbstractAuthIntegrationTest;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

@SpringBootTest
class RegistrationServiceTest extends AbstractAuthIntegrationTest {

    @Autowired
    private RegistrationService registrationService;

    @Autowired
    private AuthUserRepository users;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private final HttpServletRequest request = new MockHttpServletRequest();

    private RegistrationForm form(String email, String password, String confirm) {
        RegistrationForm f = new RegistrationForm();
        f.setEmail(email);
        f.setPassword(password);
        f.setConfirmPassword(confirm == null ? password : confirm);
        f.setDisplayName("Test");
        return f;
    }

    @Test
    void registersAValidAccountWithABcryptHash() {
        AuthUser user = registrationService.register(
                form("Alice@Example.com", "correct horse battery staple", null), request);

        assertThat(user.getEmail()).isEqualTo("alice@example.com");           // normalised
        assertThat(user.getPasswordHash()).startsWith("$2a$12$");             // BCrypt-12
        assertThat(passwordEncoder.matches("correct horse battery staple", user.getPasswordHash())).isTrue();
        assertThat(users.findByEmail("alice@example.com")).isPresent();
    }

    @Test
    void rejectsShortPassword() {
        assertThatThrownBy(() -> registrationService.register(form("b@example.com", "short", null), request))
                .isInstanceOf(RegistrationException.class)
                .hasMessageContaining("12");
    }

    @Test
    void rejectsPredictablePassword() {
        RegistrationException ex = catchThrowableOfType(
                () -> registrationService.register(form("c@example.com", "password1234", null), request),
                RegistrationException.class);
        assertThat(ex.getField()).isEqualTo("password");
    }

    @Test
    void rejectsPasswordContainingTheEmail() {
        assertThatThrownBy(() -> registrationService.register(
                form("charlie@example.com", "charlie-lives-here-9", null), request))
                .isInstanceOf(RegistrationException.class);
    }

    @Test
    void rejectsMismatchedConfirmation() {
        assertThatThrownBy(() -> registrationService.register(
                form("d@example.com", "a decent long passphrase", "different one entirely"), request))
                .isInstanceOf(RegistrationException.class)
                .hasMessageContaining("match");
    }

    @Test
    void rejectsDuplicateEmailCaseInsensitively() {
        registrationService.register(form("dup@example.com", "first good passphrase", null), request);
        RegistrationException ex = catchThrowableOfType(
                () -> registrationService.register(form("DUP@example.com", "second good passphrase", null), request),
                RegistrationException.class);
        assertThat(ex.getField()).isEqualTo("email");
        assertThat(users.findAll().stream().filter(u -> u.getEmail().equals("dup@example.com")).count())
                .isEqualTo(1);
    }
}
