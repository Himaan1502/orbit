package com.orbit.platform.execution.service;

import com.orbit.platform.common.exception.ApiException;
import com.orbit.platform.execution.model.Task;
import com.orbit.platform.execution.model.TaskStatus;
import com.orbit.platform.execution.repository.TaskRepository;
import com.orbit.platform.execution.web.dto.TaskDtos.CreateTaskRequest;
import com.orbit.platform.execution.web.dto.TaskDtos.UpdateTaskRequest;
import com.orbit.platform.identity.service.CurrentUserService;
import com.orbit.platform.objectives.service.ObjectiveLookup;
import com.orbit.platform.objectives.service.ObjectiveProgressService;
import com.orbit.platform.projects.service.ProjectLookup;
import com.orbit.platform.skills.service.SkillCatalog;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

/**
 * Task use-cases. Ownership is enforced by {@code tasks.user_id} scoping on
 * every lookup; a task the caller does not own is answered {@code 404}
 * (ADR-016 §5). A task's objective must belong to the caller, and its milestone
 * (if any) must belong to that objective. Every mutation reprioritises the task
 * and recomputes the parent objective's progress inline (ADR-015 §4).
 */
@Service
public class TaskService {

    private final TaskRepository tasks;
    private final CurrentUserService currentUser;
    private final ObjectiveLookup objectives;
    private final ObjectiveProgressService objectiveProgress;
    private final TaskScoringService scoring;
    private final SkillCatalog skillCatalog;
    private final ProjectLookup projects;
    private final Clock clock;

    public TaskService(TaskRepository tasks, CurrentUserService currentUser, ObjectiveLookup objectives,
                       ObjectiveProgressService objectiveProgress, TaskScoringService scoring,
                       SkillCatalog skillCatalog, ProjectLookup projects, Clock clock) {
        this.tasks = tasks;
        this.currentUser = currentUser;
        this.objectives = objectives;
        this.objectiveProgress = objectiveProgress;
        this.scoring = scoring;
        this.skillCatalog = skillCatalog;
        this.projects = projects;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<Task> listMine() {
        return tasks.findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(currentUser.currentUserId());
    }

    @Transactional(readOnly = true)
    public Task getMine(UUID id) {
        return tasks.findByIdAndUserIdAndDeletedAtIsNull(id, currentUser.currentUserId())
                .orElseThrow(() -> ApiException.notFound("Task not found."));
    }

    @Transactional
    public Task create(CreateTaskRequest req) {
        UUID userId = currentUser.getOrProvision().getId();
        requireOwnedObjective(req.objectiveId(), userId);
        requireMilestoneInObjective(req.milestoneId(), req.objectiveId());
        requireSkillExists(req.skillId());
        requireOwnedProject(req.projectId(), userId);

        Task task = Task.create(userId, req.objectiveId(), req.milestoneId(), req.skillId(), req.projectId(),
                req.title().strip(), strip(req.description()), req.estimatedMinutes(), req.dueAt());
        scoring.rescore(task, userId);
        task = tasks.save(task);
        objectiveProgress.recompute(req.objectiveId());
        return task;
    }

    @Transactional
    public Task update(UUID id, UpdateTaskRequest req) {
        UUID userId = currentUser.currentUserId();
        Task task = getMine(id);

        if (req.title() != null && !req.title().isBlank()) {
            task.setTitle(req.title().strip());
        }
        if (req.description() != null) {
            task.setDescription(strip(req.description()));
        }
        if (req.estimatedMinutes() != null) {
            task.setEstimatedMinutes(req.estimatedMinutes());
        }
        if (req.dueAt() != null) {
            task.setDueAt(req.dueAt());
        }
        if (req.milestoneId() != null) {
            requireMilestoneInObjective(req.milestoneId(), task.getObjectiveId());
            task.setMilestoneId(req.milestoneId());
        }
        if (req.skillId() != null) {
            requireSkillExists(req.skillId());
            task.setSkillId(req.skillId());
        }
        if (req.projectId() != null) {
            requireOwnedProject(req.projectId(), userId);
            task.setProjectId(req.projectId());
        }
        if (req.status() != null && !req.status().isBlank()) {
            TaskStatus next = TaskStatus.fromValue(req.status());
            boolean wasTerminal = task.getStatus().isTerminal();
            task.changeStatus(next, clock.instant());
            if (wasTerminal && !next.isTerminal()) {
                task.setCompletedAt(null);
            }
        }

        scoring.rescore(task, userId);
        task = tasks.save(task);
        objectiveProgress.recompute(task.getObjectiveId());
        return task;
    }

    @Transactional
    public Task complete(UUID id) {
        Task task = getMine(id);
        task.markComplete(clock.instant());
        scoring.rescore(task, currentUser.currentUserId());
        task = tasks.save(task);
        objectiveProgress.recompute(task.getObjectiveId());
        return task;
    }

    @Transactional
    public void delete(UUID id) {
        Task task = getMine(id);
        task.softDelete();
        tasks.save(task);
        objectiveProgress.recompute(task.getObjectiveId());
    }

    private void requireOwnedObjective(UUID objectiveId, UUID userId) {
        if (objectives.findOwned(objectiveId, userId).isEmpty()) {
            throw ApiException.notFound("Objective not found.");
        }
    }

    private void requireMilestoneInObjective(UUID milestoneId, UUID objectiveId) {
        if (milestoneId != null && !objectives.milestoneBelongsTo(milestoneId, objectiveId)) {
            throw ApiException.notFound("Milestone not found.");
        }
    }

    private void requireSkillExists(UUID skillId) {
        if (skillId != null && !skillCatalog.exists(skillId)) {
            throw ApiException.notFound("Skill not found.");
        }
    }

    private void requireOwnedProject(UUID projectId, UUID userId) {
        if (projectId != null && !projects.existsForUser(projectId, userId)) {
            throw ApiException.notFound("Project not found.");
        }
    }

    private static String strip(String s) {
        return s == null ? null : s.strip();
    }
}
