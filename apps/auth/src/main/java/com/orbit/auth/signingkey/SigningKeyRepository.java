package com.orbit.auth.signingkey;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SigningKeyRepository extends JpaRepository<SigningKey, UUID> {

    Optional<SigningKey> findByActiveTrue();

    /**
     * Keys still worth publishing: the active signer plus any retired key whose
     * last-signed token could still be alive ({@code rotated_at} newer than the
     * cutoff). Ordered active-first for determinism.
     */
    List<SigningKey> findByActiveTrueOrRotatedAtAfterOrderByActiveDescCreatedAtDesc(Instant cutoff);

    List<SigningKey> findByActiveFalseAndRotatedAtBefore(Instant cutoff);
}
