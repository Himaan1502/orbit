package com.orbit.platform.objectives.service;

import com.orbit.platform.objectives.model.ObjectiveStatus;
import com.orbit.platform.objectives.repository.MilestoneRepository;
import com.orbit.platform.objectives.repository.ObjectiveRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Component
class ObjectiveLookupImpl implements ObjectiveLookup {

    private final ObjectiveRepository objectives;
    private final MilestoneRepository milestones;

    ObjectiveLookupImpl(ObjectiveRepository objectives, MilestoneRepository milestones) {
        this.objectives = objectives;
        this.milestones = milestones;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Snapshot> findOwned(UUID objectiveId, UUID userId) {
        return objectives.findByIdAndUserIdAndDeletedAtIsNull(objectiveId, userId)
                .map(o -> new Snapshot(o.getId(), o.getTitle(), o.getPriority(), o.getTargetDate(),
                        o.getStatus() == ObjectiveStatus.ARCHIVED, o.getCareerTargetId()));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean milestoneBelongsTo(UUID milestoneId, UUID objectiveId) {
        return milestones.findByIdAndObjectiveIdAndDeletedAtIsNull(milestoneId, objectiveId).isPresent();
    }
}
