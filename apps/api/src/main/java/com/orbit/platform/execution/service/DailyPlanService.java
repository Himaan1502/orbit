package com.orbit.platform.execution.service;

import com.orbit.platform.common.exception.ApiException;
import com.orbit.platform.execution.PlanningProperties;
import com.orbit.platform.execution.model.DailyPlan;
import com.orbit.platform.execution.model.DailyPlanTask;
import com.orbit.platform.execution.model.Task;
import com.orbit.platform.execution.model.TaskStatus;
import com.orbit.platform.execution.repository.DailyPlanRepository;
import com.orbit.platform.execution.repository.TaskRepository;
import com.orbit.platform.execution.web.dto.DailyPlanDtos.DailyPlanResponse;
import com.orbit.platform.execution.web.dto.DailyPlanDtos.GeneratePlanRequest;
import com.orbit.platform.execution.web.dto.DailyPlanDtos.PlanEntryResponse;
import com.orbit.platform.identity.service.CurrentUserService;
import com.orbit.platform.objectives.service.ObjectiveLookup;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Inline (synchronous) caller of {@link DailyPlanGenerator} (ADR-015 §1).
 * Transactional and ownership-aware; the generator itself does the pure
 * selection. A future durable job runner would call
 * {@code DailyPlanGenerator.generate(...)} directly with its own assembled
 * request — no code here needs extracting.
 */
@Service
public class DailyPlanService {

    private final DailyPlanRepository plans;
    private final TaskRepository tasks;
    private final DailyPlanGenerator generator;
    private final TaskScoringService scoring;
    private final ObjectiveLookup objectives;
    private final CurrentUserService currentUser;
    private final PlanningProperties planning;
    private final Clock clock;

    public DailyPlanService(DailyPlanRepository plans, TaskRepository tasks, DailyPlanGenerator generator,
                            TaskScoringService scoring, ObjectiveLookup objectives,
                            CurrentUserService currentUser, PlanningProperties planning, Clock clock) {
        this.plans = plans;
        this.tasks = tasks;
        this.generator = generator;
        this.scoring = scoring;
        this.objectives = objectives;
        this.currentUser = currentUser;
        this.planning = planning;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public DailyPlanResponse getForDate(LocalDate date) {
        DailyPlan plan = plans.findByUserIdAndPlanDate(currentUser.currentUserId(), date)
                .orElseThrow(() -> ApiException.notFound("No plan for " + date + "."));
        return toResponse(plan);
    }

    /** Non-throwing lookup for aggregate reads (e.g. the dashboard). */
    @Transactional(readOnly = true)
    public java.util.Optional<DailyPlanResponse> findForDate(LocalDate date) {
        return plans.findByUserIdAndPlanDate(currentUser.currentUserId(), date).map(this::toResponse);
    }

    @Transactional
    public DailyPlanResponse generate(GeneratePlanRequest req) {
        UUID userId = currentUser.getOrProvision().getId();
        LocalDate date = req.date() != null ? req.date() : LocalDate.now(clock);
        boolean force = Boolean.TRUE.equals(req.force());

        DailyPlan existing = plans.findByUserIdAndPlanDate(userId, date).orElse(null);
        if (existing != null && !force) {
            return toResponse(existing);                 // idempotent (ADR-015 §3)
        }

        int budget = req.availableMinutes() != null
                ? req.availableMinutes()
                : currentUser.availableDailyMinutes(planning.defaultAvailableMinutes());

        DailyPlan plan = existing != null ? existing : DailyPlan.create(userId, date, budget);
        plan.setAvailableMinutes(budget);
        rebuild(plan, userId, Set.of());
        return toResponse(plans.save(plan));
    }

    @Transactional
    public DailyPlanResponse regenerate(UUID planId) {
        UUID userId = currentUser.currentUserId();
        DailyPlan plan = plans.findByIdAndUserId(planId, userId)
                .orElseThrow(() -> ApiException.notFound("Plan not found."));

        // Tasks already completed in this plan keep their slot (ADR-015 §3).
        Set<UUID> completedInPlan = new java.util.HashSet<>();
        for (DailyPlanTask e : plan.getEntries()) {
            tasks.findByIdAndUserIdAndDeletedAtIsNull(e.getTaskId(), userId)
                    .filter(t -> t.getStatus() == TaskStatus.DONE)
                    .ifPresent(t -> completedInPlan.add(t.getId()));
        }
        rebuild(plan, userId, completedInPlan);
        return toResponse(plans.save(plan));
    }

    // --- internals -----------------------------------------------------

    private void rebuild(DailyPlan plan, UUID userId, Set<UUID> keepAsDone) {
        List<Task> candidates = candidateTasks(userId, keepAsDone);
        candidates.forEach(t -> scoring.rescore(t, userId));
        tasks.saveAll(candidates);

        List<DailyPlanGenerator.Candidate> genCandidates = candidates.stream()
                .map(t -> new DailyPlanGenerator.Candidate(
                        t.getId(),
                        t.getPriorityScore(),
                        t.getDueAt(),
                        t.getCreatedAt(),
                        t.effortMinutes(planning.defaultTaskMinutes()),
                        keepAsDone.contains(t.getId())))
                .toList();

        DailyPlanGenerator.GeneratedPlan generated = generator.generate(
                new DailyPlanGenerator.Request(plan.getPlanDate(), plan.getAvailableMinutes(), genCandidates));

        List<DailyPlanTask> entries = generated.entries().stream()
                .map(e -> DailyPlanTask.of(plan.getId(), e.taskId(), e.position(), e.allocatedMinutes()))
                .toList();
        plan.replaceEntries(new ArrayList<>(entries));
    }

    /** Open tasks whose objective is not archived, plus any {@code keepAsDone} tasks. */
    private List<Task> candidateTasks(UUID userId, Set<UUID> keepAsDone) {
        Map<UUID, Boolean> archivedByObjective = new HashMap<>();
        List<Task> open = tasks.findByUserIdAndStatusInAndDeletedAtIsNull(
                userId, List.of(TaskStatus.TODO, TaskStatus.IN_PROGRESS));

        List<Task> result = new ArrayList<>();
        for (Task t : open) {
            if (t.getObjectiveId() == null || !isArchived(t.getObjectiveId(), userId, archivedByObjective)) {
                result.add(t);
            }
        }
        for (UUID doneId : keepAsDone) {
            tasks.findByIdAndUserIdAndDeletedAtIsNull(doneId, userId).ifPresent(result::add);
        }
        return result;
    }

    private boolean isArchived(UUID objectiveId, UUID userId, Map<UUID, Boolean> cache) {
        return cache.computeIfAbsent(objectiveId,
                id -> objectives.findOwned(id, userId).map(ObjectiveLookup.Snapshot::archived).orElse(false));
    }

    private DailyPlanResponse toResponse(DailyPlan plan) {
        UUID userId = plan.getUserId();
        List<PlanEntryResponse> entries = new ArrayList<>();
        for (DailyPlanTask e : plan.getEntries()) {
            Task t = tasks.findByIdAndUserIdAndDeletedAtIsNull(e.getTaskId(), userId).orElse(null);
            if (t == null) {
                continue;
            }
            var objective = t.getObjectiveId() == null ? null
                    : objectives.findOwned(t.getObjectiveId(), userId).orElse(null);
            entries.add(new PlanEntryResponse(
                    t.getId(), e.getPosition(), e.getAllocatedMinutes(), t.getTitle(),
                    t.getStatus().value(), t.getEstimatedMinutes(), t.getPriorityScore(), t.getDueAt(),
                    t.getObjectiveId(), objective == null ? null : objective.title()));
        }
        entries.sort((a, b) -> Integer.compare(a.position(), b.position()));
        int allocated = entries.stream().mapToInt(PlanEntryResponse::allocatedMinutes).sum();
        return new DailyPlanResponse(
                plan.getId(), plan.getPlanDate(), plan.getAvailableMinutes(), allocated,
                plan.getStatus().value(), plan.getGeneratedBy(), entries,
                plan.getCreatedAt(), plan.getUpdatedAt());
    }
}
