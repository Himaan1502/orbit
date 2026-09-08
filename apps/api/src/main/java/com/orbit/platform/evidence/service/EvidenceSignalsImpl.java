package com.orbit.platform.evidence.service;

import com.orbit.platform.evidence.repository.ProjectEvidenceRepository;
import com.orbit.platform.evidence.repository.SkillEvidenceRepository;
import com.orbit.platform.evidence.repository.SkillEvidenceRepository.SkillEvidenceRow;
import com.orbit.platform.projects.service.ProjectEvidenceCount;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** One bean implementing every read port the evidence domain exposes to others. */
@Component
class EvidenceSignalsImpl implements SkillEvidenceSignals, ProjectSkillEvidenceStrength, ProjectEvidenceCount {

    private final SkillEvidenceRepository skillEvidence;
    private final ProjectEvidenceRepository projectEvidence;

    EvidenceSignalsImpl(SkillEvidenceRepository skillEvidence, ProjectEvidenceRepository projectEvidence) {
        this.skillEvidence = skillEvidence;
        this.projectEvidence = projectEvidence;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Entry> forUser(UUID userId) {
        return skillEvidence.findAllForUser(userId).stream().map(EvidenceSignalsImpl::toEntry).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Entry> forUserAndSkills(UUID userId, Collection<UUID> skillIds) {
        if (skillIds == null || skillIds.isEmpty()) {
            return List.of();
        }
        return skillEvidence.findForUserAndSkills(userId, skillIds).stream()
                .map(EvidenceSignalsImpl::toEntry).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Entry> forUserAndSkill(UUID userId, UUID skillId) {
        return skillEvidence.findForUserAndSkill(userId, skillId).stream()
                .map(EvidenceSignalsImpl::toEntry).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public double strength(UUID projectId, UUID skillId) {
        BigDecimal sum = skillEvidence.sumWeightForProjectAndSkill(projectId, skillId);
        return Math.min(1.0, sum == null ? 0 : sum.doubleValue());
    }

    @Override
    @Transactional(readOnly = true)
    public int forProject(UUID projectId) {
        return (int) projectEvidence.countByProjectId(projectId);
    }

    private static Entry toEntry(SkillEvidenceRow r) {
        return new Entry(r.getSkillId(), r.getWeight().doubleValue(), r.getType());
    }
}
