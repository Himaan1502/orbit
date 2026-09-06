package com.orbit.platform.objectives.web.dto;

import com.orbit.platform.objectives.model.Objective;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Request / response shapes for {@code /api/v1/objectives}. */
public final class ObjectiveDtos {

    private ObjectiveDtos() {
    }

    public record CreateObjectiveRequest(
            @NotBlank(message = "Title is required.")
            @Size(max = 200, message = "Title must be at most 200 characters.")
            String title,

            @Size(max = 5000, message = "Description is too long.")
            String description,

            LocalDate targetDate) {
    }

    public record UpdateObjectiveRequest(
            @Size(max = 200, message = "Title must be at most 200 characters.")
            String title,

            @Size(max = 5000, message = "Description is too long.")
            String description,

            @Pattern(regexp = "active|completed|archived", message = "Unknown status.")
            String status,

            LocalDate targetDate) {
    }

    public record ObjectiveResponse(
            UUID id,
            String title,
            String description,
            String status,
            LocalDate targetDate,
            Instant createdAt,
            Instant updatedAt) {

        public static ObjectiveResponse from(Objective o) {
            return new ObjectiveResponse(
                    o.getId(), o.getTitle(), o.getDescription(), o.getStatus(),
                    o.getTargetDate(), o.getCreatedAt(), o.getUpdatedAt());
        }
    }
}
