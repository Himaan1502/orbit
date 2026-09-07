package com.orbit.platform.objectives.model;

import com.orbit.platform.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

/**
 * A checkpoint within an {@link Objective} (Blueprint §9). Owned transitively by
 * the objective's owner — milestones carry no {@code user_id} (ADR-016 §5).
 */
@Entity
@Table(name = "milestones", schema = "orbit")
@Getter
@Setter
@NoArgsConstructor
public class Milestone extends BaseEntity {

    @Column(name = "objective_id", nullable = false, updatable = false)
    private UUID objectiveId;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "target_date")
    private LocalDate targetDate;

    @Column(name = "status", nullable = false, length = 20)
    @Convert(converter = MilestoneStatus.JpaConverter.class)
    private MilestoneStatus status = MilestoneStatus.PENDING;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 0;

    public static Milestone create(UUID objectiveId, String title, String description,
                                   LocalDate targetDate, int sortOrder) {
        Milestone m = new Milestone();
        m.objectiveId = objectiveId;
        m.title = title;
        m.description = description;
        m.targetDate = targetDate;
        m.sortOrder = sortOrder;
        m.status = MilestoneStatus.PENDING;
        return m;
    }

    public boolean isCompleted() {
        return status == MilestoneStatus.COMPLETED;
    }
}
