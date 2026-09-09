package com.orbit.platform.objectives.repository;

import com.orbit.platform.objectives.model.ObjectiveProgressEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface ObjectiveProgressEventRepository extends JpaRepository<ObjectiveProgressEvent, UUID> {

    List<ObjectiveProgressEvent> findByUserIdAndRecordedAtBetweenOrderByRecordedAtAsc(
            UUID userId, Instant from, Instant to);

    List<ObjectiveProgressEvent> findByObjectiveIdOrderByRecordedAtAsc(UUID objectiveId);
}
