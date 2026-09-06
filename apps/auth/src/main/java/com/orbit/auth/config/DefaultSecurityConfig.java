package com.orbit.auth.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.util.StringUtils;

import java.util.UUID;

/**
 * Login security for the authorization server's own UI (the {@code /login} page
 * end users are redirected to during the Authorization Code flow).
 *
 * <p>Milestone 0 scope: a single in-memory user whose password is generated and
 * logged on startup unless {@code AUTH_DEV_PASSWORD} is set. No credentials are
 * committed. Milestone 1 replaces this with a persistent identity store — see
 * ADR-004.
 */
@Configuration
@EnableWebSecurity
public class DefaultSecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(DefaultSecurityConfig.class);

    @Bean
    @Order(Ordered.LOWEST_PRECEDENCE)
    public SecurityFilterChain defaultSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                        .anyRequest().authenticated())
                .formLogin(Customizer.withDefaults())
                .logout(Customizer.withDefaults());
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public UserDetailsService userDetailsService(
            PasswordEncoder passwordEncoder,
            @Value("${orbit.auth.dev-user.username:dev@orbit.local}") String username,
            @Value("${orbit.auth.dev-user.password:}") String configuredPassword) {

        String password = configuredPassword;
        if (!StringUtils.hasText(password)) {
            password = UUID.randomUUID().toString();
            log.warn("""

                    ============================================================
                    ORBIT auth server — generated dev login (no AUTH_DEV_PASSWORD set)
                      username: {}
                      password: {}
                    Set AUTH_DEV_PASSWORD for a stable login. Dev/scaffold only.
                    ============================================================""",
                    username, password);
        }

        UserDetails user = User.withUsername(username)
                .password(passwordEncoder.encode(password))
                .roles("USER")
                .build();
        return new InMemoryUserDetailsManager(user);
    }
}
