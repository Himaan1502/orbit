package com.orbit.platform.career.service;

import com.orbit.platform.career.model.CareerTarget;
import com.orbit.platform.career.model.CareerTargetSkill;
import com.orbit.platform.career.repository.CareerTargetSkillRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
class PrimaryCareerSkillWeightsImpl implements PrimaryCareerSkillWeights {

    private final CareerTargetService targets;
    private final CareerTargetSkillRepository targetSkills;

    PrimaryCareerSkillWeightsImpl(CareerTargetService targets, CareerTargetSkillRepository targetSkills) {
        this.targets = targets;
        this.targetSkills = targetSkills;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, Double> importanceBySkill(UUID userId) {
        CareerTarget primary = targets.primaryOf(userId);
        if (primary == null) {
            return Map.of();
        }
        return targetSkills.findByCareerTargetId(primary.getId()).stream()
                .collect(Collectors.toMap(CareerTargetSkill::getSkillId, CareerTargetSkill::importanceValue));
    }
}
