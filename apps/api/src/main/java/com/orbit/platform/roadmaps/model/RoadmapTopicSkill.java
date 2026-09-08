package com.orbit.platform.roadmaps.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/**
 * Maps a roadmap topic to a canonical skill (Blueprint §11). {@code relevance}
 * (0..1) is how central the skill is to <em>this</em> roadmap; it weights the
 * derived roadmap progress and the intersection ranking (ADR-023). One canonical
 * skill can be mapped by many topics across many roadmaps.
 */
@Entity
@Table(name = "roadmap_topic_skills", schema = "orbit")
@IdClass(RoadmapTopicSkill.Key.class)
@Getter
@NoArgsConstructor
public class RoadmapTopicSkill {

    @Id
    @Column(name = "roadmap_topic_id", nullable = false, updatable = false)
    private UUID roadmapTopicId;

    @Id
    @Column(name = "skill_id", nullable = false, updatable = false)
    private UUID skillId;

    @Column(name = "relevance", nullable = false, precision = 3, scale = 2)
    private BigDecimal relevance = BigDecimal.valueOf(0.5);

    public double relevanceValue() {
        return relevance.doubleValue();
    }

    public static final class Key implements Serializable {
        private UUID roadmapTopicId;
        private UUID skillId;

        public Key() {
        }

        public Key(UUID roadmapTopicId, UUID skillId) {
            this.roadmapTopicId = roadmapTopicId;
            this.skillId = skillId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Key k)) {
                return false;
            }
            return Objects.equals(roadmapTopicId, k.roadmapTopicId) && Objects.equals(skillId, k.skillId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(roadmapTopicId, skillId);
        }
    }
}
