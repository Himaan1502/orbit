package com.orbit.platform.evidence.model;

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

/** Links an evidence item to a skill it demonstrates (Blueprint §13). */
@Entity
@Table(name = "skill_evidence", schema = "orbit")
@IdClass(SkillEvidence.Key.class)
@Getter
@NoArgsConstructor
public class SkillEvidence {

    @Id
    @Column(name = "skill_id", nullable = false, updatable = false)
    private UUID skillId;

    @Id
    @Column(name = "evidence_id", nullable = false, updatable = false)
    private UUID evidenceId;

    @Column(name = "contribution_weight", nullable = false, precision = 3, scale = 2)
    private BigDecimal contributionWeight = BigDecimal.valueOf(0.5);

    public static SkillEvidence of(UUID skillId, UUID evidenceId, BigDecimal contributionWeight) {
        SkillEvidence s = new SkillEvidence();
        s.skillId = skillId;
        s.evidenceId = evidenceId;
        s.contributionWeight = contributionWeight != null ? contributionWeight : BigDecimal.valueOf(0.5);
        return s;
    }

    public double weight() {
        return contributionWeight.doubleValue();
    }

    public static final class Key implements Serializable {
        private UUID skillId;
        private UUID evidenceId;

        public Key() {
        }

        public Key(UUID skillId, UUID evidenceId) {
            this.skillId = skillId;
            this.evidenceId = evidenceId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Key k)) {
                return false;
            }
            return Objects.equals(skillId, k.skillId) && Objects.equals(evidenceId, k.evidenceId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(skillId, evidenceId);
        }
    }
}
