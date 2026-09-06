package com.orbit.platform.common.config;

import com.orbit.platform.common.security.OidcJwtAuthenticationConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Resource-server security for the ORBIT API.
 *
 * <p>Access tokens are issued by the self-hosted OIDC provider ({@code apps/auth},
 * Spring Authorization Server) and validated here against its JWKS — see ADR-004.
 * This service never issues tokens and holds no signing secret.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final OidcJwtAuthenticationConverter jwtAuthenticationConverter;

    public SecurityConfig(OidcJwtAuthenticationConverter jwtAuthenticationConverter) {
        this.jwtAuthenticationConverter = jwtAuthenticationConverter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // Bearer-token API: no auth cookies are used, so there is no CSRF
                // vector to protect (Tech Arch §9: "CSRF protection where cookie
                // authentication requires it").
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/api/v1/health",
                                "/actuator/health/**",
                                "/actuator/info",
                                "/actuator/prometheus",
                                "/api/docs/**",
                                "/api/swagger-ui/**",
                                "/v3/api-docs/**"
                        ).permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)))
                .headers(headers -> headers
                        .contentTypeOptions(contentType -> { })
                        .frameOptions(frame -> frame.deny())
                        .xssProtection(xss -> { })
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(31536000)));

        return http.build();
    }
}
