package com.orbit.platform.projects.model;

import com.orbit.platform.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * An execution vehicle for building evidence (UX §22, Blueprint §14). Per-user,
 * soft-deletable. Optionally linked to one objective, whose milestones the
 * project reuses (ADR-024 §3).
 */
@Entity
@Table(name = "projects", schema = "orbit")
@Getter
@Setter
@NoArgsConstructor
public class Project extends BaseEntity {

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "objective_id")
    private UUID objectiveId;

    @Column(name = "name", nullable = false, length = 160)
    private String name;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "status", nullable = false, length = 20)
    @Convert(converter = ProjectStatus.JpaConverter.class)
    private ProjectStatus status = ProjectStatus.PLANNED;

    @Column(name = "repository_url", columnDefinition = "text")
    private String repositoryUrl;

    @Column(name = "demo_url", columnDefinition = "text")
    private String demoUrl;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    public static Project create(UUID userId, UUID objectiveId, String name, String description,
                                 String repositoryUrl, String demoUrl) {
        Project p = new Project();
        p.userId = userId;
        p.objectiveId = objectiveId;
        p.name = name;
        p.description = description;
        p.repositoryUrl = repositoryUrl;
        p.demoUrl = demoUrl;
        p.status = ProjectStatus.PLANNED;
        return p;
    }

    /** Apply a status transition, keeping {@code started_at} / {@code completed_at} consistent. */
    public void transitionTo(ProjectStatus next) {
        if (next == ProjectStatus.ACTIVE && startedAt == null) {
            startedAt = Instant.now();
        }
        if (next == ProjectStatus.COMPLETED) {
            if (startedAt == null) {
                startedAt = Instant.now();
            }
            completedAt = Instant.now();
        } else {
            completedAt = null;   // re-opening clears the completion timestamp
        }
        this.status = next;
    }
}
