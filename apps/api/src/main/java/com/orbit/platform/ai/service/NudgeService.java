package com.orbit.platform.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orbit.platform.ai.AiProperties;
import com.orbit.platform.ai.provider.AiRequest;
import com.orbit.platform.ai.provider.ChatItem;
import com.orbit.platform.ai.service.ProviderGateway.Attempt;
import com.orbit.platform.ai.web.dto.OscarDtos.Nudge;
import com.orbit.platform.ai.web.dto.OscarDtos.NudgeCard;
import com.orbit.platform.execution.model.Task;
import com.orbit.platform.execution.service.DailyPlanService;
import com.orbit.platform.execution.service.TaskService;
import com.orbit.platform.identity.service.CurrentUserService;
import com.orbit.platform.objectives.model.Objective;
import com.orbit.platform.objectives.service.ObjectiveService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Contextual recommendation cards (ADR-034). Single-shot, <b>no tool rounds</b>,
 * a small surface-focused context; the generator declares <b>no mutation
 * tools</b> — prose-only in this pass. Every result (including "nothing") is
 * cached in Redis keyed by a data fingerprint so an unchanged page costs zero
 * provider calls; a separate hourly budget (isolated from the conversational
 * cap) gates generation; a Redis-down / provider-down / SKIP outcome renders
 * nothing, never an error.
 */
@Service
public class NudgeService {

    private static final Logger log = LoggerFactory.getLogger(NudgeService.class);
    private static final int TOP_TASKS = 6;

    private final ObjectiveService objectives;
    private final TaskService tasks;
    private final DailyPlanService dailyPlans;
    private final CurrentUserService currentUser;
    private final ProviderGateway gateway;
    private final NudgeCache cache;
    private final AiProperties props;
    private final ObjectMapper json;
    private final Clock clock;
    private final String prompt;

    public NudgeService(ObjectiveService objectives, TaskService tasks, DailyPlanService dailyPlans,
                        CurrentUserService currentUser, ProviderGateway gateway, NudgeCache cache,
                        AiProperties props, ObjectMapper json, Clock clock,
                        @Value("classpath:oscar/nudge-prompt-v1.md") Resource promptResource) {
        this.objectives = objectives;
        this.tasks = tasks;
        this.dailyPlans = dailyPlans;
        this.currentUser = currentUser;
        this.gateway = gateway;
        this.cache = cache;
        this.props = props;
        this.json = json;
        this.clock = clock;
        try {
            this.prompt = new String(promptResource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("OSCAR nudge prompt resource missing", e);
        }
    }

    public Nudge nudgeFor(String surface, UUID objectiveId) {
        if (!gateway.anyProviderConfigured()) {
            return Nudge.unavailable();
        }
        UUID userId = currentUser.currentUserId();
        Focus focus = switch (surface == null ? "" : surface) {
            case "today" -> todayFocus();
            case "objective" -> objectiveFocus(objectiveId);
            default -> null;
        };
        if (focus == null) {
            return Nudge.none();
        }

        String base = "oscar:nudge:" + userId + ":" + surface + ":" + focus.surfaceId() + ":";
        String key = base + focus.fingerprint();
        String latestKey = base + "latest";

        Optional<Nudge> hit = cache.get(key).flatMap(this::deserialize);
        if (hit.isPresent()) {
            return hit.get();
        }
        if (cache.hourlyBudgetExhausted(userId, props.nudgeHourlyCap())) {
            return cache.get(latestKey).flatMap(this::deserialize).orElse(Nudge.none());
        }

        try {
            AiRequest req = new AiRequest(prompt,
                    List.of(new ChatItem.UserText("<orbit_context>\n" + focus.contextJson() + "\n</orbit_context>")),
                    List.of(), 320, 0.3);
            Attempt attempt = gateway.callBlocking(req);
            if (attempt == null || attempt.completion().text() == null) {
                return cache.get(latestKey).flatMap(this::deserialize).orElse(Nudge.none());
            }
            String text = attempt.completion().text().strip();
            Nudge nudge = text.isBlank() || text.toUpperCase(Locale.ROOT).startsWith("SKIP")
                    ? Nudge.none()
                    : Nudge.ready(parseCard(text));
            serialize(nudge).ifPresent(j -> {
                cache.put(key, j);
                if (nudge.card() != null) {
                    cache.put(latestKey, j);
                }
            });
            cache.recordGeneration(userId);
            log.info("event=oscar.nudge surface={} user_id={} status={}", surface, userId, nudge.status());
            return nudge;
        } catch (RuntimeException e) {
            log.warn("event=oscar.nudge.failed surface={} error={}", surface, e.toString());
            return cache.get(latestKey).flatMap(this::deserialize).orElse(Nudge.none());
        }
    }

    // ----- surface focus (context + fingerprint) --------------------------

    private record Focus(String surfaceId, String fingerprint, String contextJson) {
    }

    private Focus todayFocus() {
        LocalDate today = LocalDate.now(clock);
        var plan = dailyPlans.findForDate(today).orElse(null);
        List<Task> open = tasks.listMine().stream()
                .filter(t -> t.getStatus().isOpen())
                .sorted(Comparator.comparing(Task::getPriorityScore,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(TOP_TASKS)
                .toList();

        Map<String, Object> ctx = new LinkedHashMap<>();
        ctx.put("today", today.toString());
        ctx.put("dailyPlanGenerated", plan != null);
        ctx.put("dailyPlanTasks", plan == null ? List.of()
                : plan.tasks().stream().map(e -> e.title()).toList());
        ctx.put("availableMinutes", plan == null ? null : plan.availableMinutes());
        ctx.put("openTasks", open.stream().map(t -> Map.of(
                "title", t.getTitle(),
                "status", t.getStatus().value(),
                "priorityScore", String.valueOf(t.getPriorityScore()),
                "dueAt", String.valueOf(t.getDueAt()))).toList());

        String fp = today + "|" + (plan == null ? "noplan" : plan.planDate() + "#" + plan.tasks().size())
                + "|" + open.stream().map(t -> t.getId() + ":" + t.getStatus().value()).sorted().toList().hashCode();
        return new Focus("today", shortHash(fp), toJson(ctx));
    }

    private Focus objectiveFocus(UUID objectiveId) {
        if (objectiveId == null) {
            return null;
        }
        Objective o;
        try {
            o = objectives.requireOwned(objectiveId);
        } catch (RuntimeException notOwned) {
            return null;
        }
        List<Task> objTasks = tasks.listMine().stream()
                .filter(t -> objectiveId.equals(t.getObjectiveId()))
                .toList();

        Map<String, Object> ctx = new LinkedHashMap<>();
        ctx.put("objective", Map.of(
                "title", o.getTitle(),
                "status", o.getStatus().value(),
                "progress", String.valueOf(o.getProgress()),
                "priority", o.getPriority(),
                "targetDate", String.valueOf(o.getTargetDate())));
        ctx.put("tasks", objTasks.stream().map(t -> Map.of(
                "title", t.getTitle(),
                "status", t.getStatus().value(),
                "dueAt", String.valueOf(t.getDueAt()))).toList());
        ctx.put("hasCareerTarget", o.getCareerTargetId() != null);

        String fp = o.getUpdatedAt() + "|" + objTasks.stream()
                .map(t -> t.getId() + ":" + t.getStatus().value()).sorted().toList().hashCode();
        return new Focus(objectiveId.toString(), shortHash(fp), toJson(ctx));
    }

    // ----- helpers ------------------------------------------------------

    private NudgeCard parseCard(String text) {
        String[] lines = text.split("\\R");
        String headline = lines.length > 0 ? stripEndPunct(lines[0].strip()) : "A suggestion";
        String why = null;
        StringBuilder body = new StringBuilder();
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i].strip();
            if (line.isEmpty()) {
                continue;
            }
            if (line.toLowerCase(Locale.ROOT).startsWith("why:")) {
                why = line.substring(4).strip();
            } else if (why == null) {
                if (body.length() > 0) {
                    body.append(' ');
                }
                body.append(line);
            }
        }
        return new NudgeCard(cap(headline, 90), cap(body.toString(), 600), cap(why, 300), null,
                clock.instant());
    }

    private Optional<String> serialize(Nudge n) {
        try {
            return Optional.of(json.writeValueAsString(n));
        } catch (com.fasterxml.jackson.core.JacksonException e) {
            return Optional.empty();
        }
    }

    private Optional<Nudge> deserialize(String raw) {
        try {
            return Optional.of(json.readValue(raw, Nudge.class));
        } catch (com.fasterxml.jackson.core.JacksonException e) {
            return Optional.empty();
        }
    }

    private String toJson(Object o) {
        try {
            return json.writeValueAsString(o);
        } catch (com.fasterxml.jackson.core.JacksonException e) {
            return "{}";
        }
    }

    private static String shortHash(String s) {
        return Integer.toHexString(s.hashCode());
    }

    private static String stripEndPunct(String s) {
        return s.replaceAll("[.:;,\\s]+$", "");
    }

    private static String cap(String s, int max) {
        if (s == null || s.isBlank()) {
            return null;
        }
        String t = s.strip();
        return t.length() <= max ? t : t.substring(0, max - 1) + "…";
    }
}
