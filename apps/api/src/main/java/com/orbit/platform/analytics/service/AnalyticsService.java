package com.orbit.platform.analytics.service;

import com.orbit.platform.analytics.trend.WeekBuckets;
import com.orbit.platform.analytics.web.dto.AnalyticsDtos.BlockerRow;
import com.orbit.platform.analytics.web.dto.AnalyticsDtos.Bottleneck;
import com.orbit.platform.analytics.web.dto.AnalyticsDtos.CareerAnalytics;
import com.orbit.platform.analytics.web.dto.AnalyticsDtos.CountWeek;
import com.orbit.platform.analytics.web.dto.AnalyticsDtos.ExecutionAnalytics;
import com.orbit.platform.analytics.web.dto.AnalyticsDtos.ExecutionWeek;
import com.orbit.platform.analytics.web.dto.AnalyticsDtos.LeverageSkill;
import com.orbit.platform.analytics.web.dto.AnalyticsDtos.NeglectedSkill;
import com.orbit.platform.analytics.web.dto.AnalyticsDtos.ProgressionPoint;
import com.orbit.platform.analytics.web.dto.AnalyticsDtos.Range;
import com.orbit.platform.analytics.web.dto.AnalyticsDtos.ReadinessPoint;
import com.orbit.platform.analytics.web.dto.AnalyticsDtos.SkillAnalytics;
import com.orbit.platform.analytics.web.dto.AnalyticsDtos.SkillProgression;
import com.orbit.platform.analytics.web.dto.AnalyticsDtos.SkillSeriesPoint;
import com.orbit.platform.analytics.web.dto.AnalyticsDtos.StatusCounts;
import com.orbit.platform.career.service.CareerReadinessService;
import com.orbit.platform.career.service.CareerTargetService;
import com.orbit.platform.analytics.web.dto.AnalyticsDtos.ObjectiveProgressPoint;
import com.orbit.platform.analytics.web.dto.AnalyticsDtos.ObjectiveProgressSeries;
import com.orbit.platform.evidence.service.EvidenceService;
import com.orbit.platform.execution.model.DailyPlan;
import com.orbit.platform.execution.model.Task;
import com.orbit.platform.execution.model.TaskStatus;
import com.orbit.platform.execution.repository.DailyPlanRepository;
import com.orbit.platform.execution.repository.TaskRepository;
import com.orbit.platform.identity.service.CurrentUserService;
import com.orbit.platform.objectives.model.Objective;
import com.orbit.platform.objectives.model.ObjectiveProgressEvent;
import com.orbit.platform.objectives.repository.ObjectiveProgressEventRepository;
import com.orbit.platform.objectives.service.ObjectiveService;
import com.orbit.platform.skills.graph.SkillGraph;
import com.orbit.platform.skills.model.Skill;
import com.orbit.platform.skills.model.SkillAssessment;
import com.orbit.platform.skills.repository.SkillRepository;
import com.orbit.platform.skills.service.SkillGraphService;
import com.orbit.platform.skills.service.SkillService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * The read-only analytics projection (ADR-027 §1) — a leaf consumer, like
 * {@code dashboard}. Every series is computed on request from already-persisted
 * domain data; nothing derived is stored. Ownership is the current user's.
 */
@Service
public class AnalyticsService {

    private static final double COVERED = 0.70;
    private static final int NEGLECTED_DAYS = 30;

    private final CurrentUserService currentUser;
    private final TaskRepository tasks;
    private final DailyPlanRepository dailyPlans;
    private final CareerReadinessService readiness;
    private final CareerTargetService targets;
    private final EvidenceService evidence;
    private final SkillService skillService;
    private final SkillGraphService graphService;
    private final SkillRepository skills;
    private final ObjectiveService objectives;
    private final ObjectiveProgressEventRepository objectiveEvents;
    private final Clock clock;

    public AnalyticsService(CurrentUserService currentUser, TaskRepository tasks,
                            DailyPlanRepository dailyPlans, CareerReadinessService readiness,
                            CareerTargetService targets, EvidenceService evidence,
                            SkillService skillService, SkillGraphService graphService,
                            SkillRepository skills, ObjectiveService objectives,
                            ObjectiveProgressEventRepository objectiveEvents, Clock clock) {
        this.currentUser = currentUser;
        this.tasks = tasks;
        this.dailyPlans = dailyPlans;
        this.readiness = readiness;
        this.targets = targets;
        this.evidence = evidence;
        this.skillService = skillService;
        this.graphService = graphService;
        this.skills = skills;
        this.objectives = objectives;
        this.objectiveEvents = objectiveEvents;
        this.clock = clock;
    }

    // =====================================================================
    // Execution (UX §24)
    // =====================================================================

    @Transactional(readOnly = true)
    public ExecutionAnalytics execution(LocalDate from, LocalDate to) {
        UUID userId = currentUser.currentUserId();
        List<Task> all = tasks.findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(userId);
        List<DailyPlan> plans = dailyPlans.findByUserIdAndPlanDateBetweenOrderByPlanDateAsc(userId, from, to);

        Range range = range(from, to);
        List<ObjectiveProgressSeries> objectiveProgress = objectiveProgress(userId, from, to);
        if (all.isEmpty()) {
            return new ExecutionAnalytics(range, "no-data-yet", List.of(), null, 0,
                    countStatuses(all), List.of(), objectiveProgress);
        }

        Map<LocalDate, int[]> planned = WeekBuckets.accumulator(from, to);
        for (DailyPlan p : plans) {
            WeekBuckets.add(planned, p.getPlanDate(), p.getEntries().size());
        }

        Map<LocalDate, int[]> completed = WeekBuckets.accumulator(from, to);
        Map<UUID, Integer> allocById = plans.stream()
                .flatMap(p -> p.getEntries().stream())
                .collect(Collectors.toMap(e -> e.getTaskId(), e -> e.getAllocatedMinutes(), Integer::sum));
        Map<LocalDate, int[]> focused = WeekBuckets.accumulator(from, to);

        int completedInRange = 0;
        for (Task t : all) {
            Instant done = t.getCompletedAt();
            if (t.getStatus() == TaskStatus.DONE && done != null) {
                LocalDate day = LocalDate.ofInstant(done, ZoneOffset.UTC);
                if (!day.isBefore(from) && !day.isAfter(to)) {
                    WeekBuckets.add(completed, day, 1);
                    WeekBuckets.add(focused, day, allocById.getOrDefault(t.getId(), 0));
                    completedInRange++;
                }
            }
        }

        List<ExecutionWeek> weekly = new ArrayList<>();
        for (LocalDate w : WeekBuckets.weekStarts(from, to)) {
            weekly.add(new ExecutionWeek(w, planned.get(w)[0], completed.get(w)[0], focused.get(w)[0]));
        }

        int totalPlanned = weekly.stream().mapToInt(ExecutionWeek::planned).sum();
        Double completionRate = totalPlanned == 0 ? null
                : round(Math.min(1.0, (double) completedInRange / totalPlanned));
        double throughput = round((double) completedInRange / Math.max(1, weekly.size()));

        return new ExecutionAnalytics(range, "ok", weekly, completionRate, throughput,
                countStatuses(all), blockers(all), objectiveProgress);
    }

    private List<ObjectiveProgressSeries> objectiveProgress(UUID userId, LocalDate from, LocalDate to) {
        Instant fromI = from.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant toI = to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        var events = objectiveEvents.findByUserIdAndRecordedAtBetweenOrderByRecordedAtAsc(userId, fromI, toI);
        if (events.isEmpty()) {
            return List.of();
        }
        Map<UUID, String> titles = objectives.listMine(null).stream()
                .collect(Collectors.toMap(Objective::getId, Objective::getTitle));
        Map<UUID, List<ObjectiveProgressPoint>> byObjective = new LinkedHashMap<>();
        for (ObjectiveProgressEvent e : events) {
            byObjective.computeIfAbsent(e.getObjectiveId(), k -> new ArrayList<>())
                    .add(new ObjectiveProgressPoint(e.getRecordedAt(),
                            round(e.getProgress().doubleValue()), e.getStatus().value()));
        }
        return byObjective.entrySet().stream()
                .map(en -> new ObjectiveProgressSeries(en.getKey(),
                        titles.getOrDefault(en.getKey(), "(removed)"), en.getValue()))
                .toList();
    }

    private StatusCounts countStatuses(List<Task> all) {
        Map<TaskStatus, Long> c = all.stream()
                .collect(Collectors.groupingBy(Task::getStatus, Collectors.counting()));
        return new StatusCounts(
                c.getOrDefault(TaskStatus.TODO, 0L), c.getOrDefault(TaskStatus.IN_PROGRESS, 0L),
                c.getOrDefault(TaskStatus.BLOCKED, 0L), c.getOrDefault(TaskStatus.DONE, 0L),
                c.getOrDefault(TaskStatus.SKIPPED, 0L));
    }

    private List<BlockerRow> blockers(List<Task> all) {
        Instant now = clock.instant();
        return all.stream()
                .filter(t -> t.getStatus() == TaskStatus.BLOCKED && t.getBlockedSince() != null)
                .sorted(Comparator.comparing(Task::getBlockedSince))
                .map(t -> new BlockerRow(t.getId(), t.getTitle(), t.getBlockedSince(),
                        Duration.between(t.getBlockedSince(), now).toDays()))
                .toList();
    }

    // =====================================================================
    // Career (UX §24)
    // =====================================================================

    @Transactional(readOnly = true)
    public CareerAnalytics career(UUID careerTargetId, LocalDate from, LocalDate to) {
        UUID userId = currentUser.currentUserId();
        Instant fromI = from.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant toI = to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();

        var trend = readiness.readinessTrend(careerTargetId, fromI, toI);
        Set<UUID> targetSkillIds = targets.skillsOf(trend.careerTargetId()).stream()
                .map(s -> s.getSkillId()).collect(Collectors.toSet());

        List<ReadinessPoint> readinessTrend = trend.points().stream()
                .map(p -> new ReadinessPoint(p.at(), round(p.overall()), round(p.skillCapability()),
                        p.assessedSkillCount()))
                .toList();

        // evidence growth — target-skill-linked evidence per week
        Map<LocalDate, int[]> ev = WeekBuckets.accumulator(from, to);
        for (var ewl : evidence.listMine(null, null, null)) {
            LocalDate day = LocalDate.ofInstant(ewl.evidence().getCreatedAt(), ZoneOffset.UTC);
            boolean touchesTarget = ewl.skills().stream()
                    .anyMatch(se -> targetSkillIds.contains(se.getSkillId()));
            if (touchesTarget && !day.isBefore(from) && !day.isAfter(to)) {
                WeekBuckets.add(ev, day, 1);
            }
        }
        List<CountWeek> evidenceGrowth = WeekBuckets.weekStarts(from, to).stream()
                .map(w -> new CountWeek(w, ev.get(w)[0])).toList();

        // target progression — how many target skills sit at capability >= 0.70, sampled with the trend
        List<ProgressionPoint> progression = new ArrayList<>();
        for (var p : trend.points()) {
            int atThreshold = 0;
            var caps = skillService.latestBySkillAsOf(userId, p.at());
            for (UUID sid : targetSkillIds) {
                SkillAssessment a = caps.get(sid);
                if (a != null && a.overall() >= COVERED) {
                    atThreshold++;
                }
            }
            progression.add(new ProgressionPoint(p.at(), atThreshold, targetSkillIds.size()));
        }

        String status = readinessTrend.stream().allMatch(p -> p.assessedSkillCount() == 0)
                ? "no-data-yet" : "ok";
        return new CareerAnalytics(trend.careerTargetId(), trend.targetName(), status,
                readinessTrend, evidenceGrowth, progression);
    }

    // =====================================================================
    // Skills (UX §24)
    // =====================================================================

    @Transactional(readOnly = true)
    public SkillAnalytics skillsAnalytics() {
        UUID userId = currentUser.currentUserId();
        List<SkillAssessment> history = skillService.assessmentHistory(userId);
        Map<UUID, String> names = skills.findAll().stream()
                .collect(Collectors.toMap(Skill::getId, Skill::getCanonicalName));

        if (history.isEmpty()) {
            return new SkillAnalytics("no-data-yet", List.of(), List.of(), List.of(), List.of());
        }

        // progression — one series per assessed skill, oldest → newest
        Map<UUID, List<SkillSeriesPoint>> bySkill = new LinkedHashMap<>();
        for (int i = history.size() - 1; i >= 0; i--) {
            SkillAssessment a = history.get(i);
            bySkill.computeIfAbsent(a.getSkillId(), k -> new ArrayList<>())
                    .add(new SkillSeriesPoint(a.getAssessedAt(), round(a.overall())));
        }
        List<SkillProgression> progression = bySkill.entrySet().stream()
                .map(e -> new SkillProgression(e.getKey(), names.getOrDefault(e.getKey(), "?"), e.getValue()))
                .toList();

        // the "in scope" skill set — the caller's target skills
        Set<UUID> targetSkillIds = targets.listMineWithSkills().stream()
                .flatMap(t -> t.skills().stream())
                .map(s -> s.getSkillId())
                .collect(Collectors.toSet());

        Map<UUID, SkillAssessment> latest = skillService.latestBySkill(userId);
        Instant now = clock.instant();

        // neglected — a target skill not assessed in NEGLECTED_DAYS (or never)
        List<NeglectedSkill> neglected = targetSkillIds.stream()
                .map(sid -> {
                    SkillAssessment a = latest.get(sid);
                    Long daysSince = a == null ? null : Duration.between(a.getAssessedAt(), now).toDays();
                    return new NeglectedSkill(sid, names.getOrDefault(sid, "?"),
                            a == null ? null : a.getAssessedAt(), daysSince);
                })
                .filter(n -> n.daysSince() == null || n.daysSince() >= NEGLECTED_DAYS)
                .sorted(Comparator.comparingLong(
                        (NeglectedSkill n) -> n.daysSince() == null ? Long.MAX_VALUE : n.daysSince()).reversed())
                .toList();

        // high-leverage — reuse SkillGraph.leverage over the target set
        SkillGraph graph = graphService.graph();
        Map<UUID, Double> importance = new HashMap<>();
        Map<UUID, Double> gap = new HashMap<>();
        for (var t : targets.listMineWithSkills()) {
            for (var s : t.skills()) {
                importance.merge(s.getSkillId(), s.importanceValue(), Math::max);
                SkillAssessment a = latest.get(s.getSkillId());
                gap.put(s.getSkillId(), 1.0 - (a == null ? 0.0 : a.overall()));
            }
        }
        List<LeverageSkill> highLeverage = targetSkillIds.stream()
                .map(sid -> {
                    double lev = graph.leverage(sid, importance, gap);
                    int unlocks = (int) graph.downstream(sid).stream().filter(importance::containsKey).count();
                    return new LeverageSkill(sid, names.getOrDefault(sid, "?"), round(lev), unlocks);
                })
                .filter(l -> l.leverage() > 0.001)
                .sorted(Comparator.comparingDouble(LeverageSkill::leverage).reversed())
                .limit(5)
                .toList();

        // dependency bottlenecks — an unassessed prerequisite of a skill the user IS assessing
        Map<UUID, List<String>> blockedBy = new LinkedHashMap<>();
        for (UUID assessed : latest.keySet()) {
            for (SkillGraph.Edge edge : graph.prerequisites(assessed)) {
                if (!latest.containsKey(edge.prerequisite())) {
                    blockedBy.computeIfAbsent(edge.prerequisite(), k -> new ArrayList<>())
                            .add(names.getOrDefault(assessed, "?"));
                }
            }
        }
        List<Bottleneck> bottlenecks = blockedBy.entrySet().stream()
                .map(e -> new Bottleneck(e.getKey(), names.getOrDefault(e.getKey(), "?"), e.getValue()))
                .sorted(Comparator.comparingInt((Bottleneck b) -> b.blockedDependents().size()).reversed())
                .toList();

        return new SkillAnalytics("ok", progression, neglected, highLeverage,
                bottlenecks.stream().limit(5).toList());
    }

    // =====================================================================

    private Range range(LocalDate from, LocalDate to) {
        return new Range(from, to, WeekBuckets.weekStarts(from, to).size());
    }

    private static double round(double v) {
        return Math.round(v * 1000) / 1000.0;
    }
}
