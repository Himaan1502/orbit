package com.orbit.platform.career.service;

import com.orbit.platform.career.repository.CareerTargetRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
class CareerTargetLookupImpl implements CareerTargetLookup {

    private final CareerTargetRepository targets;

    CareerTargetLookupImpl(CareerTargetRepository targets) {
        this.targets = targets;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsForUser(UUID careerTargetId, UUID userId) {
        return careerTargetId != null && userId != null
                && targets.findByIdAndUserIdAndDeletedAtIsNull(careerTargetId, userId).isPresent();
    }
}
