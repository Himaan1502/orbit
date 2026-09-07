package com.orbit.platform.dashboard;

import com.orbit.platform.dashboard.DashboardDtos.DashboardResponse;
import com.orbit.platform.dashboard.DashboardDtos.ExecutionHealth;
import com.orbit.platform.dashboard.DashboardDtos.NextBestAction;
import com.orbit.platform.dashboard.DashboardDtos.ObjectiveHealth;
import com.orbit.platform.dashboard.DashboardDtos.ObjectiveSummary;
import com.orbit.platform.dashboard.DashboardDtos.TodayBlock;
import com.orbit.platform.dashboard.DashboardDtos.UserBlock;
import com.orbit.platform.execution.model.Task;
import com.orbit.platform.execution.model.TaskStatus;
import com.orbit.platform.execution.service.DailyPlanService;
import com.orbit.platform.execution.service.TaskService;
import com.orbit.platform.identity.model.User;
import com.orbit.platform.identity.service.CurrentUserService;
import com.orbit.platform.objectives.model.Objective;
import com.orbit.platform.objectives.model.ObjectiveStatus;
import com.orbit.platform.objectives.service.ObjectiveService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Assembles the Command Center view (Blueprint §30, UX §16) by composing the
 * objectives, execution and identity application services — a read-only
 * cross-domain projection, no writes.
 */
@Service
public class DashboardService {

    private final CurrentUserService currentUser;
    private final ObjectiveService objectives;
    private final TaskService tasks;
    private final DailyPlanService dailyPlans;
    private final Clock clock;

    public DashboardService(CurrentUserService currentUser, ObjectiveService objectives, TaskService tasks,
                            DailyPlanService dailyPlans, Clock clock) {
        this.currentUser = currentUser;
        this.objectives = objectives;
        this.tasks = tasks;
        this.dailyPlans = dailyPlans;
        this.clock = clock;
    }

    @Transactional
    public DashboardResponse build() {
        User user = currentUser.getOrProvision();
        LocalDate today = LocalDate.now(clock);
        Instant now = clock.instant();

        List<Objective> allObjectives = objectives.listMine(null);
        List<Objective> active = allObjectives.stream()
                .filter(o -> o.getStatus() == ObjectiveStatus.ACTIVE)
                .sorted(Comparator.comparingInt(Objective::getPriority).reversed()
                        .thenComparing(Objective::getCreatedAt))
                .toList();
        long completed = allObjectives.stream().filter(o -> o.getStatus() == ObjectiveStatus.COMPLETED).count();
        BigDecimal avgProgress = active.isEmpty() ? BigDecimal.ZERO : active.stream()
                .map(Objective::getProgress)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(active.size()), 3, RoundingMode.HALF_UP);

        Map<UUID, String> objectiveTitles = allObjectives.stream()
                .collect(Collectors.toMap(Objective::getId, Objective::getTitle));

        List<Task> myTasks = tasks.listMine();
        List<Task> open = myTasks.stream().filter(t -> t.getStatus().isOpen()).toList();
        Task topTask = open.stream()
                .filter(t -> t.getPriorityScore() != null)
                .max(Comparator.comparing(Task::getPriorityScore))
                .orElse(open.stream().findFirst().orElse(null));

        var todayPlan = dailyPlans.findForDate(today).orElse(null);

        return new DashboardResponse(
                new UserBlock(user.getDisplayName(), user.getEmail()),
                new ObjectiveHealth(active.size(), (int) completed, avgProgress),
                active.isEmpty() ? null : summary(active.get(0)),
                active.stream().limit(5).map(DashboardService::summary).toList(),
                topTask == null ? null : nextBestAction(topTask, objectiveTitles),
                new TodayBlock(
                        today,
                        todayPlan != null,
                        todayPlan == null ? 0 : todayPlan.tasks().size(),
                        todayPlan == null ? 0 : todayPlan.allocatedMinutes(),
                        todayPlan == null ? currentUser.availableDailyMinutes(120) : todayPlan.availableMinutes()),
                new ExecutionHealth(
                        open.size(),
                        (int) open.stream().filter(t -> dueWithin(t, now, 7)).count(),
                        (int) myTasks.stream().filter(t -> completedWithin(t, now, 7)).count()));
    }

    private static ObjectiveSummary summary(Objective o) {
        return new ObjectiveSummary(o.getId(), o.getTitle(), o.getPriority(),
                o.getStatus().value(), o.getProgress(), o.getTargetDate());
    }

    private static NextBestAction nextBestAction(Task t, Map<UUID, String> objectiveTitles) {
        return new NextBestAction(
                t.getId(), t.getTitle(), t.getPriorityScore(), t.getPriorityReason(),
                t.getObjectiveId(),
                t.getObjectiveId() == null ? null : objectiveTitles.get(t.getObjectiveId()),
                t.getEstimatedMinutes(), t.getDueAt());
    }

    private static boolean dueWithin(Task t, Instant now, int days) {
        return t.getDueAt() != null
                && !t.getDueAt().isBefore(now)
                && Duration.between(now, t.getDueAt()).toDays() <= days;
    }

    private static boolean completedWithin(Task t, Instant now, int days) {
        return t.getStatus() == TaskStatus.DONE
                && t.getCompletedAt() != null
                && Duration.between(t.getCompletedAt(), now).toDays() <= days;
    }
}
