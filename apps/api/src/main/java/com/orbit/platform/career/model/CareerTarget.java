package com.orbit.platform.career.model;

import com.orbit.platform.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A user's target role (Blueprint §8). Objectives may point at one
 * ({@code objectives.career_target_id}); the target's
 * {@link CareerTargetSkill} set defines what capabilities matter and how much.
 */
@Entity
@Table(name = "career_targets", schema = "orbit")
@Getter
@Setter
@NoArgsConstructor
public class CareerTarget extends BaseEntity {

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "name", nullable = false, length = 160)
    private String name;

    @Column(name = "role_family", length = 80)
    private String roleFamily;

    @Column(name = "specialization", length = 80)
    private String specialization;

    /** Desired overall readiness 0..1, or null. */
    @Column(name = "target_readiness", precision = 4, scale = 3)
    private BigDecimal targetReadiness;

    @Column(name = "target_date")
    private LocalDate targetDate;

    /** 1..5, 5 = highest. The user's primary target is the highest-priority active one. */
    @Column(name = "priority", nullable = false)
    private int priority = 3;

    @Column(name = "status", nullable = false, length = 20)
    @Convert(converter = CareerTargetStatus.JpaConverter.class)
    private CareerTargetStatus status = CareerTargetStatus.ACTIVE;

    public static CareerTarget create(UUID userId, String name, String roleFamily, String specialization,
                                      Integer priority, LocalDate targetDate, BigDecimal targetReadiness) {
        CareerTarget t = new CareerTarget();
        t.userId = userId;
        t.name = name;
        t.roleFamily = roleFamily;
        t.specialization = specialization;
        t.priority = priority == null ? 3 : priority;
        t.targetDate = targetDate;
        t.targetReadiness = targetReadiness;
        t.status = CareerTargetStatus.ACTIVE;
        return t;
    }
}
