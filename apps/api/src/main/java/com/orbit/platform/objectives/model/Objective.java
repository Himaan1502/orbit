package com.orbit.platform.objectives.model;

import com.orbit.platform.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Minimal subset of Blueprint §9 {@code objectives}. Exists in Milestone 1 to
 * prove server-side resource-ownership enforcement (Tech Arch §9); the full
 * Objective Engine is a later milestone.
 */
@Entity
@Table(name = "objectives", schema = "orbit")
@Getter
@Setter
@NoArgsConstructor
public class Objective extends BaseEntity {

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "active";

    @Column(name = "target_date")
    private LocalDate targetDate;

    public static Objective create(UUID userId, String title, String description, LocalDate targetDate) {
        Objective o = new Objective();
        o.userId = userId;
        o.title = title;
        o.description = description;
        o.targetDate = targetDate;
        o.status = "active";
        return o;
    }
}
