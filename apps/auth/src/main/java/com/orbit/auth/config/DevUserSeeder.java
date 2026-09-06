package com.orbit.auth.config;

import com.orbit.auth.identity.AuthUser;
import com.orbit.auth.identity.AuthUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Locale;

/**
 * Dev-only convenience: upserts a stable local login when
 * {@code AUTH_DEV_PASSWORD} is set. No credentials are committed — if the env
 * var is blank this does nothing and you register through {@code /signup}.
 */
@Component
@Profile("dev")
public class DevUserSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DevUserSeeder.class);

    private final AuthUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final String devEmail;
    private final String devPassword;

    public DevUserSeeder(
            AuthUserRepository users,
            PasswordEncoder passwordEncoder,
            @Value("${orbit.auth.dev-user.username:dev@orbit.local}") String devEmail,
            @Value("${orbit.auth.dev-user.password:}") String devPassword) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.devEmail = devEmail == null ? "" : devEmail.trim().toLowerCase(Locale.ROOT);
        this.devPassword = devPassword;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!StringUtils.hasText(devPassword)) {
            log.info("No AUTH_DEV_PASSWORD set — skipping dev-user seed. Register at /signup.");
            return;
        }
        users.findByEmail(devEmail).ifPresentOrElse(
                existing -> {
                    existing.changePassword(passwordEncoder.encode(devPassword));
                    users.save(existing);
                    log.info("Dev user '{}' password reset from AUTH_DEV_PASSWORD.", devEmail);
                },
                () -> {
                    users.save(AuthUser.create(devEmail, passwordEncoder.encode(devPassword), "Dev User"));
                    log.info("Dev user '{}' created from AUTH_DEV_PASSWORD.", devEmail);
                });
    }
}
