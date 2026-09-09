package com.orbit.platform.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orbit.platform.ai.provider.ChatItem;
import com.orbit.platform.ai.provider.ToolSpec;
import com.orbit.platform.ai.provider.ToolSpec.Kind;
import com.orbit.platform.analytics.review.WeeklyReviewService;
import com.orbit.platform.analytics.service.AnalyticsService;
import com.orbit.platform.career.service.CareerReadinessService;
import com.orbit.platform.execution.service.DailyPlanService;
import com.orbit.platform.identity.service.CurrentUserService;
import com.orbit.platform.objectives.service.ObjectiveService;
import com.orbit.platform.projects.service.ProjectService;
import com.orbit.platform.roadmaps.service.RoadmapAdoptionService;
import com.orbit.platform.roadmaps.service.RoadmapIntersectionService;
import com.orbit.platform.skills.model.Skill;
import com.orbit.platform.skills.repository.SkillRepository;
import com.orbit.platform.skills.service.SkillService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * The OSCAR tool registry (Blueprint §27, ADR-032 §3). Declares the tools to the
 * provider and dispatches <b>read</b> tools against the caller's own data. Every
 * tool maps to an existing domain call; the backend validates the args
 * (Blueprint §37). An unknown tool name is not in {@link #isKnown} — the
 * allow-list. Mutation tools are declared here but handled by
 * {@link MutationProposer} — they never execute.
 */
@Component
public class ToolRegistry {

    private static final Logger log = LoggerFactory.getLogger(ToolRegistry.class);

    private final ObjectMapper json;
    private final ObjectiveService objectives;
    private final ProjectService projects;
    private final CareerReadinessService readiness;
    private final SkillService skills;
    private final SkillRepository skillRepo;
    private final AnalyticsService analytics;
    private final DailyPlanService dailyPlans;
    private final WeeklyReviewService weeklyReviews;
    private final RoadmapAdoptionService roadmapAdoption;
    private final RoadmapIntersectionService roadmapIntersection;
    private final CurrentUserService currentUser;

    public ToolRegistry(ObjectMapper json, ObjectiveService objectives, ProjectService projects,
                        CareerReadinessService readiness, SkillService skills, SkillRepository skillRepo,
                        AnalyticsService analytics, DailyPlanService dailyPlans,
                        WeeklyReviewService weeklyReviews, RoadmapAdoptionService roadmapAdoption,
                        RoadmapIntersectionService roadmapIntersection, CurrentUserService currentUser) {
        this.json = json;
        this.objectives = objectives;
        this.projects = projects;
        this.readiness = readiness;
        this.skills = skills;
        this.skillRepo = skillRepo;
        this.analytics = analytics;
        this.dailyPlans = dailyPlans;
        this.weeklyReviews = weeklyReviews;
        this.roadmapAdoption = roadmapAdoption;
        this.roadmapIntersection = roadmapIntersection;
        this.currentUser = currentUser;
    }

    // ===== declarations handed to the provider =====

    private static final String NO_ARGS = "{\"type\":\"object\",\"properties\":{},\"required\":[]}";

    private static final List<ToolSpec> SPECS = List.of(
            read("get_current_objectives",
                    "The caller's objectives with status, priority (1-5) and progress (0-1).", NO_ARGS),
            read("get_active_career_target",
                    "The caller's primary career target: readiness, component breakdown, critical gaps, "
                            + "high-leverage skills.", NO_ARGS),
            read("get_skill_gap",
                    "Critical skill gaps for the career target. Optional 'skill' filters by name.",
                    "{\"type\":\"object\",\"properties\":{\"skill\":{\"type\":\"string\"}},\"required\":[]}"),
            read("get_skill_dependencies",
                    "Prerequisites and dependents of a skill in the ORBIT skill graph.",
                    "{\"type\":\"object\",\"properties\":{\"skill\":{\"type\":\"string\"}},\"required\":[\"skill\"]}"),
            read("get_project_status",
                    "The caller's projects: status and expected-vs-demonstrated skill contribution.", NO_ARGS),
            read("get_today_plan",
                    "Today's daily plan: its tasks and the available/allocated minutes.", NO_ARGS),
            read("get_recent_execution",
                    "Execution analytics over a recent window: completion rate, throughput, status counts, "
                            + "blockers. Optional 'weeks' (default 2).",
                    "{\"type\":\"object\",\"properties\":{\"weeks\":{\"type\":\"integer\"}},\"required\":[]}"),
            read("get_weekly_metrics",
                    "The latest weekly review summary plus this-window execution metrics.", NO_ARGS),
            read("get_roadmap_intersection",
                    "Shared high-leverage skills across the caller's adopted roadmaps (needs >= 2).", NO_ARGS),

            mutation("create_task",
                    "Propose creating a task under an objective. Never executes — the user confirms.",
                    "{\"type\":\"object\",\"properties\":{\"objectiveId\":{\"type\":\"string\"},"
                            + "\"title\":{\"type\":\"string\"},\"description\":{\"type\":\"string\"},"
                            + "\"estimatedMinutes\":{\"type\":\"integer\"},\"skillId\":{\"type\":\"string\"},"
                            + "\"projectId\":{\"type\":\"string\"},\"dueAt\":{\"type\":\"string\"}},"
                            + "\"required\":[\"objectiveId\",\"title\"]}"),
            mutation("complete_task",
                    "Propose marking a task complete. Never executes — the user confirms.",
                    "{\"type\":\"object\",\"properties\":{\"taskId\":{\"type\":\"string\"}},\"required\":[\"taskId\"]}"),
            mutation("update_objective",
                    "Propose changing an objective's title, description, priority (1-5), status "
                            + "(active|completed|archived) or targetDate. Never executes — the user confirms.",
                    "{\"type\":\"object\",\"properties\":{\"objectiveId\":{\"type\":\"string\"},"
                            + "\"title\":{\"type\":\"string\"},\"description\":{\"type\":\"string\"},"
                            + "\"priority\":{\"type\":\"integer\"},\"status\":{\"type\":\"string\"},"
                            + "\"targetDate\":{\"type\":\"string\"}},\"required\":[\"objectiveId\"]}"),
            mutation("create_project",
                    "Propose creating a project. Never executes — the user confirms.",
                    "{\"type\":\"object\",\"properties\":{\"name\":{\"type\":\"string\"},"
                            + "\"description\":{\"type\":\"string\"},\"objectiveId\":{\"type\":\"string\"}},"
                            + "\"required\":[\"name\"]}"),
            mutation("record_evidence",
                    "Propose logging a piece of evidence. Never executes — the user confirms.",
                    "{\"type\":\"object\",\"properties\":{\"type\":{\"type\":\"string\"},"
                            + "\"title\":{\"type\":\"string\"},\"description\":{\"type\":\"string\"},"
                            + "\"sourceUrl\":{\"type\":\"string\"},\"skillId\":{\"type\":\"string\"}},"
                            + "\"required\":[\"type\",\"title\"]}"));

    private static final Map<String, ToolSpec> BY_NAME = SPECS.stream()
            .collect(java.util.stream.Collectors.toMap(ToolSpec::name, s -> s, (a, b) -> a, LinkedHashMap::new));

    public List<ToolSpec> specs() {
        return SPECS;
    }

    public boolean isKnown(String name) {
        return BY_NAME.containsKey(name);
    }

    public boolean isMutation(String name) {
        ToolSpec s = BY_NAME.get(name);
        return s != null && s.isMutation();
    }

    private static ToolSpec read(String name, String description, String schema) {
        return new ToolSpec(name, description, schema, Kind.READ);
    }

    private static ToolSpec mutation(String name, String description, String schema) {
        return new ToolSpec(name, description, schema, Kind.MUTATION);
    }

    // ===== read-tool dispatch =====

    /**
     * Execute a read tool against the caller's data; returns a JSON string for
     * the model. Not {@code @Transactional} — each domain service owns its
     * read-only tx, so a throwing tool never poisons a surrounding transaction.
     */
    public String dispatchRead(ChatItem.ToolCall call) {
        String name = call.name();
        if (!isKnown(name)) {
            return err("Unknown tool '" + name + "' — not in the OSCAR registry.");
        }
        if (isMutation(name)) {
            return err("'" + name + "' is a mutation tool — it is never executed; propose it instead.");
        }
        JsonNode args = ToolArgs.parse(json, call.argumentsJson());
        try {
            Object result = switch (name) {
                case "get_current_objectives" -> objectives.listMine(null);
                case "get_active_career_target" -> safeReadiness();
                case "get_skill_gap" -> skillGap(ToolArgs.text(args, "skill"));
                case "get_skill_dependencies" -> skillDependencies(ToolArgs.text(args, "skill"));
                case "get_project_status" -> projectStatus();
                case "get_today_plan" -> dailyPlans.findForDate(LocalDate.now()).orElse(null);
                case "get_recent_execution" -> analytics.execution(
                        LocalDate.now().minusWeeks(clampWeeks(args)), LocalDate.now());
                case "get_weekly_metrics" -> weeklyMetrics();
                case "get_roadmap_intersection" -> roadmapIntersectionResult();
                default -> Map.of("error", "no dispatcher for '" + name + "'");
            };
            return json.writeValueAsString(Map.of("tool", name, "result", result));
        } catch (com.orbit.platform.common.exception.ApiException e) {
            return err(e.getMessage());
        } catch (RuntimeException | com.fasterxml.jackson.core.JacksonException e) {
            log.warn("event=oscar.tool.failed tool={} error={}", name, e.toString());
            return err("Tool '" + name + "' failed to run.");
        }
    }

    private Object safeReadiness() {
        try {
            return readiness.readiness(null);
        } catch (RuntimeException e) {
            return Map.of("status", "no-career-target-set");
        }
    }

    private Object skillGap(String skillName) {
        Object r = safeReadiness();
        if (!(r instanceof CareerReadinessService.NamedReadiness nr)) {
            return r;
        }
        var gaps = nr.readiness().criticalGaps();
        if (skillName != null && !skillName.isBlank()) {
            String needle = skillName.toLowerCase(Locale.ROOT);
            gaps = gaps.stream().filter(g -> g.skill().toLowerCase(Locale.ROOT).contains(needle)).toList();
        }
        return Map.of("target", nr.targetName(), "criticalGaps", gaps,
                "highLeverageSkills", nr.readiness().highLeverageSkills());
    }

    private Object skillDependencies(String skillName) {
        Skill skill = resolveSkill(skillName);
        if (skill == null) {
            return Map.of("error", "no skill in the catalog matches '" + skillName + "'");
        }
        return skills.neighboursForUser(skill.getId(), currentUser.currentUserId());
    }

    private Object projectStatus() {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (var p : projects.listMine()) {
            var pws = projects.getMine(p.getId());
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("id", p.getId());
            r.put("name", p.getName());
            r.put("status", p.getStatus().value());
            r.put("progress", projects.progressOf(pws));
            rows.add(r);
        }
        return rows;
    }

    private Object weeklyMetrics() {
        Map<String, Object> m = new LinkedHashMap<>();
        var mine = weeklyReviews.listMine();
        m.put("latestReview", mine.isEmpty() ? null : Map.of(
                "weekStart", mine.get(0).getWeekStart(),
                "summary", String.valueOf(mine.get(0).getSummary()),
                "status", mine.get(0).getStatus()));
        m.put("execution", analytics.execution(LocalDate.now().minusWeeks(1), LocalDate.now()));
        return m;
    }

    private Object roadmapIntersectionResult() {
        var ids = new ArrayList<>(roadmapAdoption.adoptedRoadmapIds(currentUser.currentUserId()));
        if (ids.size() < 2) {
            return Map.of("status", "need-at-least-2-adopted-roadmaps", "adoptedCount", ids.size());
        }
        try {
            return roadmapIntersection.compute(ids);
        } catch (RuntimeException e) {
            return Map.of("error", String.valueOf(e.getMessage()));
        }
    }

    private Skill resolveSkill(String nameOrId) {
        if (nameOrId == null || nameOrId.isBlank()) {
            return null;
        }
        try {
            return skillRepo.findById(UUID.fromString(nameOrId.trim())).orElse(null);
        } catch (IllegalArgumentException notAUuid) {
            String needle = nameOrId.trim().toLowerCase(Locale.ROOT);
            return skillRepo.findAll().stream()
                    .filter(s -> s.getCanonicalName().toLowerCase(Locale.ROOT).equals(needle))
                    .findFirst()
                    .or(() -> skillRepo.findAll().stream()
                            .filter(s -> s.getCanonicalName().toLowerCase(Locale.ROOT).contains(needle))
                            .findFirst())
                    .orElse(null);
        }
    }

    private static long clampWeeks(JsonNode args) {
        return Math.max(1, Math.min(12, args.path("weeks").asLong(2)));
    }

    private String err(String message) {
        try {
            return json.writeValueAsString(Map.of("error", message));
        } catch (com.fasterxml.jackson.core.JacksonException e) {
            return "{\"error\":\"tool error\"}";
        }
    }
}
