package com.orbit.platform.career.model;

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
 * How much one skill matters for a career target (Blueprint §8). {@code importance}
 * weights the skill in overall readiness; {@code interviewWeight} /
 * {@code productionWeight} weight its interview / production dimensions.
 */
@Entity
@Table(name = "career_target_skills", schema = "orbit")
@IdClass(CareerTargetSkill.Key.class)
@Getter
@Setter
@NoArgsConstructor
public class CareerTargetSkill {

    @Id
    @Column(name = "career_target_id", nullable = false, updatable = false)
    private UUID careerTargetId;

    @Id
    @Column(name = "skill_id", nullable = false, updatable = false)
    private UUID skillId;

    @Column(name = "importance", nullable = false, precision = 3, scale = 2)
    private BigDecimal importance = BigDecimal.valueOf(0.5);

    @Column(name = "interview_weight", nullable = false, precision = 3, scale = 2)
    private BigDecimal interviewWeight = BigDecimal.valueOf(0.5);

    @Column(name = "production_weight", nullable = false, precision = 3, scale = 2)
    private BigDecimal productionWeight = BigDecimal.valueOf(0.5);

    public static CareerTargetSkill of(UUID careerTargetId, UUID skillId,
                                       BigDecimal importance, BigDecimal interviewWeight,
                                       BigDecimal productionWeight) {
        CareerTargetSkill s = new CareerTargetSkill();
        s.careerTargetId = careerTargetId;
        s.skillId = skillId;
        s.importance = importance != null ? importance : BigDecimal.valueOf(0.5);
        s.interviewWeight = interviewWeight != null ? interviewWeight : BigDecimal.valueOf(0.5);
        s.productionWeight = productionWeight != null ? productionWeight : BigDecimal.valueOf(0.5);
        return s;
    }

    public double importanceValue() {
        return importance.doubleValue();
    }

    public double interviewWeightValue() {
        return interviewWeight.doubleValue();
    }

    public static final class Key implements Serializable {
        private UUID careerTargetId;
        private UUID skillId;

        public Key() {
        }

        public Key(UUID careerTargetId, UUID skillId) {
            this.careerTargetId = careerTargetId;
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
            return Objects.equals(careerTargetId, k.careerTargetId) && Objects.equals(skillId, k.skillId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(careerTargetId, skillId);
        }
    }
}
