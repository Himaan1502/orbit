package com.orbit.platform.projects.web.dto;

import com.orbit.platform.projects.model.Project;
import com.orbit.platform.projects.progress.ProjectProgressCalculator.ProjectProgress;
import com.orbit.platform.projects.service.ProjectService.ProjectWithSkills;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Request / response shapes for {@code /api/v1/projects} (Blueprint §14/§20, ADR-024). */
public final class ProjectDtos {

    private ProjectDtos() {
    }

    public record ProjectSkillInput(
            @NotNull UUID skillId,
            @DecimalMin("0") @DecimalMax("1") Double expectedContribution) {
    }

    public record CreateProjectRequest(
            @NotBlank @Size(max = 160) String name,
            @Size(max = 5000) String description,
            UUID objectiveId,
            @Size(max = 2000) String repositoryUrl,
            @Size(max = 2000) String demoUrl,
            @Valid List<ProjectSkillInput> skills) {
    }

    public record UpdateProjectRequest(
            @Size(max = 160) String name,
            @Size(max = 5000) String description,
            UUID objectiveId,
            @Size(max = 2000) String repositoryUrl,
            @Size(max = 2000) String demoUrl,
            @Pattern(regexp = "planned|active|completed|archived", message = "Unknown status.") String status,
            @Valid List<ProjectSkillInput> skills) {
    }

    public record ProjectSkillResponse(
            UUID skillId, String name, double expectedContribution, double demonstratedContribution) {
    }

    public record ProjectResponse(
            UUID id, String name, String description, String status,
            UUID objectiveId, String objectiveTitle,
            String repositoryUrl, String demoUrl,
            Instant startedAt, Instant completedAt, Instant createdAt, Instant updatedAt,
            int skillCount, int evidenceCount,
            Double taskProgress, Double skillsDemonstrated, double progress,
            List<ProjectSkillResponse> skills) {

        public static ProjectResponse from(ProjectWithSkills pws, ProjectProgress progress,
                                           String objectiveTitle, int evidenceCount,
                                           Map<UUID, String> skillNames) {
            Project p = pws.project();
            List<ProjectSkillResponse> skills = pws.skills().stream()
                    .map(s -> new ProjectSkillResponse(s.getSkillId(),
                            skillNames.getOrDefault(s.getSkillId(), "?"),
                            s.expectedValue(), s.demonstratedValue()))
                    .sorted((a, b) -> Double.compare(b.expectedContribution(), a.expectedContribution()))
                    .toList();
            return new ProjectResponse(
                    p.getId(), p.getName(), p.getDescription(), p.getStatus().value(),
                    p.getObjectiveId(), objectiveTitle, p.getRepositoryUrl(), p.getDemoUrl(),
                    p.getStartedAt(), p.getCompletedAt(), p.getCreatedAt(), p.getUpdatedAt(),
                    skills.size(), evidenceCount,
                    progress.taskProgress(), progress.skillsDemonstrated(), progress.overall(),
                    skills);
        }

        /** Lightweight row for the list — no per-project skill breakdown. */
        public static ProjectResponse summary(Project p, ProjectProgress progress, String objectiveTitle,
                                              int skillCount, int evidenceCount) {
            return new ProjectResponse(
                    p.getId(), p.getName(), p.getDescription(), p.getStatus().value(),
                    p.getObjectiveId(), objectiveTitle, p.getRepositoryUrl(), p.getDemoUrl(),
                    p.getStartedAt(), p.getCompletedAt(), p.getCreatedAt(), p.getUpdatedAt(),
                    skillCount, evidenceCount,
                    progress.taskProgress(), progress.skillsDemonstrated(), progress.overall(),
                    List.of());
        }
    }

    /** For the reverse lookup used by the Skill detail page / others. */
    public record ProjectRefResponse(UUID id, String name, String status) {
        public static ProjectRefResponse from(Project p) {
            return new ProjectRefResponse(p.getId(), p.getName(), p.getStatus().value());
        }
    }
}
