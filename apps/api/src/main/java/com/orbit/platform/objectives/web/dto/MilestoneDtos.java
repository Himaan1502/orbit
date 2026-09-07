package com.orbit.platform.objectives.web.dto;

import com.orbit.platform.objectives.model.Milestone;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Request / response shapes for {@code /api/v1/objectives/{id}/milestones}. */
public final class MilestoneDtos {

    private MilestoneDtos() {
    }

    public record CreateMilestoneRequest(
            @NotBlank(message = "Title is required.")
            @Size(max = 200, message = "Title must be at most 200 characters.")
            String title,

            @Size(max = 5000, message = "Description is too long.")
            String description,

            LocalDate targetDate,

            Integer sortOrder) {
    }

    public record UpdateMilestoneRequest(
            @Size(max = 200, message = "Title must be at most 200 characters.")
            String title,

            @Size(max = 5000, message = "Description is too long.")
            String description,

            @Pattern(regexp = "pending|in_progress|completed", message = "Unknown status.")
            String status,

            LocalDate targetDate,

            Integer sortOrder) {
    }

    public record MilestoneResponse(
            UUID id,
            UUID objectiveId,
            String title,
            String description,
            String status,
            BigDecimal progress,
            LocalDate targetDate,
            int sortOrder,
            Instant createdAt,
            Instant updatedAt) {

        public static MilestoneResponse from(Milestone m, BigDecimal progress) {
            return new MilestoneResponse(
                    m.getId(), m.getObjectiveId(), m.getTitle(), m.getDescription(),
                    m.getStatus().value(), progress, m.getTargetDate(), m.getSortOrder(),
                    m.getCreatedAt(), m.getUpdatedAt());
        }
    }
}
