package com.orbit.platform.objectives.repository;

import com.orbit.platform.objectives.model.Milestone;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MilestoneRepository extends JpaRepository<Milestone, UUID> {

    List<Milestone> findByObjectiveIdAndDeletedAtIsNullOrderBySortOrderAscCreatedAtAsc(UUID objectiveId);

    Optional<Milestone> findByIdAndObjectiveIdAndDeletedAtIsNull(UUID id, UUID objectiveId);

    long countByObjectiveIdAndDeletedAtIsNull(UUID objectiveId);
}
