package com.orbit.platform.evidence.web.dto;

import com.orbit.platform.evidence.model.Evidence;
import com.orbit.platform.evidence.model.ProjectEvidence;
import com.orbit.platform.evidence.model.SkillEvidence;
import com.orbit.platform.evidence.service.EvidenceService.EvidenceWithLinks;
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

/** Request / response shapes for {@code /api/v1/evidence} (Blueprint §13/§20, ADR-025). */
public final class EvidenceDtos {

    private EvidenceDtos() {
    }

    private static final String TYPES =
            "project|repository|deployment|benchmark|document|certification|assessment|talk|other";

    public record EvidenceSkillInput(
            @NotNull UUID skillId,
            @DecimalMin("0") @DecimalMax("1") Double contributionWeight) {
    }

    public record EvidenceProjectInput(@NotNull UUID projectId) {
    }

    public record CreateEvidenceRequest(
            @NotBlank @Pattern(regexp = TYPES, message = "Unknown evidence type.") String type,
            @NotBlank @Size(max = 200) String title,
            @Size(max = 5000) String description,
            @Size(max = 2000) String sourceUrl,
            @Size(max = 120) String externalIdentifier,
            Instant occurredAt,
            Map<String, Object> metadata,
            @Valid List<EvidenceSkillInput> skills,
            @Valid List<EvidenceProjectInput> projects) {
    }

    public record UpdateEvidenceRequest(
            @Pattern(regexp = TYPES, message = "Unknown evidence type.") String type,
            @Size(max = 200) String title,
            @Size(max = 5000) String description,
            @Size(max = 2000) String sourceUrl,
            @Size(max = 120) String externalIdentifier,
            Instant occurredAt,
            Map<String, Object> metadata,
            @Valid List<EvidenceSkillInput> skills,
            @Valid List<EvidenceProjectInput> projects) {
    }

    public record LinkedSkillResponse(UUID skillId, String name, double contributionWeight) {
    }

    public record LinkedProjectResponse(UUID projectId, String name) {
    }

    public record EvidenceResponse(
            UUID id, String type, String title, String description, String sourceUrl,
            String externalIdentifier, String verificationStatus, Map<String, Object> metadata,
            Instant occurredAt, Instant createdAt, Instant updatedAt,
            List<LinkedSkillResponse> skills, List<LinkedProjectResponse> projects) {

        public static EvidenceResponse from(EvidenceWithLinks ewl, Map<UUID, String> skillNames,
                                            Map<UUID, String> projectNames) {
            Evidence e = ewl.evidence();
            List<LinkedSkillResponse> skills = ewl.skills().stream()
                    .map(se -> new LinkedSkillResponse(se.getSkillId(),
                            skillNames.getOrDefault(se.getSkillId(), "?"), se.weight()))
                    .toList();
            List<LinkedProjectResponse> projects = ewl.projects().stream()
                    .map(pe -> new LinkedProjectResponse(pe.getProjectId(),
                            projectNames.getOrDefault(pe.getProjectId(), "?")))
                    .toList();
            return new EvidenceResponse(
                    e.getId(), e.getType().value(), e.getTitle(), e.getDescription(), e.getSourceUrl(),
                    e.getExternalIdentifier(), e.getVerificationStatus().value(), e.getMetadata(),
                    e.getOccurredAt(), e.getCreatedAt(), e.getUpdatedAt(), skills, projects);
        }

        /** List row — link ids only, no name resolution. */
        public static EvidenceResponse summary(Evidence e, List<SkillEvidence> skills,
                                               List<ProjectEvidence> projects) {
            return new EvidenceResponse(
                    e.getId(), e.getType().value(), e.getTitle(), e.getDescription(), e.getSourceUrl(),
                    e.getExternalIdentifier(), e.getVerificationStatus().value(), e.getMetadata(),
                    e.getOccurredAt(), e.getCreatedAt(), e.getUpdatedAt(),
                    skills.stream().map(se ->
                            new LinkedSkillResponse(se.getSkillId(), null, se.weight())).toList(),
                    projects.stream().map(pe ->
                            new LinkedProjectResponse(pe.getProjectId(), null)).toList());
        }
    }
}
