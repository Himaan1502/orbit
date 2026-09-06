package com.orbit.platform;

import com.orbit.platform.support.AbstractApiIntegrationTest;
import org.junit.jupiter.api.Test;

class OrbitApplicationTests extends AbstractApiIntegrationTest {

    @Test
    void contextLoads() {
        // Verifies the Spring context loads against a real (Testcontainers)
        // PostgreSQL + Redis, with Flyway V1 + V2 applied.
    }
}
