package com.orbit.platform.objectives.model;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * An append-only record of an objective's progress/status at a point in time
 * (ADR-027 §4). Written by {@code ObjectiveProgressService} inside the existing
 * inline recompute, only when progress or status actually moves. Powers the
 * objective-health trend on Analytics.
 */
@Entity
@Table(name = "objective_progress_events", schema = "orbit")
@Getter
@NoArgsConstructor
public class ObjectiveProgressEvent {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "objective_id", nullable = false, updatable = false)
    private UUID objectiveId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "progress", nullable = false)
    private BigDecimal progress;

    @Column(name = "status", nullable = false, length = 20)
    @Convert(converter = ObjectiveStatus.JpaConverter.class)
    private ObjectiveStatus status;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    public static ObjectiveProgressEvent of(UUID objectiveId, UUID userId, BigDecimal progress,
                                            ObjectiveStatus status, Instant recordedAt) {
        ObjectiveProgressEvent e = new ObjectiveProgressEvent();
        e.id = UUID.randomUUID();
        e.objectiveId = objectiveId;
        e.userId = userId;
        e.progress = progress;
        e.status = status;
        e.recordedAt = recordedAt;
        return e;
    }
}
