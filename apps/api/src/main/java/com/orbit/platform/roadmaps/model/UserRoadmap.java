package com.orbit.platform.roadmaps.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * A user's adoption of a roadmap (ADR-021 §5). The only per-user roadmap state.
 * Progress is <em>derived</em> (ADR-023), never stored here. Ownership is
 * structural — every row is keyed by {@code (userId, roadmapId)}.
 */
@Entity
@Table(name = "user_roadmaps", schema = "orbit")
@IdClass(UserRoadmap.Key.class)
@Getter
@Setter
@NoArgsConstructor
public class UserRoadmap {

    @Id
    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Id
    @Column(name = "roadmap_id", nullable = false, updatable = false)
    private UUID roadmapId;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "active";

    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;

    @Column(name = "target_date")
    private LocalDate targetDate;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static UserRoadmap start(UUID userId, UUID roadmapId, LocalDate targetDate) {
        UserRoadmap r = new UserRoadmap();
        r.userId = userId;
        r.roadmapId = roadmapId;
        r.status = "active";
        r.startedAt = Instant.now();
        r.targetDate = targetDate;
        return r;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (startedAt == null) {
            startedAt = now;
        }
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public static final class Key implements Serializable {
        private UUID userId;
        private UUID roadmapId;

        public Key() {
        }

        public Key(UUID userId, UUID roadmapId) {
            this.userId = userId;
            this.roadmapId = roadmapId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Key k)) {
                return false;
            }
            return Objects.equals(userId, k.userId) && Objects.equals(roadmapId, k.roadmapId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(userId, roadmapId);
        }
    }
}
