package com.orbit.platform.skills.model;

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
 * A directed prerequisite edge in the global skill graph (Blueprint §12):
 * knowing {@code prerequisiteSkillId} helps toward {@code dependentSkillId}.
 * The graph is kept acyclic — see {@code SkillGraphValidator} (ADR-019).
 */
@Entity
@Table(name = "skill_dependencies", schema = "orbit")
@IdClass(SkillDependency.Key.class)
@Getter
@NoArgsConstructor
public class SkillDependency {

    @Id
    @Column(name = "prerequisite_skill_id", nullable = false, updatable = false)
    private UUID prerequisiteSkillId;

    @Id
    @Column(name = "dependent_skill_id", nullable = false, updatable = false)
    private UUID dependentSkillId;

    @Column(name = "dependency_strength", nullable = false, precision = 3, scale = 2)
    private BigDecimal dependencyStrength = BigDecimal.valueOf(0.5);

    public double strength() {
        return dependencyStrength.doubleValue();
    }

    public static final class Key implements Serializable {
        private UUID prerequisiteSkillId;
        private UUID dependentSkillId;

        public Key() {
        }

        public Key(UUID prerequisiteSkillId, UUID dependentSkillId) {
            this.prerequisiteSkillId = prerequisiteSkillId;
            this.dependentSkillId = dependentSkillId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Key k)) {
                return false;
            }
            return Objects.equals(prerequisiteSkillId, k.prerequisiteSkillId)
                    && Objects.equals(dependentSkillId, k.dependentSkillId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(prerequisiteSkillId, dependentSkillId);
        }
    }
}
