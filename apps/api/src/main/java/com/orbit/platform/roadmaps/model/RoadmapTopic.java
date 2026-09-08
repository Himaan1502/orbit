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
 * A normalized topic within a roadmap (Blueprint §11). Self-referential tree via
 * {@code parentTopicId}; {@code sortOrder} + tree position give the recommended
 * reading order within the roadmap (ADR-021 §2). {@code name} is a short,
 * factual, ORBIT-authored label — never lifted prose.
 */
@Entity
@Table(name = "roadmap_topics", schema = "orbit")
@Getter
@NoArgsConstructor
public class RoadmapTopic {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "roadmap_id", nullable = false, updatable = false)
    private UUID roadmapId;

    @Column(name = "parent_topic_id")
    private UUID parentTopicId;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "external_identifier", length = 120)
    private String externalIdentifier;

    @Column(name = "source_url", columnDefinition = "text")
    private String sourceUrl;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
