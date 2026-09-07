package com.orbit.platform.execution.model;

import com.orbit.platform.common.model.BaseEntity;
import com.orbit.platform.execution.priority.PriorityReason;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * An executable unit of work (Blueprint §10). M2 subset: linked to an objective
 * and optionally a milestone; {@code project_id} / {@code skill_id} arrive with
 * their domains in M3+.
 */
@Entity
@Table(name = "tasks", schema = "orbit")
@Getter
@Setter
@NoArgsConstructor
public class Task extends BaseEntity {

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "objective_id")
    private UUID objectiveId;

    @Column(name = "milestone_id")
    private UUID milestoneId;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "estimated_minutes")
    private Integer estimatedMinutes;

    @Column(name = "priority_score", precision = 6, scale = 4)
    private BigDecimal priorityScore;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "priority_reason", columnDefinition = "jsonb")
    private PriorityReason priorityReason;

    @Column(name = "status", nullable = false, length = 20)
    @Convert(converter = TaskStatus.JpaConverter.class)
    private TaskStatus status = TaskStatus.TODO;

    @Column(name = "due_at")
    private Instant dueAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    public static Task create(UUID userId, UUID objectiveId, UUID milestoneId, String title,
                              String description, Integer estimatedMinutes, Instant dueAt) {
        Task t = new Task();
        t.userId = userId;
        t.objectiveId = objectiveId;
        t.milestoneId = milestoneId;
        t.title = title;
        t.description = description;
        t.estimatedMinutes = estimatedMinutes;
        t.dueAt = dueAt;
        t.status = TaskStatus.TODO;
        return t;
    }

    /** Effort used for planning; {@link #estimatedMinutes} or the {@code defaultMinutes} fallback. */
    public int effortMinutes(int defaultMinutes) {
        return estimatedMinutes != null && estimatedMinutes > 0 ? estimatedMinutes : defaultMinutes;
    }

    public void markComplete(Instant when) {
        this.status = TaskStatus.DONE;
        this.completedAt = when;
    }

    public void reopen() {
        this.status = TaskStatus.TODO;
        this.completedAt = null;
    }

    public void applyPriority(PriorityReason reason) {
        this.priorityReason = reason;
        this.priorityScore = BigDecimal.valueOf(reason.score());
    }
}
