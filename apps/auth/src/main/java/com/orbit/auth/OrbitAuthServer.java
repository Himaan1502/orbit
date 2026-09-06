package com.orbit.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * ORBIT's self-hosted OIDC provider (Spring Authorization Server).
 *
 * <p>Issues RS256-signed access and refresh tokens for the {@code orbit-web}
 * client via Authorization Code + PKCE, and publishes its public keys at
 * {@code /oauth2/jwks} for the resource server ({@code apps/api}) to verify.
 * See ADR-004. Contains no ORBIT business logic by design.
 */
@SpringBootApplication
public class OrbitAuthServer {

    public static void main(String[] args) {
        SpringApplication.run(OrbitAuthServer.class, args);
    }
}
