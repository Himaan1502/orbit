package com.orbit.platform.career.web.dto;

import com.orbit.platform.career.model.CareerTarget;
import com.orbit.platform.career.model.CareerTargetSkill;
import com.orbit.platform.career.readiness.CareerReadinessCalculator.Component;
import com.orbit.platform.career.readiness.CareerReadinessCalculator.CriticalGap;
import com.orbit.platform.career.readiness.CareerReadinessCalculator.EvidenceGap;
import com.orbit.platform.career.readiness.CareerReadinessCalculator.HighLeverageSkill;
import com.orbit.platform.career.readiness.CareerReadinessCalculator.ProjectGap;
import com.orbit.platform.career.service.CareerReadinessService.NamedReadiness;
import com.orbit.platform.career.service.CareerTargetService.TargetWithSkills;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Request / response shapes for {@code /api/v1/career}. */
public final class CareerDtos {

    private CareerDtos() {
    }

    public record CareerTargetSkillInput(
            @NotNull UUID skillId,
            @DecimalMin("0") @DecimalMax("1") Double importance,
            @DecimalMin("0") @DecimalMax("1") Double interviewWeight,
            @DecimalMin("0") @DecimalMax("1") Double productionWeight) {
    }

    public record CreateCareerTargetRequest(
            @NotBlank @Size(max = 160) String name,
            @Size(max = 80) String roleFamily,
            @Size(max = 80) String specialization,
            @Min(1) @Max(5) Integer priority,
            LocalDate targetDate,
            @DecimalMin("0") @DecimalMax("1") Double targetReadiness,
            @Valid List<CareerTargetSkillInput> skills) {
    }

    public record UpdateCareerTargetRequest(
            @Size(max = 160) String name,
            @Size(max = 80) String roleFamily,
            @Size(max = 80) String specialization,
            @Min(1) @Max(5) Integer priority,
            LocalDate targetDate,
            @DecimalMin("0") @DecimalMax("1") Double targetReadiness,
            @Pattern(regexp = "active|archived", message = "Unknown status.") String status,
            @Valid List<CareerTargetSkillInput> skills) {
    }

    public record CareerTargetSkillResponse(
            UUID skillId, double importance, double interviewWeight, double productionWeight) {

        static CareerTargetSkillResponse from(CareerTargetSkill s) {
            return new CareerTargetSkillResponse(s.getSkillId(), s.importanceValue(),
                    s.interviewWeightValue(), s.getProductionWeight().doubleValue());
        }
    }

    public record CareerTargetResponse(
            UUID id, String name, String roleFamily, String specialization,
            int priority, String status, LocalDate targetDate, Double targetReadiness,
            int skillCount, List<CareerTargetSkillResponse> skills) {

        public static CareerTargetResponse from(TargetWithSkills tws) {
            CareerTarget t = tws.target();
            return new CareerTargetResponse(
                    t.getId(), t.getName(), t.getRoleFamily(), t.getSpecialization(),
                    t.getPriority(), t.getStatus().value(), t.getTargetDate(),
                    t.getTargetReadiness() == null ? null : t.getTargetReadiness().doubleValue(),
                    tws.skills().size(),
                    tws.skills().stream().map(CareerTargetSkillResponse::from).toList());
        }
    }

    public record ReadinessResponse(
            UUID careerTargetId, String targetName, double overall, String confidence,
            List<Component> componentBreakdown, List<CriticalGap> criticalGaps,
            List<HighLeverageSkill> highLeverageSkills, List<EvidenceGap> evidenceGaps,
            List<ProjectGap> projectGaps, int assessedSkillCount,
            int targetSkillCount, String explanation, Object trend) {

        public static ReadinessResponse from(NamedReadiness nr) {
            var r = nr.readiness();
            return new ReadinessResponse(
                    nr.careerTargetId(), nr.targetName(), r.overall(), r.confidence(),
                    r.componentBreakdown(), r.criticalGaps(), r.highLeverageSkills(),
                    r.evidenceGaps(), r.projectGaps(),
                    r.assessedSkillCount(), r.targetSkillCount(), r.explanation(), null);
        }
    }

    public record GapsResponse(
            UUID careerTargetId, List<CriticalGap> criticalGaps, List<HighLeverageSkill> highLeverageSkills,
            List<EvidenceGap> evidenceGaps, List<ProjectGap> projectGaps) {

        public static GapsResponse from(NamedReadiness nr) {
            var r = nr.readiness();
            return new GapsResponse(nr.careerTargetId(), r.criticalGaps(), r.highLeverageSkills(),
                    r.evidenceGaps(), r.projectGaps());
        }
    }
}
