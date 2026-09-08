package com.orbit.platform.projects.service;

import com.orbit.platform.projects.model.Project;
import com.orbit.platform.projects.model.ProjectSkill;
import com.orbit.platform.projects.model.ProjectStatus;
import com.orbit.platform.projects.repository.ProjectRepository;
import com.orbit.platform.projects.repository.ProjectSkillRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Computes {@code projectImpact} for a task (ADR-026 §4): the
 * {@code expected_contribution} of the task's skill for the task's project,
 * softened when the project is not active, {@code null} (→ neutral) when the
 * task has no project or the project is done / archived.
 */
@Component
class TaskProjectSignalsImpl implements TaskProjectSignals {

    private final ProjectRepository projects;
    private final ProjectSkillRepository projectSkills;

    TaskProjectSignalsImpl(ProjectRepository projects, ProjectSkillRepository projectSkills) {
        this.projects = projects;
        this.projectSkills = projectSkills;
    }

    @Override
    @Transactional(readOnly = true)
    public Double projectImpact(UUID taskProjectId, UUID taskSkillId, UUID userId) {
        if (taskProjectId == null) {
            return null;
        }
        Project project = projects.findByIdAndUserIdAndDeletedAtIsNull(taskProjectId, userId).orElse(null);
        if (project == null
                || project.getStatus() == ProjectStatus.COMPLETED
                || project.getStatus() == ProjectStatus.ARCHIVED) {
            return null;   // finished / parked — nothing more to advance
        }

        List<ProjectSkill> skills = projectSkills.findByProjectId(taskProjectId);
        double base;
        if (taskSkillId != null) {
            base = skills.stream().filter(s -> s.getSkillId().equals(taskSkillId))
                    .mapToDouble(ProjectSkill::expectedValue).findFirst()
                    .orElse(skills.isEmpty() ? 0.4 : 0.3);
        } else if (!skills.isEmpty()) {
            base = skills.stream().mapToDouble(ProjectSkill::expectedValue).average().orElse(0.4);
        } else {
            base = 0.4;
        }

        double factor = project.getStatus() == ProjectStatus.ACTIVE ? 1.0 : 0.8;
        return clamp(base * factor);
    }

    private static double clamp(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }
}
