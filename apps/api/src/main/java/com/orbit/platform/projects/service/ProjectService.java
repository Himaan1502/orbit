package com.orbit.platform.projects.service;

import com.orbit.platform.common.exception.ApiException;
import com.orbit.platform.identity.service.CurrentUserService;
import com.orbit.platform.objectives.service.ObjectiveLookup;
import com.orbit.platform.projects.model.Project;
import com.orbit.platform.projects.model.ProjectSkill;
import com.orbit.platform.projects.model.ProjectStatus;
import com.orbit.platform.projects.progress.ProjectProgressCalculator;
import com.orbit.platform.projects.progress.ProjectProgressCalculator.ProjectProgress;
import com.orbit.platform.projects.progress.ProjectProgressCalculator.SkillContribution;
import com.orbit.platform.projects.progress.ProjectProgressCalculator.TaskTally;
import com.orbit.platform.projects.repository.ProjectRepository;
import com.orbit.platform.projects.repository.ProjectSkillRepository;
import com.orbit.platform.projects.web.dto.ProjectDtos.CreateProjectRequest;
import com.orbit.platform.projects.web.dto.ProjectDtos.ProjectSkillInput;
import com.orbit.platform.projects.web.dto.ProjectDtos.UpdateProjectRequest;
import com.orbit.platform.skills.service.SkillCatalog;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * CRUD for the caller's projects and their weighted skill sets (Blueprint §14,
 * ADR-024). Ownership enforced on every op; a project the caller does not own is
 * answered {@code 404} (the M1 pattern). A linked objective must belong to the
 * caller; each skill must be a catalogue skill.
 *
 * <p>{@code project_skills.demonstrated_contribution} is derived (ADR-026 §3) and
 * is never set from a request body.
 */
@Service
public class ProjectService {

    private final ProjectRepository projects;
    private final ProjectSkillRepository projectSkills;
    private final ObjectiveLookup objectives;
    private final SkillCatalog skillCatalog;
    private final ProjectTaskStats taskStats;
    private final CurrentUserService currentUser;

    public ProjectService(ProjectRepository projects, ProjectSkillRepository projectSkills,
                          ObjectiveLookup objectives, SkillCatalog skillCatalog,
                          ProjectTaskStats taskStats, CurrentUserService currentUser) {
        this.projects = projects;
        this.projectSkills = projectSkills;
        this.objectives = objectives;
        this.skillCatalog = skillCatalog;
        this.taskStats = taskStats;
        this.currentUser = currentUser;
    }

    public record ProjectWithSkills(Project project, List<ProjectSkill> skills) {
    }

    @Transactional(readOnly = true)
    public List<Project> listMine() {
        return projects.findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(currentUser.currentUserId());
    }

    @Transactional(readOnly = true)
    public ProjectWithSkills getMine(UUID id) {
        Project p = projects.findByIdAndUserIdAndDeletedAtIsNull(id, currentUser.currentUserId())
                .orElseThrow(() -> ApiException.notFound("Project not found."));
        return new ProjectWithSkills(p, projectSkills.findByProjectId(id));
    }

    /** Derived progress for a project (ADR-024 §6). */
    @Transactional(readOnly = true)
    public ProjectProgress progressOf(ProjectWithSkills pws) {
        TaskTally tally = taskStats.forProject(pws.project().getId());
        List<SkillContribution> contributions = pws.skills().stream()
                .map(s -> new SkillContribution(s.expectedValue(), s.demonstratedValue()))
                .toList();
        return ProjectProgressCalculator.compute(tally, contributions);
    }

    /** The linked objective's title, or {@code null}. */
    @Transactional(readOnly = true)
    public String objectiveTitleOf(Project p) {
        if (p.getObjectiveId() == null) {
            return null;
        }
        return objectives.findOwned(p.getObjectiveId(), p.getUserId())
                .map(ObjectiveLookup.Snapshot::title).orElse(null);
    }

    @Transactional
    public ProjectWithSkills create(CreateProjectRequest req) {
        UUID userId = currentUser.getOrProvision().getId();
        requireOwnedObjective(req.objectiveId(), userId);

        Project saved = projects.save(Project.create(
                userId, req.objectiveId(), req.name().strip(), strip(req.description()),
                strip(req.repositoryUrl()), strip(req.demoUrl())));
        if (req.skills() != null && !req.skills().isEmpty()) {
            replaceSkills(saved.getId(), req.skills());
        }
        return getMine(saved.getId());
    }

    @Transactional
    public ProjectWithSkills update(UUID id, UpdateProjectRequest req) {
        Project p = getMine(id).project();
        UUID userId = p.getUserId();

        if (req.name() != null && !req.name().isBlank()) {
            p.setName(req.name().strip());
        }
        if (req.description() != null) {
            p.setDescription(strip(req.description()));
        }
        if (req.objectiveId() != null) {
            requireOwnedObjective(req.objectiveId(), userId);
            p.setObjectiveId(req.objectiveId());
        }
        if (req.repositoryUrl() != null) {
            p.setRepositoryUrl(strip(req.repositoryUrl()));
        }
        if (req.demoUrl() != null) {
            p.setDemoUrl(strip(req.demoUrl()));
        }
        if (req.status() != null && !req.status().isBlank()) {
            p.transitionTo(ProjectStatus.fromValue(req.status()));
        }
        projects.save(p);

        if (req.skills() != null) {
            replaceSkills(id, req.skills());
        }
        return getMine(id);
    }

    @Transactional
    public void delete(UUID id) {
        Project p = getMine(id).project();
        p.softDelete();
        projects.save(p);
    }

    private void replaceSkills(UUID projectId, List<ProjectSkillInput> inputs) {
        for (ProjectSkillInput in : inputs) {
            if (!skillCatalog.exists(in.skillId())) {
                throw ApiException.notFound("Skill not found: " + in.skillId());
            }
        }
        projectSkills.deleteByProjectId(projectId);
        projectSkills.flush();
        for (ProjectSkillInput in : inputs) {
            projectSkills.save(ProjectSkill.of(projectId, in.skillId(),
                    in.expectedContribution() == null ? null : BigDecimal.valueOf(in.expectedContribution())));
        }
    }

    private void requireOwnedObjective(UUID objectiveId, UUID userId) {
        if (objectiveId != null && objectives.findOwned(objectiveId, userId).isEmpty()) {
            throw ApiException.notFound("Objective not found.");
        }
    }

    private static String strip(String s) {
        return s == null ? null : s.strip();
    }
}
