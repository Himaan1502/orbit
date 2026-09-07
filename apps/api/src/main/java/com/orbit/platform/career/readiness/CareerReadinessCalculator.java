package com.orbit.platform.career.readiness;

import com.orbit.platform.skills.graph.SkillGraph;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Pure career-readiness aggregation (Blueprint §24, ADR-018). No I/O — the
 * caller loads the target's skills, the user's capabilities and the skill graph
 * and passes them in. Given the same inputs it always returns the same numbers
 * (Blueprint §43 — "readiness is reproducible").
 */
public final class CareerReadinessCalculator {

    private static final int TOP_N = 5;

    private CareerReadinessCalculator() {
    }

    public record TargetSkill(UUID skillId, String name, double importance, double interviewWeight) {
    }

    /** The user's latest assessment for a skill: overall score + the interview dimension, both 0..1. */
    public record Capability(double overall, double interview) {
    }

    public record Component(String component, Double value, double weight, String status) {
    }

    public record CriticalGap(UUID skillId, String skill, double importance, double capability, double gap) {
    }

    public record HighLeverageSkill(UUID skillId, String skill, int unlocks, double leverage) {
    }

    public record Readiness(
            double overall,
            String confidence,
            List<Component> componentBreakdown,
            List<CriticalGap> criticalGaps,
            List<HighLeverageSkill> highLeverageSkills,
            int assessedSkillCount,
            int targetSkillCount,
            String explanation) {
    }

    public static Readiness compute(List<TargetSkill> targetSkills, Map<UUID, Capability> capabilities,
                                    SkillGraph graph, ReadinessProperties weights) {
        int assessed = (int) targetSkills.stream().filter(t -> capabilities.containsKey(t.skillId())).count();

        double skillCapability = weightedMean(targetSkills,
                TargetSkill::importance, t -> cap(capabilities, t.skillId()).overall());
        double interviewReadiness = weightedMean(targetSkills,
                TargetSkill::interviewWeight, t -> cap(capabilities, t.skillId()).interview());

        double wCap = weights.skillCapabilityWeight();
        double wInt = weights.interviewReadinessWeight();
        double norm = wCap + wInt;
        double overall = norm == 0 ? 0 : (skillCapability * wCap + interviewReadiness * wInt) / norm;

        List<Component> breakdown = List.of(
                new Component("skillCapability", round(skillCapability), round(wCap / norm), "computed"),
                new Component("interviewReadiness", round(interviewReadiness), round(wInt / norm), "computed"),
                new Component("evidenceCoverage", null, 0.0, "not-tracked-until-evidence-engine"),
                new Component("projectCoverage", null, 0.0, "not-tracked-until-project-engine"));

        return new Readiness(
                round(overall),
                "self-reported",
                breakdown,
                criticalGaps(targetSkills, capabilities),
                highLeverageSkills(targetSkills, capabilities, graph),
                assessed,
                targetSkills.size(),
                explanation(assessed, targetSkills.size()));
    }

    private static Capability cap(Map<UUID, Capability> capabilities, UUID skillId) {
        return capabilities.getOrDefault(skillId, new Capability(0, 0));   // un-assessed = a gap, not neutral
    }

    private static double weightedMean(List<TargetSkill> skills,
                                       java.util.function.ToDoubleFunction<TargetSkill> weight,
                                       java.util.function.ToDoubleFunction<TargetSkill> value) {
        double wSum = 0;
        double vSum = 0;
        for (TargetSkill s : skills) {
            double w = weight.applyAsDouble(s);
            wSum += w;
            vSum += w * value.applyAsDouble(s);
        }
        return wSum == 0 ? 0 : vSum / wSum;
    }

    private static List<CriticalGap> criticalGaps(List<TargetSkill> targetSkills,
                                                  Map<UUID, Capability> capabilities) {
        return targetSkills.stream()
                .map(t -> {
                    double capability = cap(capabilities, t.skillId()).overall();
                    return new CriticalGap(t.skillId(), t.name(), round(t.importance()),
                            round(capability), round(t.importance() * (1 - capability)));
                })
                .filter(g -> g.gap() > 0.001)
                .sorted(Comparator.comparingDouble(CriticalGap::gap).reversed())
                .limit(TOP_N)
                .toList();
    }

    private static List<HighLeverageSkill> highLeverageSkills(List<TargetSkill> targetSkills,
                                                             Map<UUID, Capability> capabilities,
                                                             SkillGraph graph) {
        Map<UUID, Double> importance = new HashMap<>();
        Map<UUID, Double> gap = new HashMap<>();
        for (TargetSkill t : targetSkills) {
            importance.put(t.skillId(), t.importance());
            gap.put(t.skillId(), 1 - cap(capabilities, t.skillId()).overall());
        }

        List<HighLeverageSkill> scored = new ArrayList<>();
        double max = 0;
        for (TargetSkill t : targetSkills) {
            double raw = graph.leverage(t.skillId(), importance, gap);
            int unlocks = (int) graph.downstream(t.skillId()).stream()
                    .filter(importance::containsKey).count();
            scored.add(new HighLeverageSkill(t.skillId(), t.name(), unlocks, raw));
            max = Math.max(max, raw);
        }
        double norm = max == 0 ? 1 : max;
        return scored.stream()
                .filter(s -> s.leverage() > 0.001)
                .map(s -> new HighLeverageSkill(s.skillId(), s.skill(), s.unlocks(), round(s.leverage() / norm)))
                .sorted(Comparator.comparingDouble(HighLeverageSkill::leverage).reversed())
                .limit(TOP_N)
                .toList();
    }

    private static String explanation(int assessed, int target) {
        if (target == 0) {
            return "This target has no skills yet — add the capabilities it requires.";
        }
        return "Based on " + assessed + " of " + target + " target skills self-assessed, weighted by "
                + "this target. Evidence and project coverage are not yet tracked.";
    }

    private static double round(double v) {
        return Math.round(v * 1000) / 1000.0;
    }
}
