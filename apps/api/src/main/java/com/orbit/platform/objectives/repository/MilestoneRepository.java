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

    /** Milestones under a user's objectives that reached {@code completed} within the window (Weekly Review). */
    @org.springframework.data.jpa.repository.Query("""
            select count(m) from Milestone m
              join Objective o on o.id = m.objectiveId
            where o.userId = :userId and m.deletedAt is null
              and m.status = com.orbit.platform.objectives.model.MilestoneStatus.COMPLETED
              and m.updatedAt >= :from and m.updatedAt < :to
            """)
    long countCompletedInWindow(@org.springframework.data.repository.query.Param("userId") UUID userId,
                                @org.springframework.data.repository.query.Param("from") java.time.Instant from,
                                @org.springframework.data.repository.query.Param("to") java.time.Instant to);

    long countByObjectiveIdAndDeletedAtIsNull(UUID objectiveId);
}
