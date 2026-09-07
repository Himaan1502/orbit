package com.orbit.platform.execution.web.dto;

import com.orbit.platform.execution.model.Task;
import com.orbit.platform.execution.priority.PriorityReason;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Request / response shapes for {@code /api/v1/tasks}. */
public final class TaskDtos {

    private TaskDtos() {
    }

    public record CreateTaskRequest(
            @NotNull(message = "objectiveId is required.")
            UUID objectiveId,

            UUID milestoneId,

            /** Optional link to a catalogue skill — drives the career/skill priority factors. */
            UUID skillId,

            @NotBlank(message = "Title is required.")
            @Size(max = 200, message = "Title must be at most 200 characters.")
            String title,

            @Size(max = 5000, message = "Description is too long.")
            String description,

            @Positive(message = "estimatedMinutes must be positive.")
            Integer estimatedMinutes,

            Instant dueAt) {
    }

    public record UpdateTaskRequest(
            @Size(max = 200, message = "Title must be at most 200 characters.")
            String title,

            @Size(max = 5000, message = "Description is too long.")
            String description,

            @Positive(message = "estimatedMinutes must be positive.")
            Integer estimatedMinutes,

            Instant dueAt,

            UUID milestoneId,

            UUID skillId,

            @Pattern(regexp = "todo|in_progress|blocked|skipped",
                    message = "Use POST /tasks/{id}/complete to complete a task.")
            String status) {
    }

    public record TaskResponse(
            UUID id,
            UUID objectiveId,
            UUID milestoneId,
            UUID skillId,
            String title,
            String description,
            Integer estimatedMinutes,
            String status,
            BigDecimal priorityScore,
            PriorityReason priorityReason,
            Instant dueAt,
            Instant completedAt,
            Instant createdAt,
            Instant updatedAt) {

        public static TaskResponse from(Task t) {
            return new TaskResponse(
                    t.getId(), t.getObjectiveId(), t.getMilestoneId(), t.getSkillId(), t.getTitle(),
                    t.getDescription(), t.getEstimatedMinutes(), t.getStatus().value(), t.getPriorityScore(),
                    t.getPriorityReason(), t.getDueAt(), t.getCompletedAt(),
                    t.getCreatedAt(), t.getUpdatedAt());
        }
    }
}
