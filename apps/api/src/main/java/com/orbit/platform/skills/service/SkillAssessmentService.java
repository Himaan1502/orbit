package com.orbit.platform.skills.service;

import com.orbit.platform.common.exception.ApiException;
import com.orbit.platform.identity.service.CurrentUserService;
import com.orbit.platform.skills.model.SkillAssessment;
import com.orbit.platform.skills.repository.SkillAssessmentRepository;
import com.orbit.platform.skills.repository.SkillRepository;
import com.orbit.platform.skills.scoring.SkillDimensions;
import com.orbit.platform.skills.scoring.SkillScore;
import com.orbit.platform.skills.scoring.SkillScoringEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

/**
 * Records self-assessments (Blueprint §12, ADR-017). Append-only: each call
 * writes a new immutable row; the {@code overall_score} is computed by the
 * {@link SkillScoringEngine}. In Milestone 3 every assessment is
 * {@code source = self} → {@code confidence = self-reported} (PRD §7).
 */
@Service
public class SkillAssessmentService {

    private static final Logger log = LoggerFactory.getLogger(SkillAssessmentService.class);

    private final SkillAssessmentRepository assessments;
    private final SkillRepository skills;
    private final SkillScoringEngine engine;
    private final SkillEvidenceConfidence evidenceConfidence;
    private final CurrentUserService currentUser;
    private final Clock clock;

    public SkillAssessmentService(SkillAssessmentRepository assessments, SkillRepository skills,
                                  SkillScoringEngine engine, SkillEvidenceConfidence evidenceConfidence,
                                  CurrentUserService currentUser, Clock clock) {
        this.assessments = assessments;
        this.skills = skills;
        this.engine = engine;
        this.evidenceConfidence = evidenceConfidence;
        this.currentUser = currentUser;
        this.clock = clock;
    }

    public record AssessmentInput(
            double knowledge, double practical, double evidence, double interview, double production) {
    }

    /** {@code confidence} reflects the evidence linked to the skill, not just the source (ADR-026 §1). */
    public record AssessmentResult(SkillAssessment assessment, SkillScore score, String confidence) {
    }

    @Transactional
    public AssessmentResult assess(UUID skillId, AssessmentInput input) {
        UUID userId = currentUser.getOrProvision().getId();
        if (skills.findById(skillId).isEmpty()) {
            throw ApiException.notFound("Skill not found.");
        }

        SkillDimensions dims = new SkillDimensions(
                input.knowledge(), input.practical(), input.evidence(), input.interview(), input.production());
        // M3: self-assessment only, so evidence/production dimensions are claims,
        // not verified — the score carries that caveat (ADR-017 §4).
        SkillScore score = engine.score(dims, SkillScore.Confidence.SELF_REPORTED);

        SkillAssessment saved = assessments.save(SkillAssessment.create(
                userId, skillId, dims, score.overall(), "self", clock.instant()));

        String confidence = evidenceConfidence.forSkill(userId, skillId);

        log.info("event=skills.assessment.recorded user_id={} skill_id={} overall={} source=self confidence={}",
                userId, skillId, score.overall(), confidence);
        return new AssessmentResult(saved, score, confidence);
    }

    @Transactional(readOnly = true)
    public List<SkillAssessment> history(UUID skillId) {
        return assessments.findByUserIdAndSkillIdOrderByAssessedAtDesc(currentUser.currentUserId(), skillId);
    }
}
