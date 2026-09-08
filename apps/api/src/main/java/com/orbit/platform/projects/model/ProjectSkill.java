package com.orbit.platform.projects.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/**
 * A skill a project is meant to build (Blueprint §14). {@code expectedContribution}
 * is the user's claim; {@code demonstratedContribution} is <em>derived</em> — 0
 * until the project is completed <em>and</em> has evidence for the skill
 * (ADR-026 §3) — and is never written from a request body.
 */
@Entity
@Table(name = "project_skills", schema = "orbit")
@IdClass(ProjectSkill.Key.class)
@Getter
@Setter
@NoArgsConstructor
public class ProjectSkill {

    @Id
    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @Id
    @Column(name = "skill_id", nullable = false, updatable = false)
    private UUID skillId;

    @Column(name = "expected_contribution", nullable = false, precision = 3, scale = 2)
    private BigDecimal expectedContribution = BigDecimal.valueOf(0.5);

    @Column(name = "demonstrated_contribution", nullable = false, precision = 3, scale = 2)
    private BigDecimal demonstratedContribution = BigDecimal.ZERO;

    public static ProjectSkill of(UUID projectId, UUID skillId, BigDecimal expectedContribution) {
        ProjectSkill s = new ProjectSkill();
        s.projectId = projectId;
        s.skillId = skillId;
        s.expectedContribution = expectedContribution != null ? expectedContribution : BigDecimal.valueOf(0.5);
        s.demonstratedContribution = BigDecimal.ZERO;
        return s;
    }

    public double expectedValue() {
        return expectedContribution.doubleValue();
    }

    public double demonstratedValue() {
        return demonstratedContribution.doubleValue();
    }

    public static final class Key implements Serializable {
        private UUID projectId;
        private UUID skillId;

        public Key() {
        }

        public Key(UUID projectId, UUID skillId) {
            this.projectId = projectId;
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
            return Objects.equals(projectId, k.projectId) && Objects.equals(skillId, k.skillId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(projectId, skillId);
        }
    }
}
