package com.orbit.platform.skills.model;

import com.orbit.platform.skills.scoring.SkillDimensions;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One point-in-time capability estimate for a user × skill (Blueprint §12).
 * <strong>Append-only</strong> — rows are never updated or deleted, so a score's
 * provenance is always inspectable (ADR-017 §5). {@code overallScore} is
 * computed on insert from the five dimensions.
 */
@Entity
@Table(name = "skill_assessments", schema = "orbit")
@Getter
@NoArgsConstructor
public class SkillAssessment {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "skill_id", nullable = false, updatable = false)
    private UUID skillId;

    @Column(name = "knowledge_score", nullable = false, precision = 4, scale = 3)
    private BigDecimal knowledgeScore;

    @Column(name = "practical_score", nullable = false, precision = 4, scale = 3)
    private BigDecimal practicalScore;

    @Column(name = "evidence_score", nullable = false, precision = 4, scale = 3)
    private BigDecimal evidenceScore;

    @Column(name = "interview_score", nullable = false, precision = 4, scale = 3)
    private BigDecimal interviewScore;

    @Column(name = "production_score", nullable = false, precision = 4, scale = 3)
    private BigDecimal productionScore;

    @Column(name = "overall_score", nullable = false, precision = 4, scale = 3)
    private BigDecimal overallScore;

    @Column(name = "assessment_source", nullable = false, length = 20)
    private String assessmentSource = "self";

    @Column(name = "assessed_at", nullable = false)
    private Instant assessedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static SkillAssessment create(UUID userId, UUID skillId, SkillDimensions dims,
                                         double overall, String source, Instant assessedAt) {
        SkillAssessment a = new SkillAssessment();
        a.id = UUID.randomUUID();
        a.userId = userId;
        a.skillId = skillId;
        a.knowledgeScore = bd(dims.knowledge());
        a.practicalScore = bd(dims.practical());
        a.evidenceScore = bd(dims.evidence());
        a.interviewScore = bd(dims.interview());
        a.productionScore = bd(dims.production());
        a.overallScore = bd(overall);
        a.assessmentSource = source;
        a.assessedAt = assessedAt;
        return a;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
        if (this.assessedAt == null) {
            this.assessedAt = this.createdAt;
        }
    }

    public SkillDimensions dimensions() {
        return new SkillDimensions(
                knowledgeScore.doubleValue(), practicalScore.doubleValue(), evidenceScore.doubleValue(),
                interviewScore.doubleValue(), productionScore.doubleValue());
    }

    public double overall() {
        return overallScore.doubleValue();
    }

    private static BigDecimal bd(double v) {
        return BigDecimal.valueOf(Math.round(v * 1000) / 1000.0);
    }
}
