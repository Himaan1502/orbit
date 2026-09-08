package com.orbit.platform.evidence.web;

import com.orbit.platform.common.dto.ApiResponse;
import com.orbit.platform.evidence.model.ProjectEvidence;
import com.orbit.platform.evidence.model.SkillEvidence;
import com.orbit.platform.evidence.service.EvidenceService;
import com.orbit.platform.evidence.service.EvidenceService.EvidenceWithLinks;
import com.orbit.platform.evidence.web.dto.EvidenceDtos.EvidenceResponse;
import com.orbit.platform.projects.service.ProjectNames;
import com.orbit.platform.skills.service.SkillCatalog;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * {@code GET /api/v1/skills/{id}/evidence} (Blueprint §20) — the evidence a user
 * has logged for one skill. Drives the Skill detail "Evidence" section.
 */
@RestController
public class SkillEvidenceController {

    private final EvidenceService evidence;
    private final SkillCatalog skillCatalog;
    private final ProjectNames projectNames;

    public SkillEvidenceController(EvidenceService evidence, SkillCatalog skillCatalog,
                                   ProjectNames projectNames) {
        this.evidence = evidence;
        this.skillCatalog = skillCatalog;
        this.projectNames = projectNames;
    }

    @GetMapping("/api/v1/skills/{skillId}/evidence")
    public ApiResponse<List<EvidenceResponse>> forSkill(@PathVariable UUID skillId) {
        List<EvidenceWithLinks> rows = evidence.forSkill(skillId);
        Set<UUID> skillIds = rows.stream().flatMap(r -> r.skills().stream())
                .map(SkillEvidence::getSkillId).collect(Collectors.toSet());
        Set<UUID> projectIds = rows.stream().flatMap(r -> r.projects().stream())
                .map(ProjectEvidence::getProjectId).collect(Collectors.toSet());
        Map<UUID, String> skillNames = skillCatalog.namesByIds(skillIds);
        Map<UUID, String> projNames = projectNames.namesByIds(projectIds);
        return ApiResponse.of(rows.stream()
                .map(r -> EvidenceResponse.from(r, skillNames, projNames)).toList());
    }
}
