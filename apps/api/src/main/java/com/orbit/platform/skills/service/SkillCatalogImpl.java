package com.orbit.platform.skills.service;

import com.orbit.platform.skills.model.Skill;
import com.orbit.platform.skills.repository.SkillRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
class SkillCatalogImpl implements SkillCatalog {

    private final SkillRepository skills;

    SkillCatalogImpl(SkillRepository skills) {
        this.skills = skills;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean exists(UUID skillId) {
        return skillId != null && skills.existsById(skillId);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, String> namesByIds(Collection<UUID> skillIds) {
        if (skillIds == null || skillIds.isEmpty()) {
            return Map.of();
        }
        return skills.findAllById(skillIds).stream()
                .collect(Collectors.toMap(Skill::getId, Skill::getCanonicalName));
    }
}
