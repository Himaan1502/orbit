package com.orbit.platform.projects.service;

import com.orbit.platform.projects.model.ProjectSkill;
import com.orbit.platform.projects.repository.ProjectSkillRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Component
class ProjectSkillCoverageImpl implements ProjectSkillCoverage {

    private final ProjectSkillRepository projectSkills;

    ProjectSkillCoverageImpl(ProjectSkillRepository projectSkills) {
        this.projectSkills = projectSkills;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, Double> demonstratedBySkill(UUID userId, Collection<UUID> skillIds) {
        return cap(projectSkills.findForUserAndSkills(userId, skillIds), skillIds);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, Double> demonstratedBySkillAsOf(UUID userId, Collection<UUID> skillIds,
                                                     java.time.Instant asOf) {
        return cap(projectSkills.findForUserAndSkillsCompletedBy(userId, skillIds, asOf), skillIds);
    }

    private static Map<UUID, Double> cap(java.util.List<ProjectSkill> rows, Collection<UUID> skillIds) {
        if (skillIds == null || skillIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, Double> sum = new HashMap<>();
        for (ProjectSkill ps : rows) {
            sum.merge(ps.getSkillId(), ps.demonstratedValue(), Double::sum);
        }
        sum.replaceAll((k, v) -> Math.min(1.0, v));
        return sum;
    }
}
