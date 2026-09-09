package com.orbit.platform.analytics.review;

import com.orbit.platform.analytics.review.WeeklyReviewComposer.ObjectiveMove;
import com.orbit.platform.analytics.review.WeeklyReviewComposer.ReadinessMove;
import com.orbit.platform.analytics.review.WeeklyReviewComposer.SkillMove;
import com.orbit.platform.analytics.review.WeeklyReviewComposer.WeeklyInputs;
import com.orbit.platform.analytics.review.WeeklyReviewContent.MissedTask;
import com.orbit.platform.analytics.review.WeeklyReviewContent.NextAction;
import com.orbit.platform.analytics.review.model.WeeklyReview;
import com.orbit.platform.analytics.review.repository.WeeklyReviewRepository;
import com.orbit.platform.career.service.CareerReadinessService;
import com.orbit.platform.career.service.CareerReadinessService.NamedTrend;
import com.orbit.platform.career.service.CareerReadinessService.ReadinessPoint;
import com.orbit.platform.career.service.CareerTargetService;
import com.orbit.platform.common.exception.ApiException;
import com.orbit.platform.evidence.model.Evidence;
import com.orbit.platform.evidence.repository.EvidenceRepository;
import com.orbit.platform.execution.model.DailyPlan;
import com.orbit.platform.execution.model.Task;
import com.orbit.platform.execution.model.TaskStatus;
import com.orbit.platform.execution.repository.DailyPlanRepository;
import com.orbit.platform.execution.repository.TaskRepository;
import com.orbit.platform.identity.repository.UserProfileRepository;
import com.orbit.platform.identity.service.CurrentUserService;
import com.orbit.platform.objectives.model.Objective;
import com.orbit.platform.objectives.model.ObjectiveProgressEvent;
import com.orbit.platform.objectives.repository.MilestoneRepository;
import com.orbit.platform.objectives.repository.ObjectiveProgressEventRepository;
import com.orbit.platform.objectives.repository.ObjectiveRepository;
import com.orbit.platform.projects.model.Project;
import com.orbit.platform.projects.repository.ProjectRepository;
import com.orbit.platform.skills.model.Skill;
import com.orbit.platform.skills.model.SkillAssessment;
import com.orbit.platform.skills.repository.SkillRepository;
import com.orbit.platform.skills.service.SkillService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.IsoFields;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Assembles a week's {@link WeeklyInputs} from persisted domain data and
 * composes a {@link WeeklyReview} with the pure {@link WeeklyReviewComposer}
 * (ADR-028). One review per {@code (user_id, week_start)} — regeneration keeps
 * {@code user_notes} and {@code status}. Works with an explicit {@code userId}
 * so the scheduled job runs with no security context.
 */
@Service
public class WeeklyReviewService {

    private static final Logger log = LoggerFactory.getLogger(WeeklyReviewService.class);
    private static final int NEXT_LIMIT = 5;

    private final WeeklyReviewRepository reviews;
    private final TaskRepository tasks;
    private final DailyPlanRepository dailyPlans;
    private final EvidenceRepository evidence;
    private final ProjectRepository projects;
    private final ObjectiveRepository objectives;
    private final ObjectiveProgressEventRepository objectiveEvents;
    private final MilestoneRepository milestones;
    private final SkillService skillService;
    private final SkillRepository skills;
    private final CareerReadinessService readiness;
    private final CareerTargetService targets;
    private final UserProfileRepository profiles;
    private final CurrentUserService currentUser;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public WeeklyReviewService(WeeklyReviewRepository reviews, TaskRepository tasks,
                               DailyPlanRepository dailyPlans, EvidenceRepository evidence,
                               ProjectRepository projects, ObjectiveRepository objectives,
                               ObjectiveProgressEventRepository objectiveEvents,
                               MilestoneRepository milestones, SkillService skillService,
                               SkillRepository skills, CareerReadinessService readiness,
                               CareerTargetService targets, UserProfileRepository profiles,
                               CurrentUserService currentUser, JdbcTemplate jdbc, Clock clock) {
        this.reviews = reviews;
        this.tasks = tasks;
        this.dailyPlans = dailyPlans;
        this.evidence = evidence;
        this.projects = projects;
        this.objectives = objectives;
        this.objectiveEvents = objectiveEvents;
        this.milestones = milestones;
        this.skillService = skillService;
        this.skills = skills;
        this.readiness = readiness;
        this.targets = targets;
        this.profiles = profiles;
        this.currentUser = currentUser;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public record Week(LocalDate start, LocalDate end, Instant fromInstant, Instant toInstant) {
    }

    // ===== request paths (security context present) =====

    @Transactional(readOnly = true)
    public List<WeeklyReview> listMine() {
        return reviews.findByUserIdOrderByWeekStartDesc(currentUser.currentUserId());
    }

    /** Return the review for a week; lazily generate it if the week is over and it is missing. */
    @Transactional
    public WeeklyReview getMine(LocalDate weekStart) {
        UUID userId = currentUser.getOrProvision().getId();
        LocalDate monday = weekStart.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        return reviews.findByUserIdAndWeekStart(userId, monday).orElseGet(() -> {
            Week w = week(userId, monday);
            if (!w.end().isBefore(LocalDate.now(clock))) {
                throw ApiException.notFound("That week's review is not available yet.");
            }
            return generateFor(userId, monday);
        });
    }

    @Transactional
    public WeeklyReview generateMine(LocalDate weekStart) {
        UUID userId = currentUser.getOrProvision().getId();
        LocalDate monday = (weekStart != null ? weekStart : lastCompletedWeekStart(userId))
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        return generateFor(userId, monday);
    }

    @Transactional
    public WeeklyReview update(UUID id, String userNotes, String status) {
        WeeklyReview r = reviews.findByIdAndUserId(id, currentUser.currentUserId())
                .orElseThrow(() -> ApiException.notFound("Weekly review not found."));
        Instant now = clock.instant();
        if (userNotes != null) {
            r.setUserNotes(userNotes.isBlank() ? null : userNotes.strip(), now);
        }
        if ("reviewed".equals(status)) {
            r.markReviewed(now);
        }
        return reviews.save(r);
    }

    // ===== job path (no security context) =====

    /** Generate last-ended week's review for every user with activity in it. Returns the count. */
    @Transactional
    public int runForWeek(LocalDate anyDayInWeek) {
        LocalDate monday = anyDayInWeek.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        Instant from = monday.atStartOfDay(ZoneId.of("UTC")).toInstant();
        Instant to = monday.plusWeeks(1).atStartOfDay(ZoneId.of("UTC")).toInstant();
        List<UUID> userIds = activeUserIds(from, to);
        int done = 0;
        for (UUID userId : userIds) {
            try {
                generateFor(userId, monday);
                done++;
            } catch (RuntimeException ex) {
                log.warn("event=weekly_review.user_failed user_id={} week={} error={}",
                        userId, monday, ex.toString());
            }
        }
        return done;
    }

    private List<UUID> activeUserIds(Instant from, Instant to) {
        return jdbc.query("""
                select distinct s.user_id from (
                  select user_id from orbit.tasks
                    where completed_at >= ? and completed_at < ? and deleted_at is null
                  union select user_id from orbit.skill_assessments where assessed_at >= ? and assessed_at < ?
                  union select user_id from orbit.evidence
                    where created_at >= ? and created_at < ? and deleted_at is null
                  union select user_id from orbit.objective_progress_events
                    where recorded_at >= ? and recorded_at < ?
                ) s
                """,
                (rs, i) -> (UUID) rs.getObject("user_id"),
                java.sql.Timestamp.from(from), java.sql.Timestamp.from(to),
                java.sql.Timestamp.from(from), java.sql.Timestamp.from(to),
                java.sql.Timestamp.from(from), java.sql.Timestamp.from(to),
                java.sql.Timestamp.from(from), java.sql.Timestamp.from(to));
    }

    // ===== generation =====

    @Transactional
    public WeeklyReview generateFor(UUID userId, LocalDate weekStartInput) {
        LocalDate monday = weekStartInput.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        Week w = week(userId, monday);
        WeeklyInputs inputs = assemble(userId, w);
        WeeklyReviewComposer.Composed composed = WeeklyReviewComposer.compose(inputs);
        Instant now = clock.instant();

        WeeklyReview review = reviews.findByUserIdAndWeekStart(userId, monday)
                .map(existing -> {
                    existing.regenerate(composed.summary(), composed.content(), now);
                    return existing;
                })
                .orElseGet(() -> WeeklyReview.create(userId, w.start(), w.end(),
                        composed.summary(), composed.content(), now));
        return reviews.save(review);
    }

    private WeeklyInputs assemble(UUID userId, Week w) {
        List<Task> allTasks = tasks.findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(userId);

        int tasksCompleted = (int) allTasks.stream()
                .filter(t -> t.getStatus() == TaskStatus.DONE && inWindow(t.getCompletedAt(), w))
                .count();

        List<DailyPlan> plans = dailyPlans.findByUserIdAndPlanDateBetweenOrderByPlanDateAsc(
                userId, w.start(), w.end());
        java.util.Set<UUID> plannedTaskIds = plans.stream()
                .flatMap(p -> p.getEntries().stream())
                .map(e -> e.getTaskId())
                .collect(Collectors.toSet());
        int tasksPlanned = plannedTaskIds.size();

        List<Evidence> allEvidence = evidence.findByUserIdAndDeletedAtIsNullOrderByOccurredAtDescCreatedAtDesc(userId);
        int evidenceLogged = (int) allEvidence.stream().filter(e -> inWindow(e.getCreatedAt(), w)).count();

        List<Project> allProjects = projects.findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(userId);
        int projectsAdvanced = (int) allProjects.stream()
                .filter(p -> inWindow(p.getStartedAt(), w) || inWindow(p.getCompletedAt(), w))
                .count();

        int milestonesHit = (int) milestones.countCompletedInWindow(userId, w.fromInstant(), w.toInstant());

        // skill moves — target/assessed skills whose latest score rose across the week
        Map<UUID, String> skillNames = skills.findAll().stream()
                .collect(Collectors.toMap(Skill::getId, Skill::getCanonicalName));
        List<SkillAssessment> history = skillService.assessmentHistory(userId);
        Map<UUID, Double> before = asOfOverall(history, w.fromInstant());
        Map<UUID, Double> after = asOfOverall(history, w.toInstant());
        List<SkillMove> skillMoves = new ArrayList<>();
        for (var e : after.entrySet()) {
            double from = before.getOrDefault(e.getKey(), 0.0);
            if (e.getValue() > from + 0.005) {
                skillMoves.add(new SkillMove(e.getKey(), skillNames.getOrDefault(e.getKey(), "?"),
                        from, e.getValue()));
            }
        }

        // readiness moves — per active target, first vs last trend point in the week
        List<ReadinessMove> readinessMoves = new ArrayList<>();
        for (var tws : targets.activeWithSkillsFor(userId)) {
            try {
                NamedTrend trend = readiness.readinessTrendFor(userId, null, w.fromInstant(), w.toInstant());
                List<ReadinessPoint> pts = trend.points();
                if (pts.size() >= 2 && pts.get(pts.size() - 1).overall() > pts.get(0).overall() + 0.005) {
                    readinessMoves.add(new ReadinessMove(trend.targetName(),
                            pts.get(0).overall(), pts.get(pts.size() - 1).overall()));
                }
                break;   // one primary target per user in M6
            } catch (RuntimeException ignored) {
                break;
            }
        }

        // objective moves — from the progress events in the window
        List<ObjectiveMove> objectiveMoves = objectiveMoves(userId, w);

        // missed — planned or due this week, still not done
        List<MissedTask> missed = allTasks.stream()
                .filter(t -> t.getStatus() != TaskStatus.DONE && t.getStatus() != TaskStatus.SKIPPED)
                .filter(t -> plannedTaskIds.contains(t.getId())
                        || (t.getDueAt() != null && inWindow(t.getDueAt(), w)))
                .map(t -> new MissedTask(t.getId(), t.getTitle(),
                        objectiveTitle(t.getObjectiveId()), t.getDueAt()))
                .limit(10)
                .toList();

        // next — top of the current priority ordering among open tasks
        List<NextAction> next = allTasks.stream()
                .filter(t -> !t.getStatus().isTerminal() && t.getStatus() != TaskStatus.BLOCKED)
                .filter(t -> t.getPriorityScore() != null)
                .sorted(Comparator.comparing(Task::getPriorityScore).reversed())
                .limit(NEXT_LIMIT)
                .map(t -> new NextAction(t.getId(), t.getTitle(),
                        t.getPriorityScore().doubleValue(), topFactor(t)))
                .toList();

        return new WeeklyInputs(tasksCompleted, tasksPlanned, evidenceLogged, projectsAdvanced,
                milestonesHit, skillMoves, readinessMoves, objectiveMoves, missed, next);
    }

    private List<ObjectiveMove> objectiveMoves(UUID userId, Week w) {
        List<ObjectiveProgressEvent> events = objectiveEvents
                .findByUserIdAndRecordedAtBetweenOrderByRecordedAtAsc(userId, w.fromInstant(), w.toInstant());
        if (events.isEmpty()) {
            return List.of();
        }
        Map<UUID, String> titles = objectives.findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(userId).stream()
                .collect(Collectors.toMap(Objective::getId, Objective::getTitle));
        Map<UUID, List<ObjectiveProgressEvent>> byObj = events.stream()
                .collect(Collectors.groupingBy(ObjectiveProgressEvent::getObjectiveId, LinkedHashMap::new,
                        Collectors.toList()));
        List<ObjectiveMove> moves = new ArrayList<>();
        for (var en : byObj.entrySet()) {
            List<ObjectiveProgressEvent> es = en.getValue();
            ObjectiveProgressEvent first = es.get(0);
            ObjectiveProgressEvent last = es.get(es.size() - 1);
            moves.add(new ObjectiveMove(titles.getOrDefault(en.getKey(), "(removed)"),
                    first.getProgress().doubleValue(), last.getProgress().doubleValue(),
                    first.getStatus().value(), last.getStatus().value()));
        }
        return moves;
    }

    private static Map<UUID, Double> asOfOverall(List<SkillAssessment> historyNewestFirst, Instant asOf) {
        Map<UUID, Double> latest = new HashMap<>();
        for (SkillAssessment a : historyNewestFirst) {
            if (!a.getAssessedAt().isAfter(asOf)) {
                latest.putIfAbsent(a.getSkillId(), a.overall());
            }
        }
        return latest;
    }

    private String objectiveTitle(UUID objectiveId) {
        if (objectiveId == null) {
            return null;
        }
        return objectives.findById(objectiveId).map(Objective::getTitle).orElse(null);
    }

    private static String topFactor(Task t) {
        var reason = t.getPriorityReason();
        if (reason == null || reason.factors() == null || reason.factors().isEmpty()) {
            return "priority order";
        }
        return reason.factors().entrySet().stream()
                .max(Comparator.comparingDouble(e -> e.getValue().contribution()))
                .map(e -> e.getKey().replaceAll("([A-Z])", " $1").toLowerCase().trim())
                .orElse("priority order");
    }

    private Week week(UUID userId, LocalDate monday) {
        ZoneId zone = profiles.findById(userId)
                .map(p -> safeZone(p.getTimezone()))
                .orElse(ZoneId.of("UTC"));
        LocalDate end = monday.plusDays(6);
        return new Week(monday, end,
                monday.atStartOfDay(zone).toInstant(),
                monday.plusWeeks(1).atStartOfDay(zone).toInstant());
    }

    private static ZoneId safeZone(String tz) {
        try {
            return tz == null ? ZoneId.of("UTC") : ZoneId.of(tz);
        } catch (RuntimeException ex) {
            return ZoneId.of("UTC");
        }
    }

    private LocalDate lastCompletedWeekStart(UUID userId) {
        ZoneId zone = profiles.findById(userId).map(p -> safeZone(p.getTimezone())).orElse(ZoneId.of("UTC"));
        return LocalDate.now(clock.withZone(zone))
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                .minusWeeks(1);
    }

    private static boolean inWindow(Instant instant, Week w) {
        return instant != null && !instant.isBefore(w.fromInstant()) && instant.isBefore(w.toInstant());
    }

    /** ISO-week key like {@code 2026-W36} for the {@link com.orbit.platform.common.jobs.JobRunner} run_key. */
    public static String isoWeekKey(LocalDate anyDay) {
        LocalDate monday = anyDay.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        int week = monday.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
        int year = monday.get(IsoFields.WEEK_BASED_YEAR);
        return "%d-W%02d".formatted(year, week);
    }
}
