package com.orbit.platform.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orbit.platform.analytics.service.AnalyticsService;
import com.orbit.platform.analytics.web.dto.AnalyticsDtos.ExecutionAnalytics;
import com.orbit.platform.analytics.review.WeeklyReviewService;
import com.orbit.platform.analytics.review.model.WeeklyReview;
import com.orbit.platform.career.readiness.CareerReadinessCalculator;
import com.orbit.platform.career.service.CareerReadinessService;
import com.orbit.platform.career.service.CareerReadinessService.NamedReadiness;
import com.orbit.platform.execution.model.Task;
import com.orbit.platform.execution.priority.PriorityReason;
import com.orbit.platform.execution.service.DailyPlanService;
import com.orbit.platform.execution.service.TaskService;
import com.orbit.platform.execution.web.dto.DailyPlanDtos.DailyPlanResponse;
import com.orbit.platform.identity.service.CurrentUserService;
import com.orbit.platform.objectives.model.Objective;
import com.orbit.platform.objectives.model.ObjectiveStatus;
import com.orbit.platform.objectives.service.ObjectiveService;
import com.orbit.platform.projects.model.Project;
import com.orbit.platform.projects.service.ProjectService;
import com.orbit.platform.skills.service.SkillService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Assembles the Tech Arch §12 context layers for one OSCAR turn (ADR-032 §2) by
 * calling the existing {@code currentUser}-scoped domain services — never SQL,
 * never another user's data. M7 sends the full bounded bundle every turn;
 * Gemini's window absorbs it (ADR-031). Each layer is built defensively: a
 * failing layer is logged and omitted, not fatal to the turn.
 */
@Component
public class ContextBuilder {

    private static final Logger log = LoggerFactory.getLogger(ContextBuilder.class);
    private static final int TOP_TASKS = 10;
    private static final int TOP_GAPS = 5;

    private final CurrentUserService currentUser;
    private final ObjectiveService objectives;
    private final TaskService tasks;
    private final CareerReadinessService readiness;
    private final ProjectService projects;
    private final AnalyticsService analytics;
    private final DailyPlanService dailyPlans;
    private final WeeklyReviewService weeklyReviews;
    private final SkillService skills;
    private final ObjectMapper json;
    private final Clock clock;

    public ContextBuilder(CurrentUserService currentUser, ObjectiveService objectives, TaskService tasks,
                          CareerReadinessService readiness, ProjectService projects,
                          AnalyticsService analytics, DailyPlanService dailyPlans,
                          WeeklyReviewService weeklyReviews, SkillService skills,
                          ObjectMapper json, Clock clock) {
        this.currentUser = currentUser;
        this.objectives = objectives;
        this.tasks = tasks;
        this.readiness = readiness;
        this.projects = projects;
        this.analytics = analytics;
        this.dailyPlans = dailyPlans;
        this.weeklyReviews = weeklyReviews;
        this.skills = skills;
        this.json = json;
        this.clock = clock;
    }

    /**
     * Not {@code @Transactional} — each domain service call manages its own
     * read-only transaction, so a service that throws (e.g. "no career target")
     * only rolls back its own tx and {@link #addLayer} can swallow it without
     * poisoning a surrounding transaction.
     */
    public ContextBundle forTurn(UUID userId, String userMessage) {
        ContextBundle.Builder b = ContextBundle.builder();

        b.put("userConstraints", userConstraints(), true);
        addLayer(b, "objectives", this::objectivesLayer);
        addLayer(b, "careerTarget", this::careerLayer);
        addLayer(b, "skillGraph", this::skillGraphLayer);
        addLayer(b, "recentExecution", this::executionLayer);
        addLayer(b, "projects", this::projectsLayer);
        addLayer(b, "incompleteTasks", this::tasksLayer);
        addLayer(b, "todayPlan", this::todayLayer);
        addLayer(b, "latestWeeklyReview", this::weeklyReviewLayer);

        ContextBundle bundle = b.build();
        log.info("event=oscar.context.built user_id={} layers={} est_tokens={}",
                userId, bundle.layerNames(), bundle.estimatedTokens(json));
        return bundle;
    }

    private void addLayer(ContextBundle.Builder b, String name,
                          java.util.function.Supplier<LayerResult> supplier) {
        try {
            LayerResult r = supplier.get();
            b.put(name, r.payload(), r.hasData());
        } catch (RuntimeException ex) {
            log.warn("event=oscar.context.layer_failed layer={} error={}", name, ex.toString());
            b.put(name, Map.of("status", "unavailable"), false);
        }
    }

    private record LayerResult(Object payload, boolean hasData) {
    }

    // ----- layers -------------------------------------------------------------

    private Map<String, Object> userConstraints() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("today", LocalDate.now(clock).toString());
        m.put("availableDailyMinutes", currentUser.availableDailyMinutes(120));
        return m;
    }

    private LayerResult objectivesLayer() {
        List<Objective> all = objectives.listMine(null);
        List<Map<String, Object>> rows = all.stream()
                .filter(o -> o.getStatus() != ObjectiveStatus.ARCHIVED)
                .sorted(Comparator.comparingInt(Objective::getPriority).reversed())
                .map(o -> {
                    Map<String, Object> r = new LinkedHashMap<>();
                    r.put("id", o.getId());
                    r.put("title", o.getTitle());
                    r.put("status", o.getStatus().value());
                    r.put("priority", o.getPriority());
                    r.put("progress", o.getProgress());
                    r.put("targetDate", o.getTargetDate());
                    return r;
                })
                .toList();
        return new LayerResult(rows, !rows.isEmpty());
    }

    private LayerResult careerLayer() {
        NamedReadiness nr;
        try {
            nr = readiness.readiness(null);
        } catch (RuntimeException noTarget) {
            return new LayerResult(Map.of("status", "no-career-target-set"), false);
        }
        CareerReadinessCalculator.Readiness r = nr.readiness();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("target", nr.targetName());
        m.put("readiness", r.overall());
        m.put("confidence", r.confidence());
        m.put("components", r.componentBreakdown());
        m.put("criticalGaps", r.criticalGaps().stream().limit(TOP_GAPS).toList());
        m.put("highLeverageSkills", r.highLeverageSkills().stream().limit(TOP_GAPS).toList());
        m.put("evidenceGaps", r.evidenceGaps().stream().limit(TOP_GAPS).toList());
        m.put("projectGaps", r.projectGaps().stream().limit(TOP_GAPS).toList());
        m.put("note", "readiness blends skill capability 0.45 / interview 0.20 / evidence 0.20 / project 0.15 (ADR-026)");
        return new LayerResult(m, true);
    }

    private LayerResult skillGraphLayer() {
        NamedReadiness nr;
        try {
            nr = readiness.readiness(null);
        } catch (RuntimeException noTarget) {
            return new LayerResult(Map.of("status", "no-career-target-set"), false);
        }
        UUID userId = currentUser.currentUserId();
        List<Map<String, Object>> rows = new ArrayList<>();
        nr.readiness().criticalGaps().stream().limit(3).forEach(g -> {
            try {
                SkillService.SkillNeighbours n = skills.neighboursForUser(g.skillId(), userId);
                Map<String, Object> r = new LinkedHashMap<>();
                r.put("skill", g.skill());
                r.put("capability", g.capability());
                r.put("prerequisites", n.prerequisites().stream()
                        .map(p -> Map.of("name", p.name(), "capability", p.capability())).toList());
                r.put("dependents", n.dependents().stream()
                        .map(p -> Map.of("name", p.name(), "capability", p.capability())).toList());
                rows.add(r);
            } catch (RuntimeException ignored) {
                // skip a skill whose neighbours can't be resolved
            }
        });
        return new LayerResult(rows, !rows.isEmpty());
    }

    private LayerResult executionLayer() {
        LocalDate to = LocalDate.now(clock);
        LocalDate from = to.minusWeeks(2);
        ExecutionAnalytics ex = analytics.execution(from, to);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("window", from + " to " + to);
        m.put("status", ex.status());
        m.put("completionRate", ex.completionRate());
        m.put("throughputPerWeek", ex.throughputPerWeek());
        m.put("statusCounts", ex.statusCounts());
        m.put("blockers", ex.blockers());
        return new LayerResult(m, !"no-data-yet".equals(ex.status()));
    }

    private LayerResult projectsLayer() {
        List<Project> all = projects.listMine();
        List<Map<String, Object>> rows = all.stream()
                .filter(p -> p.getStatus() != com.orbit.platform.projects.model.ProjectStatus.ARCHIVED)
                .map(p -> {
                    Map<String, Object> r = new LinkedHashMap<>();
                    r.put("id", p.getId());
                    r.put("name", p.getName());
                    r.put("status", p.getStatus().value());
                    try {
                        var pws = projects.getMine(p.getId());
                        r.put("progress", projects.progressOf(pws));
                    } catch (RuntimeException ignored) {
                        // progress optional
                    }
                    return r;
                })
                .toList();
        return new LayerResult(rows, !rows.isEmpty());
    }

    private LayerResult tasksLayer() {
        List<Task> open = tasks.listMine().stream()
                .filter(t -> t.getStatus().isOpen())
                .filter(t -> t.getPriorityScore() != null)
                .sorted(Comparator.comparing(Task::getPriorityScore).reversed())
                .limit(TOP_TASKS)
                .toList();
        List<Map<String, Object>> rows = open.stream().map(t -> {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("id", t.getId());
            r.put("title", t.getTitle());
            r.put("status", t.getStatus().value());
            r.put("objectiveId", t.getObjectiveId());
            r.put("skillId", t.getSkillId());
            r.put("projectId", t.getProjectId());
            r.put("estimatedMinutes", t.getEstimatedMinutes());
            r.put("dueAt", t.getDueAt());
            r.put("priorityScore", t.getPriorityScore());
            r.put("topFactor", topFactor(t.getPriorityReason()));
            return r;
        }).toList();
        return new LayerResult(rows, !rows.isEmpty());
    }

    private LayerResult todayLayer() {
        LocalDate today = LocalDate.now(clock);
        DailyPlanResponse plan = dailyPlans.findForDate(today).orElse(null);
        if (plan == null) {
            return new LayerResult(Map.of("date", today.toString(), "generated", false), false);
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("date", plan.planDate());
        m.put("generated", true);
        m.put("availableMinutes", plan.availableMinutes());
        m.put("allocatedMinutes", plan.allocatedMinutes());
        m.put("tasks", plan.tasks().stream()
                .map(e -> Map.of("taskId", e.taskId(), "title", e.title())).toList());
        return new LayerResult(m, true);
    }

    private LayerResult weeklyReviewLayer() {
        List<WeeklyReview> mine = weeklyReviews.listMine();
        if (mine.isEmpty()) {
            return new LayerResult(Map.of("status", "none-generated-yet"), false);
        }
        WeeklyReview latest = mine.get(0);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("weekStart", latest.getWeekStart());
        m.put("summary", latest.getSummary());
        m.put("status", latest.getStatus());
        return new LayerResult(m, true);
    }

    private static String topFactor(PriorityReason reason) {
        if (reason == null || reason.factors() == null || reason.factors().isEmpty()) {
            return null;
        }
        return reason.factors().entrySet().stream()
                .max(Comparator.comparingDouble(e -> e.getValue().contribution()))
                .map(Map.Entry::getKey)
                .orElse(null);
    }
}
