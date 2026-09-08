package com.orbit.platform.career.readiness;

import com.orbit.platform.skills.graph.SkillGraph;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Pure career-readiness aggregation (Blueprint §24, ADR-018, amended by
 * ADR-026 §2). No I/O — the caller loads the target's skills, the user's
 * capabilities, evidence strength and project coverage per skill, and the skill
 * graph. Given the same inputs it always returns the same numbers (Blueprint
 * §43 — "readiness is reproducible").
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

    /** A skill the user can do (capability ≥ bar) but has not demonstrated (evidence ≈ 0). */
    public record EvidenceGap(UUID skillId, String skill, double importance, double capability) {
    }

    /** A target skill with ~no demonstrated project work. */
    public record ProjectGap(UUID skillId, String skill, double importance, double capability) {
    }

    public record Readiness(
            double overall,
            String confidence,
            List<Component> componentBreakdown,
            List<CriticalGap> criticalGaps,
            List<HighLeverageSkill> highLeverageSkills,
            List<EvidenceGap> evidenceGaps,
            List<ProjectGap> projectGaps,
            int assessedSkillCount,
            int targetSkillCount,
            String explanation) {
    }

    public static Readiness compute(List<TargetSkill> targetSkills, Map<UUID, Capability> capabilities,
                                    Map<UUID, Double> evidenceStrengthBySkill,
                                    Map<UUID, Double> projectCoverageBySkill,
                                    SkillGraph graph, ReadinessProperties weights) {
        int assessed = (int) targetSkills.stream().filter(t -> capabilities.containsKey(t.skillId())).count();

        double skillCapability = weightedMean(targetSkills,
                TargetSkill::importance, t -> cap(capabilities, t.skillId()).overall());
        double interviewReadiness = weightedMean(targetSkills,
                TargetSkill::interviewWeight, t -> cap(capabilities, t.skillId()).interview());
        double evidenceCoverage = weightedMean(targetSkills,
                TargetSkill::importance, t -> clamp(evidenceStrengthBySkill.getOrDefault(t.skillId(), 0.0)));
        double projectCoverage = weightedMean(targetSkills,
                TargetSkill::importance, t -> clamp(projectCoverageBySkill.getOrDefault(t.skillId(), 0.0)));

        double wCap = weights.skillCapabilityWeight();
        double wInt = weights.interviewReadinessWeight();
        double wEv = weights.evidenceCoverageWeight();
        double wPr = weights.projectCoverageWeight();
        double norm = wCap + wInt + wEv + wPr;
        double overall = norm == 0 ? 0
                : (skillCapability * wCap + interviewReadiness * wInt
                   + evidenceCoverage * wEv + projectCoverage * wPr) / norm;

        List<Component> breakdown = List.of(
                new Component("skillCapability", round(skillCapability), round(wCap / norm), "computed"),
                new Component("interviewReadiness", round(interviewReadiness), round(wInt / norm), "computed"),
                new Component("evidenceCoverage", round(evidenceCoverage), round(wEv / norm), "computed"),
                new Component("projectCoverage", round(projectCoverage), round(wPr / norm), "computed"));

        String confidence = confidence(evidenceCoverage, projectCoverage);

        return new Readiness(
                round(overall),
                confidence,
                breakdown,
                criticalGaps(targetSkills, capabilities),
                highLeverageSkills(targetSkills, capabilities, graph),
                evidenceGaps(targetSkills, capabilities, evidenceStrengthBySkill,
                        weights.evidenceGapCapabilityBar()),
                projectGaps(targetSkills, capabilities, projectCoverageBySkill),
                assessed,
                targetSkills.size(),
                explanation(assessed, targetSkills.size()));
    }

    private static String confidence(double evidenceCoverage, double projectCoverage) {
        if (evidenceCoverage >= 0.5 && projectCoverage >= 0.3) {
            return "partially-verified";
        }
        return "self-reported";
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

    private static List<EvidenceGap> evidenceGaps(List<TargetSkill> targetSkills,
                                                  Map<UUID, Capability> capabilities,
                                                  Map<UUID, Double> evidenceStrengthBySkill, double bar) {
        return targetSkills.stream()
                .filter(t -> cap(capabilities, t.skillId()).overall() >= bar)
                .filter(t -> evidenceStrengthBySkill.getOrDefault(t.skillId(), 0.0) < 0.001)
                .map(t -> new EvidenceGap(t.skillId(), t.name(), round(t.importance()),
                        round(cap(capabilities, t.skillId()).overall())))
                .sorted(Comparator.comparingDouble(EvidenceGap::importance).reversed())
                .limit(TOP_N)
                .toList();
    }

    private static List<ProjectGap> projectGaps(List<TargetSkill> targetSkills,
                                                Map<UUID, Capability> capabilities,
                                                Map<UUID, Double> projectCoverageBySkill) {
        return targetSkills.stream()
                .filter(t -> projectCoverageBySkill.getOrDefault(t.skillId(), 0.0) < 0.001)
                .map(t -> new ProjectGap(t.skillId(), t.name(), round(t.importance()),
                        round(cap(capabilities, t.skillId()).overall())))
                .sorted(Comparator.comparingDouble(ProjectGap::importance).reversed())
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
        return "Based on " + assessed + " of " + target + " target skills self-assessed, plus logged "
                + "evidence and demonstrated project work, weighted by this target.";
    }

    private static double clamp(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }

    private static double round(double v) {
        return Math.round(v * 1000) / 1000.0;
    }
}
