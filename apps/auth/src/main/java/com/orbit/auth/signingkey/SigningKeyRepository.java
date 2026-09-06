package com.orbit.auth.signingkey;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SigningKeyRepository extends JpaRepository<SigningKey, UUID> {

    Optional<SigningKey> findByActiveTrue();
}
