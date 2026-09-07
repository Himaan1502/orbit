package com.orbit.platform.objectives.repository;

import com.orbit.platform.objectives.model.Objective;
import com.orbit.platform.objectives.model.ObjectiveStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ObjectiveRepository extends JpaRepository<Objective, UUID> {

    List<Objective> findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID userId);

    List<Objective> findByUserIdAndStatusAndDeletedAtIsNullOrderByPriorityDescCreatedAtDesc(
            UUID userId, ObjectiveStatus status);

    Optional<Objective> findByIdAndUserIdAndDeletedAtIsNull(UUID id, UUID userId);
}
