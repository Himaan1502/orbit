package com.orbit.platform.identity.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Per-user preferences and planning context (Blueprint §7.2). Shares the
 * primary key with {@link User} ({@code user_id} == {@code users.id}).
 */
@Entity
@Table(name = "user_profiles", schema = "orbit")
@Getter
@Setter
@NoArgsConstructor
public class UserProfile {

    @Id
    @Column(name = "user_id", updatable = false, nullable = false)
    private UUID userId;

    @Column(name = "timezone", nullable = false, length = 64)
    private String timezone = "UTC";

    @Column(name = "locale", length = 16)
    private String locale;

    @Column(name = "available_daily_minutes")
    private Integer availableDailyMinutes;

    // `preferences` (jsonb) is created by V2 with a '{}' default but not mapped
    // here — no M1 endpoint reads or writes it. Added when preferences land.

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static UserProfile forUser(UUID userId) {
        UserProfile p = new UserProfile();
        p.userId = userId;
        return p;
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
}
