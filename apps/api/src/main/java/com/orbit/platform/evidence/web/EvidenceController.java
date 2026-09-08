package com.orbit.platform.evidence.web;

import com.orbit.platform.common.dto.ApiResponse;
import com.orbit.platform.evidence.model.EvidenceType;
import com.orbit.platform.evidence.model.ProjectEvidence;
import com.orbit.platform.evidence.model.SkillEvidence;
import com.orbit.platform.evidence.service.EvidenceService;
import com.orbit.platform.evidence.service.EvidenceService.EvidenceWithLinks;
import com.orbit.platform.evidence.web.dto.EvidenceDtos.CreateEvidenceRequest;
import com.orbit.platform.evidence.web.dto.EvidenceDtos.EvidenceResponse;
import com.orbit.platform.evidence.web.dto.EvidenceDtos.UpdateEvidenceRequest;
import com.orbit.platform.projects.service.ProjectNames;
import com.orbit.platform.skills.service.SkillCatalog;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** {@code /api/v1/evidence} (Blueprint §20). Evidence is the caller's own. */
@RestController
@RequestMapping("/api/v1/evidence")
public class EvidenceController {

    private final EvidenceService evidence;
    private final SkillCatalog skillCatalog;
    private final ProjectNames projectNames;

    public EvidenceController(EvidenceService evidence, SkillCatalog skillCatalog, ProjectNames projectNames) {
        this.evidence = evidence;
        this.skillCatalog = skillCatalog;
        this.projectNames = projectNames;
    }

    @GetMapping
    public ApiResponse<List<EvidenceResponse>> list(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) UUID skillId,
            @RequestParam(required = false) UUID projectId) {
        EvidenceType t = type == null || type.isBlank() ? null : EvidenceType.fromValue(type);
        List<EvidenceWithLinks> rows = evidence.listMine(t, skillId, projectId);
        return ApiResponse.of(render(rows));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<EvidenceResponse> create(@Valid @RequestBody CreateEvidenceRequest req) {
        return ApiResponse.of(render(List.of(evidence.create(req))).get(0));
    }

    @GetMapping("/{id}")
    public ApiResponse<EvidenceResponse> get(@PathVariable UUID id) {
        return ApiResponse.of(render(List.of(evidence.getMine(id))).get(0));
    }

    @PatchMapping("/{id}")
    public ApiResponse<EvidenceResponse> update(@PathVariable UUID id,
                                                @Valid @RequestBody UpdateEvidenceRequest req) {
        return ApiResponse.of(render(List.of(evidence.update(id, req))).get(0));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        evidence.delete(id);
    }

    private List<EvidenceResponse> render(List<EvidenceWithLinks> rows) {
        Set<UUID> skillIds = rows.stream().flatMap(r -> r.skills().stream())
                .map(SkillEvidence::getSkillId).collect(Collectors.toSet());
        Set<UUID> projectIds = rows.stream().flatMap(r -> r.projects().stream())
                .map(ProjectEvidence::getProjectId).collect(Collectors.toSet());
        Map<UUID, String> skillNames = skillCatalog.namesByIds(skillIds);
        Map<UUID, String> projNames = projectNames.namesByIds(projectIds);
        return rows.stream().map(r -> EvidenceResponse.from(r, skillNames, projNames)).toList();
    }
}
