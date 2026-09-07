package com.orbit.platform.execution.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A generated (or curated) plan of tasks for one day (Blueprint §10, ADR-015).
 * Exactly one per {@code (user_id, plan_date)}.
 */
@Entity
@Table(name = "daily_plans", schema = "orbit")
@Getter
@Setter
@NoArgsConstructor
public class DailyPlan {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "plan_date", nullable = false, updatable = false)
    private LocalDate planDate;

    @Column(name = "available_minutes", nullable = false)
    private int availableMinutes;

    @Column(name = "status", nullable = false, length = 20)
    @Convert(converter = DailyPlanStatus.JpaConverter.class)
    private DailyPlanStatus status = DailyPlanStatus.DRAFT;

    @Column(name = "generated_by", nullable = false, length = 20)
    private String generatedBy = "system";

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "dailyPlanId", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    private List<DailyPlanTask> entries = new ArrayList<>();

    public static DailyPlan create(UUID userId, LocalDate planDate, int availableMinutes) {
        DailyPlan p = new DailyPlan();
        p.id = UUID.randomUUID();
        p.userId = userId;
        p.planDate = planDate;
        p.availableMinutes = availableMinutes;
        p.status = DailyPlanStatus.DRAFT;
        p.generatedBy = "system";
        return p;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public int allocatedMinutes() {
        return entries.stream().mapToInt(DailyPlanTask::getAllocatedMinutes).sum();
    }

    public void replaceEntries(List<DailyPlanTask> next) {
        entries.clear();
        entries.addAll(next);
    }
}
