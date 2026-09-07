package com.orbit.platform.skills.web.dto;

import com.orbit.platform.skills.model.SkillAssessment;
import com.orbit.platform.skills.scoring.SkillScore;
import com.orbit.platform.skills.service.SkillService.Neighbour;
import com.orbit.platform.skills.service.SkillService.SkillNeighbours;
import com.orbit.platform.skills.service.SkillService.SkillWithCapability;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Request / response shapes for {@code /api/v1/skills}. */
public final class SkillDtos {

    private SkillDtos() {
    }

    private static final String RANGE = "Score must be between 0 and 1.";

    public record AssessSkillRequest(
            @NotNull @DecimalMin(value = "0", message = RANGE) @DecimalMax(value = "1", message = RANGE) Double knowledge,
            @NotNull @DecimalMin(value = "0", message = RANGE) @DecimalMax(value = "1", message = RANGE) Double practical,
            @NotNull @DecimalMin(value = "0", message = RANGE) @DecimalMax(value = "1", message = RANGE) Double evidence,
            @NotNull @DecimalMin(value = "0", message = RANGE) @DecimalMax(value = "1", message = RANGE) Double interview,
            @NotNull @DecimalMin(value = "0", message = RANGE) @DecimalMax(value = "1", message = RANGE) Double production) {
    }

    public record SkillSummary(
            UUID id, String canonicalName, String category, String description, Double capability) {

        public static SkillSummary from(SkillWithCapability s) {
            return new SkillSummary(s.skill().getId(), s.skill().getCanonicalName(),
                    s.skill().getCategory(), s.skill().getDescription(), s.capability());
        }
    }

    public record SkillDetail(
            UUID id, String canonicalName, String category, String description,
            Double capability, String confidence,
            DimensionBreakdown dimensions, Instant lastAssessedAt) {

        public static SkillDetail from(SkillWithCapability s) {
            SkillAssessment a = s.latestAssessment().orElse(null);
            return new SkillDetail(
                    s.skill().getId(), s.skill().getCanonicalName(), s.skill().getCategory(),
                    s.skill().getDescription(), s.capability(),
                    a == null ? null : "self-reported",
                    a == null ? null : DimensionBreakdown.from(a),
                    a == null ? null : a.getAssessedAt());
        }
    }

    public record DimensionBreakdown(
            double knowledge, double practical, double evidence, double interview, double production) {

        public static DimensionBreakdown from(SkillAssessment a) {
            var d = a.dimensions();
            return new DimensionBreakdown(
                    d.knowledge(), d.practical(), d.evidence(), d.interview(), d.production());
        }
    }

    public record NeighbourResponse(UUID skillId, String name, double strength, Double capability) {
        static NeighbourResponse from(Neighbour n) {
            return new NeighbourResponse(n.skillId(), n.name(), n.strength(), n.capability());
        }
    }

    public record SkillDependenciesResponse(
            List<NeighbourResponse> prerequisites, List<NeighbourResponse> dependents) {

        public static SkillDependenciesResponse from(SkillNeighbours n) {
            return new SkillDependenciesResponse(
                    n.prerequisites().stream().map(NeighbourResponse::from).toList(),
                    n.dependents().stream().map(NeighbourResponse::from).toList());
        }
    }

    public record AssessmentResponse(
            UUID id, UUID skillId, double overall, String confidence,
            DimensionBreakdown dimensions, List<ScoreComponent> components,
            String assessmentSource, Instant assessedAt) {

        public static AssessmentResponse from(SkillAssessment a, SkillScore score) {
            List<ScoreComponent> comps = score.components().entrySet().stream()
                    .map(e -> new ScoreComponent(e.getKey(), e.getValue().value(),
                            e.getValue().weight(), e.getValue().contribution()))
                    .toList();
            return new AssessmentResponse(
                    a.getId(), a.getSkillId(), score.overall(), score.confidence().value(),
                    DimensionBreakdown.from(a), comps, a.getAssessmentSource(), a.getAssessedAt());
        }
    }

    public record ScoreComponent(String dimension, double value, double weight, double contribution) {
    }
}
