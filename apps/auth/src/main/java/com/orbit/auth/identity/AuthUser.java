package com.orbit.auth.identity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * The ORBIT identity / credential record — see ADR-012.
 *
 * <p>This entity is owned by {@code apps/auth}. Its {@link #id} is the value
 * issued as the OIDC {@code sub} claim and is mirrored (same UUID) by
 * {@code orbit.users.id} in the API's schema. {@code apps/api} holds no
 * password material.
 */
@Entity
@Table(name = "users")
public class AuthUser {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "email", nullable = false, length = 254)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "display_name", length = 100)
    private String displayName;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "active";

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AuthUser() {
        // JPA
    }

    private AuthUser(UUID id, String email, String passwordHash, String displayName) {
        this.id = id;
        this.email = email;
        this.passwordHash = passwordHash;
        this.displayName = displayName;
        this.status = "active";
    }

    /** Creates a new active account. Email must already be normalised to lower case. */
    public static AuthUser create(String normalisedEmail, String passwordHash, String displayName) {
        return new AuthUser(UUID.randomUUID(), normalisedEmail, passwordHash, displayName);
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public boolean isActive() {
        return "active".equals(this.status);
    }

    public void changePassword(String newPasswordHash) {
        this.passwordHash = newPasswordHash;
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
