package com.orbit.platform.projects.service;

import com.orbit.platform.projects.model.Project;
import com.orbit.platform.projects.model.ProjectSkill;
import com.orbit.platform.projects.model.ProjectStatus;
import com.orbit.platform.projects.progress.DemonstratedContributionCalculator;
import com.orbit.platform.projects.repository.ProjectRepository;
import com.orbit.platform.projects.repository.ProjectSkillRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Keeps {@code project_skills.demonstrated_contribution} derived (ADR-026 §3):
 * recomputed inline whenever a project's status changes, its skill set changes,
 * or its evidence links change (the M2 "recompute progress inline" pattern).
 */
@Service
public class ProjectDemonstratedContributionService implements ProjectDemonstratedRecompute {

    private final ProjectRepository projects;
    private final ProjectSkillRepository projectSkills;
    private final ProjectSkillEvidenceStrength evidenceStrength;

    public ProjectDemonstratedContributionService(ProjectRepository projects,
                                                  ProjectSkillRepository projectSkills,
                                                  ProjectSkillEvidenceStrength evidenceStrength) {
        this.projects = projects;
        this.projectSkills = projectSkills;
        this.evidenceStrength = evidenceStrength;
    }

    @Override
    @Transactional
    public void recompute(UUID projectId) {
        Project project = projects.findById(projectId).orElse(null);
        if (project == null || project.getDeletedAt() != null) {
            return;
        }
        ProjectStatus status = project.getStatus();
        for (ProjectSkill ps : projectSkills.findByProjectId(projectId)) {
            double strength = evidenceStrength.strength(projectId, ps.getSkillId());
            double demonstrated = DemonstratedContributionCalculator.demonstrated(
                    status, ps.expectedValue(), strength);
            if (Math.abs(demonstrated - ps.demonstratedValue()) > 1e-6) {
                ps.setDemonstratedContribution(BigDecimal.valueOf(demonstrated));
                projectSkills.save(ps);
            }
        }
    }
}
