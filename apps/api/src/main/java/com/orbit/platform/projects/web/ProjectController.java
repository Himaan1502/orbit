package com.orbit.platform.projects.web;

import com.orbit.platform.common.dto.ApiResponse;
import com.orbit.platform.projects.model.ProjectSkill;
import com.orbit.platform.projects.service.ProjectEvidenceCount;
import com.orbit.platform.projects.service.ProjectService;
import com.orbit.platform.projects.service.ProjectService.ProjectWithSkills;
import com.orbit.platform.projects.web.dto.ProjectDtos.CreateProjectRequest;
import com.orbit.platform.projects.web.dto.ProjectDtos.ProjectResponse;
import com.orbit.platform.projects.web.dto.ProjectDtos.UpdateProjectRequest;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** {@code /api/v1/projects} (Blueprint §20). Projects are the caller's own. */
@RestController
@RequestMapping("/api/v1/projects")
public class ProjectController {

    private final ProjectService projects;
    private final SkillCatalog skillCatalog;
    private final ProjectEvidenceCount evidenceCount;

    public ProjectController(ProjectService projects, SkillCatalog skillCatalog,
                             ProjectEvidenceCount evidenceCount) {
        this.projects = projects;
        this.skillCatalog = skillCatalog;
        this.evidenceCount = evidenceCount;
    }

    @GetMapping
    public ApiResponse<List<ProjectResponse>> list() {
        return ApiResponse.of(projects.listMine().stream()
                .map(p -> {
                    ProjectWithSkills pws = projects.getMine(p.getId());
                    return ProjectResponse.summary(p, projects.progressOf(pws),
                            projects.objectiveTitleOf(p), pws.skills().size(),
                            evidenceCount.forProject(p.getId()));
                })
                .toList());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ProjectResponse> create(@Valid @RequestBody CreateProjectRequest req) {
        return ApiResponse.of(detail(projects.create(req)));
    }

    @GetMapping("/{id}")
    public ApiResponse<ProjectResponse> get(@PathVariable UUID id) {
        return ApiResponse.of(detail(projects.getMine(id)));
    }

    @PatchMapping("/{id}")
    public ApiResponse<ProjectResponse> update(@PathVariable UUID id,
                                               @Valid @RequestBody UpdateProjectRequest req) {
        return ApiResponse.of(detail(projects.update(id, req)));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        projects.delete(id);
    }

    private ProjectResponse detail(ProjectWithSkills pws) {
        Map<UUID, String> names = skillCatalog.namesByIds(
                pws.skills().stream().map(ProjectSkill::getSkillId).toList());
        return ProjectResponse.from(pws, projects.progressOf(pws),
                projects.objectiveTitleOf(pws.project()),
                evidenceCount.forProject(pws.project().getId()), names);
    }
}
