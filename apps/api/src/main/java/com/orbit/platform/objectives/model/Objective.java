package com.orbit.platform.objectives.model;

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
 * A user's outcome-oriented goal (Blueprint §9). Progress and status are
 * <em>derived</em> from milestone/task completion (ADR-016) — never written
 * directly through the API.
 */
@Entity
@Table(name = "objectives", schema = "orbit")
@Getter
@Setter
@NoArgsConstructor
public class Objective extends BaseEntity {

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    /** Nullable until the Career Engine (M3) — no FK constraint yet. */
    @Column(name = "career_target_id")
    private UUID careerTargetId;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    /** 1..5, 5 = highest (ADR-016 §2). Also the Priority Engine's M2 careerRelevance proxy. */
    @Column(name = "priority", nullable = false)
    private int priority = 3;

    @Column(name = "status", nullable = false, length = 20)
    @Convert(converter = ObjectiveStatus.JpaConverter.class)
    private ObjectiveStatus status = ObjectiveStatus.ACTIVE;

    @Column(name = "target_date")
    private LocalDate targetDate;

    /** 0..1, derived by {@code ProgressCalculator} (ADR-016 §3). */
    @Column(name = "progress", nullable = false, precision = 4, scale = 3)
    private BigDecimal progress = BigDecimal.ZERO;

    public static Objective create(UUID userId, String title, String description,
                                   Integer priority, LocalDate targetDate, UUID careerTargetId) {
        Objective o = new Objective();
        o.userId = userId;
        o.title = title;
        o.description = description;
        o.priority = priority == null ? 3 : priority;
        o.targetDate = targetDate;
        o.careerTargetId = careerTargetId;
        o.status = ObjectiveStatus.ACTIVE;
        o.progress = BigDecimal.ZERO;
        return o;
    }

    public boolean isArchived() {
        return status == ObjectiveStatus.ARCHIVED;
    }
}
