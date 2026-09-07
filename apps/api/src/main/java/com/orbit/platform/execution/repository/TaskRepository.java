package com.orbit.platform.execution.repository;

import com.orbit.platform.execution.model.Task;
import com.orbit.platform.execution.model.TaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TaskRepository extends JpaRepository<Task, UUID> {

    List<Task> findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID userId);

    Optional<Task> findByIdAndUserIdAndDeletedAtIsNull(UUID id, UUID userId);

    List<Task> findByObjectiveIdAndDeletedAtIsNullOrderByCreatedAtAsc(UUID objectiveId);

    List<Task> findByMilestoneIdAndDeletedAtIsNull(UUID milestoneId);

    List<Task> findByUserIdAndStatusInAndDeletedAtIsNull(UUID userId, Collection<TaskStatus> statuses);

    long countByUserIdAndStatusInAndDeletedAtIsNull(UUID userId, Collection<TaskStatus> statuses);
}
