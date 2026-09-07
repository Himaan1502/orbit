package com.orbit.platform.skills.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * A canonical skill in the global catalog (Blueprint §11). Not per-user and not
 * soft-deletable — the catalog is curated (ADR-020) and read-only to end users
 * in Milestone 3.
 */
@Entity
@Table(name = "skills", schema = "orbit")
@Getter
@NoArgsConstructor
public class Skill {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "canonical_name", nullable = false, length = 120)
    private String canonicalName;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "category", length = 60)
    private String category;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "active";

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
