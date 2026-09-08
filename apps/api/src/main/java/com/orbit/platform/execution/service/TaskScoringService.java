package com.orbit.platform.execution.service;

import com.orbit.platform.career.service.TaskCareerSignals;
import com.orbit.platform.execution.PlanningProperties;
import com.orbit.platform.execution.model.Task;
import com.orbit.platform.execution.model.TaskStatus;
import com.orbit.platform.execution.priority.PriorityEngine;
import com.orbit.platform.execution.priority.PriorityInput;
import com.orbit.platform.execution.priority.PriorityReason;
import com.orbit.platform.execution.repository.TaskRepository;
import com.orbit.platform.identity.service.CurrentUserService;
import com.orbit.platform.objectives.service.ObjectiveLookup;
import com.orbit.platform.projects.service.TaskProjectSignals;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Assembles a {@link PriorityInput} for a task from its objective, its skill,
 * the user's career target, planning budget and completion history, then applies
 * the {@link PriorityEngine}'s deterministic score. The engine is pure; this
 * service does the I/O.
 */
@Service
public class TaskScoringService {

    private final PriorityEngine engine;
    private final ObjectiveLookup objectives;
    private final TaskCareerSignals careerSignals;
    private final TaskProjectSignals projectSignals;
    private final TaskRepository tasks;
    private final CurrentUserService currentUser;
    private final PlanningProperties planning;
    private final Clock clock;

    public TaskScoringService(PriorityEngine engine, ObjectiveLookup objectives,
                              TaskCareerSignals careerSignals, TaskProjectSignals projectSignals,
                              TaskRepository tasks, CurrentUserService currentUser,
                              PlanningProperties planning, Clock clock) {
        this.engine = engine;
        this.objectives = objectives;
        this.careerSignals = careerSignals;
        this.projectSignals = projectSignals;
        this.tasks = tasks;
        this.currentUser = currentUser;
        this.planning = planning;
        this.clock = clock;
    }

    /** Recompute and persist {@code priority_score} / {@code priority_reason} for one task. */
    @Transactional
    public void rescore(Task task, UUID userId) {
        task.applyPriority(score(task, userId, clock.instant()));
    }

    @Transactional(readOnly = true)
    public PriorityReason score(Task task, UUID userId, Instant now) {
        int objectivePriority = 3;
        LocalDate objectiveTargetDate = null;
        UUID careerTargetId = null;
        if (task.getObjectiveId() != null) {
            var snapshot = objectives.findOwned(task.getObjectiveId(), userId).orElse(null);
            if (snapshot != null) {
                objectivePriority = snapshot.priority();
                objectiveTargetDate = snapshot.targetDate();
                careerTargetId = snapshot.careerTargetId();
            }
        }

        TaskCareerSignals.Signals signals = careerSignals.forTask(task.getSkillId(), careerTargetId, userId);
        Double projectImpact = projectSignals.projectImpact(
                task.getProjectId(), task.getSkillId(), userId);

        long resolved = tasks.countByUserIdAndStatusInAndDeletedAtIsNull(
                userId, List.of(TaskStatus.DONE, TaskStatus.SKIPPED));
        long done = tasks.countByUserIdAndStatusInAndDeletedAtIsNull(userId, List.of(TaskStatus.DONE));

        PriorityInput input = new PriorityInput(
                task.getDueAt(),
                objectiveTargetDate,
                objectivePriority,
                task.getEstimatedMinutes(),
                currentUser.availableDailyMinutes(planning.defaultAvailableMinutes()),
                resolved,
                done,
                signals.careerRelevance(),
                signals.skillGap(),
                signals.dependencyImpact(),
                signals.interviewValue(),
                projectImpact,
                now);
        return engine.score(input);
    }
}
