package com.orbit.platform.execution.priority;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Priority Engine v1 (Blueprint §22, ADR-014). Deterministic weighted sum over
 * eight 0–1 factors; {@link #score} is a pure function of its input (same input
 * ⇒ same score and same explanation). In Milestone 2 four factors are neutral
 * {@code 0.5} placeholders until their domains exist (M3+).
 */
@Component
public class PriorityEngine {

    public static final String VERSION = "v1";
    private static final String NEUTRAL = "neutral-default";
    private static final String COMPUTED = "computed";
    private static final String PROXY = "proxy";

    private final PriorityWeights weights;

    public PriorityEngine(PriorityWeights weights) {
        this.weights = weights;
    }

    public PriorityReason score(PriorityInput in) {
        Map<String, PriorityReason.Factor> factors = new LinkedHashMap<>();

        if (in.careerRelevanceSignal() != null) {
            add(factors, "careerRelevance", in.careerRelevanceSignal(), weights.getCareerRelevance(),
                    COMPUTED, "importance of this task's skill for your career target");
        } else {
            add(factors, "careerRelevance", careerRelevance(in.objectivePriority()),
                    weights.getCareerRelevance(), PROXY,
                    "objective priority " + in.objectivePriority() + "/5 (no career target on this task)");
        }
        signalOrNeutral(factors, "skillGap", in.skillGapSignal(), weights.getSkillGap(),
                "your current gap on this task's skill", "no skill attached to this task");
        signalOrNeutral(factors, "dependencyImpact", in.dependencyImpactSignal(),
                weights.getDependencyImpact(),
                "this skill is a prerequisite for other target skills",
                "no skill attached, or skill not in the dependency graph");
        add(factors, "projectImpact", 0.5, weights.getProjectImpact(), NEUTRAL,
                "Projects domain not yet available");
        signalOrNeutral(factors, "interviewValue", in.interviewValueSignal(), weights.getInterviewValue(),
                "interview weight of this skill for your target", "no skill / target on this task");

        Urgency u = urgency(in);
        add(factors, "urgency", u.value(), weights.getUrgency(), COMPUTED, u.detail());

        Fit f = availableTimeFit(in);
        add(factors, "availableTimeFit", f.value(), weights.getAvailableTimeFit(), COMPUTED, f.detail());

        Hist h = historicalCompletion(in);
        add(factors, "historicalCompletionProbability", h.value(),
                weights.getHistoricalCompletionProbability(), h.source(), h.detail());

        double total = factors.values().stream().mapToDouble(PriorityReason.Factor::contribution).sum();
        return PriorityReason.of(clamp(total), VERSION, weights.fingerprint(),
                in.referenceInstant(), factors);
    }

    private void signalOrNeutral(Map<String, PriorityReason.Factor> factors, String name,
                                 Double signal, double weight, String computedDetail, String neutralDetail) {
        if (signal != null) {
            add(factors, name, signal, weight, COMPUTED, computedDetail);
        } else {
            add(factors, name, 0.5, weight, NEUTRAL, neutralDetail);
        }
    }

    // --- factor providers -------------------------------------------------

    /** 1 → 0.2 … 5 → 1.0 (ADR-014 §3). */
    static double careerRelevance(int objectivePriority) {
        int p = Math.max(1, Math.min(5, objectivePriority));
        return 0.2 + (p - 1) / 4.0 * 0.8;
    }

    private record Urgency(double value, String detail) {
    }

    private static Urgency urgency(PriorityInput in) {
        Long days = null;
        boolean fromObjective = false;
        if (in.dueAt() != null) {
            days = daysUntil(in.referenceInstant(), in.dueAt());
        } else if (in.objectiveTargetDate() != null) {
            days = daysUntil(in.referenceInstant(),
                    in.objectiveTargetDate().atTime(LocalTime.MAX).toInstant(ZoneOffset.UTC));
            fromObjective = true;
        }
        if (days == null) {
            return new Urgency(0.3, "no deadline");
        }
        double base;
        if (days <= 0) {
            base = 1.0;
        } else if (days <= 1) {
            base = 0.9;
        } else if (days <= 3) {
            base = 0.75;
        } else if (days <= 7) {
            base = 0.55;
        } else if (days <= 14) {
            base = 0.4;
        } else if (days <= 30) {
            base = 0.25;
        } else {
            base = 0.15;
        }
        double value = fromObjective ? base * 0.8 : base;      // objective date is less specific
        String detail = (days <= 0 ? "overdue" : "due in " + days + " day" + (days == 1 ? "" : "s"))
                + (fromObjective ? " (via objective target date)" : "");
        return new Urgency(clamp(value), detail);
    }

    private record Fit(double value, String detail) {
    }

    private static Fit availableTimeFit(PriorityInput in) {
        if (in.estimatedMinutes() == null || in.estimatedMinutes() <= 0) {
            return new Fit(0.5, "no effort estimate");
        }
        int est = in.estimatedMinutes();
        int budget = Math.max(1, in.availableDailyMinutes());
        if (est <= budget / 2) {
            return new Fit(1.0, est + " min fits comfortably in a " + budget + " min budget");
        }
        if (est <= budget) {
            double value = 1.0 - 0.4 * ((double) (est - budget / 2) / (budget - budget / 2 + 1));
            return new Fit(clamp(value), est + " min fits a " + budget + " min budget");
        }
        return new Fit(clamp(Math.max(0.15, (double) budget / est)),
                est + " min exceeds the " + budget + " min budget");
    }

    private record Hist(double value, String source, String detail) {
    }

    private static Hist historicalCompletion(PriorityInput in) {
        if (in.resolvedTaskCount() < 5) {
            return new Hist(0.5, NEUTRAL, "not enough completion history yet");
        }
        double rate = (double) in.doneTaskCount() / in.resolvedTaskCount();
        return new Hist(clamp(rate), COMPUTED,
                Math.round(rate * 100) + "% of resolved tasks completed (not skipped)");
    }

    // --- helpers --------------------------------------------------------

    private static void add(Map<String, PriorityReason.Factor> map, String name, double value,
                            double weight, String source, String detail) {
        double v = clamp(value);
        map.put(name, new PriorityReason.Factor(round(v), round(weight), round(v * weight), source, detail));
    }

    private static long daysUntil(java.time.Instant from, java.time.Instant to) {
        return Duration.between(from, to).toDays();
    }

    private static double clamp(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }

    private static double round(double v) {
        return Math.round(v * 1_000_000d) / 1_000_000d;
    }
}
