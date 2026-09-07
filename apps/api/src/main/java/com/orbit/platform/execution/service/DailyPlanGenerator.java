package com.orbit.platform.execution.service;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Pure daily-plan selection (ADR-015 §1–2). No I/O, no security context, no
 * repositories — a future durable job runner calls {@link #generate} with a
 * {@link Request} it assembled for any user. Deterministic: the same request
 * always yields the same plan.
 */
@Component
public class DailyPlanGenerator {

    private static final int MIN_PARTIAL_MINUTES = 15;

    /** A task eligible for today's plan. {@code effortMinutes} already has the default applied. */
    public record Candidate(
            UUID taskId,
            BigDecimal priorityScore,
            Instant dueAt,
            Instant createdAt,
            int effortMinutes,
            boolean alreadyDone) {
    }

    public record Request(LocalDate planDate, int availableMinutes, List<Candidate> candidates) {
    }

    public record Entry(UUID taskId, int position, int allocatedMinutes) {
    }

    public record GeneratedPlan(List<Entry> entries) {
        public int allocatedMinutes() {
            return entries.stream().mapToInt(Entry::allocatedMinutes).sum();
        }
    }

    public GeneratedPlan generate(Request request) {
        List<Candidate> sorted = new ArrayList<>(request.candidates());
        sorted.sort(ordering());

        List<Entry> entries = new ArrayList<>();
        int position = 0;
        int remaining = request.availableMinutes();

        // 1. Tasks already completed in the current plan keep their slot; their
        //    effort still counts as time spent (ADR-015 §3).
        for (Candidate c : sorted) {
            if (c.alreadyDone()) {
                int alloc = Math.max(1, Math.min(c.effortMinutes(), Math.max(1, remaining)));
                entries.add(new Entry(c.taskId(), position++, alloc));
                remaining -= alloc;
            }
        }
        remaining = Math.max(0, remaining);

        // 2. Greedily add the highest-priority open tasks that fit.
        for (Candidate c : sorted) {
            if (c.alreadyDone()) {
                continue;
            }
            if (c.effortMinutes() <= remaining) {
                entries.add(new Entry(c.taskId(), position++, c.effortMinutes()));
                remaining -= c.effortMinutes();
            } else if (remaining >= MIN_PARTIAL_MINUTES) {
                entries.add(new Entry(c.taskId(), position++, remaining));
                remaining = 0;
                break;
            }
            // else: skip this (too big for the gap) and try smaller tasks
        }

        // 3. Never hand back an empty plan when there is work to do.
        if (entries.isEmpty() && !sorted.isEmpty()) {
            Candidate top = sorted.get(0);
            int alloc = Math.min(top.effortMinutes(), Math.max(1, request.availableMinutes()));
            entries.add(new Entry(top.taskId(), 0, Math.max(1, alloc)));
        }

        return new GeneratedPlan(List.copyOf(entries));
    }

    private static Comparator<Candidate> ordering() {
        return Comparator
                .comparing((Candidate c) -> c.priorityScore() == null ? BigDecimal.valueOf(-1) : c.priorityScore(),
                        Comparator.reverseOrder())
                .thenComparing(c -> c.dueAt() == null ? Instant.MAX : c.dueAt())
                .thenComparing(c -> c.createdAt() == null ? Instant.MAX : c.createdAt())
                .thenComparing(Candidate::taskId);
    }
}
