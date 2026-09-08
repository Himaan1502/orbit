package com.orbit.platform.projects.service;

import com.orbit.platform.projects.model.Project;
import com.orbit.platform.projects.repository.ProjectRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
class ProjectLookupImpl implements ProjectLookup, ProjectNames {

    private final ProjectRepository projects;

    ProjectLookupImpl(ProjectRepository projects) {
        this.projects = projects;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsForUser(UUID projectId, UUID userId) {
        return projectId != null && userId != null
                && projects.findByIdAndUserIdAndDeletedAtIsNull(projectId, userId).isPresent();
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, String> namesByIds(Collection<UUID> projectIds) {
        if (projectIds == null || projectIds.isEmpty()) {
            return Map.of();
        }
        return projects.findAllById(projectIds).stream()
                .collect(Collectors.toMap(Project::getId, Project::getName));
    }
}
