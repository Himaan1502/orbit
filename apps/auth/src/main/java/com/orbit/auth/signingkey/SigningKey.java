package com.orbit.auth.signingkey;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * A persisted RSA signing keypair — see ADR-011 §3.
 *
 * <p>{@link #privateKey} is AES-256-GCM sealed with a key derived from
 * {@code AUTH_KEYSTORE_SECRET}; {@link #publicKey} is stored in the clear
 * (it is published at {@code /oauth2/jwks} anyway). Multiple rows model
 * rotation history; in Milestone 1 exactly one row is {@link #active}.
 */
@Entity
@Table(name = "signing_keys")
public class SigningKey {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "key_id", nullable = false, updatable = false, length = 64)
    private String keyId;

    @Column(name = "algorithm", nullable = false, length = 10)
    private String algorithm = "RS256";

    @Column(name = "public_key", nullable = false, columnDefinition = "text")
    private String publicKey;

    @Column(name = "private_key", nullable = false, columnDefinition = "text")
    private String privateKey;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "rotated_at")
    private Instant rotatedAt;

    protected SigningKey() {
        // JPA
    }

    SigningKey(String keyId, String publicKey, String sealedPrivateKey) {
        this.id = UUID.randomUUID();
        this.keyId = keyId;
        this.publicKey = publicKey;
        this.privateKey = sealedPrivateKey;
        this.active = true;
        this.algorithm = "RS256";
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    /** Retire this key: it stops signing but its public half is still published during the overlap. */
    public void retire() {
        this.active = false;
        this.rotatedAt = Instant.now();
    }

    public Instant getRotatedAt() {
        return rotatedAt;
    }

    public UUID getId() {
        return id;
    }

    public String getKeyId() {
        return keyId;
    }

    public String getAlgorithm() {
        return algorithm;
    }

    public String getPublicKey() {
        return publicKey;
    }

    public String getPrivateKey() {
        return privateKey;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
