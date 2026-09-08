package com.orbit.platform.roadmaps.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * A normalized external roadmap in the global catalog (Blueprint §11, ADR-021).
 * Not per-user, not soft-deletable — curated data, populated by the V7 seed
 * (ADR-022) and read-only to end users. Only {@code name}, source attribution,
 * normalized topics and skill mappings are stored — no third-party prose
 * (PRD Non-Goal, Blueprint §25).
 */
@Entity
@Table(name = "roadmaps", schema = "orbit")
@Getter
@NoArgsConstructor
public class Roadmap {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "external_identifier", nullable = false, length = 80)
    private String externalIdentifier;

    @Column(name = "source_name", nullable = false, length = 80)
    private String sourceName;

    @Column(name = "source_url", nullable = false, columnDefinition = "text")
    private String sourceUrl;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    /** 1, 2 or 3 (P1/P2/P3). Informs UI surfacing only — never a Priority Engine input (ADR-021 §6). */
    @Column(name = "priority_tier", nullable = false)
    private int priorityTier;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "active";

    @Column(name = "last_verified_at")
    private Instant lastVerifiedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
