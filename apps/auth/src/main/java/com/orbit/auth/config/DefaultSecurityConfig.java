package com.orbit.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Login / signup security for the authorization server's own UI — the pages
 * end users are redirected to during the Authorization Code flow.
 *
 * <p>Milestone 1: credentials come from {@code auth.users} via
 * {@code AuthUserDetailsService} (ADR-012); the in-memory M0 user is gone.
 * Registration ({@code /signup}, {@code POST /register}) is public; everything
 * else requires authentication. CSRF protection stays on for these
 * session-cookie form pages (Blueprint §35).
 */
@Configuration
@EnableWebSecurity
public class DefaultSecurityConfig {

    @Bean
    @Order(Ordered.LOWEST_PRECEDENCE)
    public SecurityFilterChain defaultSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(
                                "/actuator/health/**", "/actuator/info",
                                "/login", "/signup", "/register", "/error",
                                "/css/**", "/favicon.ico")
                        .permitAll()
                        .anyRequest().authenticated())
                .formLogin(form -> form
                        .loginPage("/login")
                        .permitAll())
                .logout(Customizer.withDefaults());
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}
