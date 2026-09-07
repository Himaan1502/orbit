package com.orbit.platform.execution.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/** A task's slot in a {@link DailyPlan} (Blueprint §10 {@code daily_plan_tasks}). */
@Entity
@Table(name = "daily_plan_tasks", schema = "orbit")
@IdClass(DailyPlanTask.Key.class)
@Getter
@Setter
@NoArgsConstructor
public class DailyPlanTask {

    @Id
    @Column(name = "daily_plan_id", nullable = false, updatable = false)
    private UUID dailyPlanId;

    @Id
    @Column(name = "task_id", nullable = false, updatable = false)
    private UUID taskId;

    @Column(name = "position", nullable = false)
    private int position;

    @Column(name = "allocated_minutes", nullable = false)
    private int allocatedMinutes;

    public static DailyPlanTask of(UUID dailyPlanId, UUID taskId, int position, int allocatedMinutes) {
        DailyPlanTask e = new DailyPlanTask();
        e.dailyPlanId = dailyPlanId;
        e.taskId = taskId;
        e.position = position;
        e.allocatedMinutes = allocatedMinutes;
        return e;
    }

    public static final class Key implements Serializable {
        private UUID dailyPlanId;
        private UUID taskId;

        public Key() {
        }

        public Key(UUID dailyPlanId, UUID taskId) {
            this.dailyPlanId = dailyPlanId;
            this.taskId = taskId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Key key)) {
                return false;
            }
            return Objects.equals(dailyPlanId, key.dailyPlanId) && Objects.equals(taskId, key.taskId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(dailyPlanId, taskId);
        }
    }
}
