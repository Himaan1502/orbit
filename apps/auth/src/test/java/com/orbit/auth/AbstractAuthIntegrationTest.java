package com.orbit.auth;

import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base for auth-server integration tests. Uses one shared PostgreSQL container
 * for the whole test run (the Testcontainers "singleton container" pattern), so
 * the suite is hermetic on any machine with a Docker daemon (Milestone 1
 * objective 7) and context caching across test classes works cleanly. Flyway
 * runs the real {@code auth} schema migrations against it. Concrete tests add
 * {@code @SpringBootTest} with the web environment they need.
 */
@ActiveProfiles("test")
public abstract class AbstractAuthIntegrationTest {

    static final PostgreSQLContainer<?> POSTGRES;

    static {
        POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
                .withDatabaseName("orbit_db")
                .withUsername("orbit_user")
                .withPassword("orbit_pass");
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
}
