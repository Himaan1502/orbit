package com.orbit.platform.evidence.service;

import com.orbit.platform.evidence.model.EvidenceType;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Read port other domains use to fold evidence into their scores without
 * importing the {@code evidence} entities (Blueprint §5). Feeds skill-assessment
 * {@code confidence} (ADR-026 §1) and Career Readiness {@code evidenceCoverage}
 * (ADR-026 §2). Only non-deleted evidence of the given user is returned.
 */
public interface SkillEvidenceSignals {

    /** One {@code skill_evidence} link: which skill, how strongly, and the artifact type. */
    record Entry(UUID skillId, double weight, EvidenceType type) {
    }

    List<Entry> forUser(UUID userId);

    List<Entry> forUserAndSkills(UUID userId, Collection<UUID> skillIds);

    List<Entry> forUserAndSkill(UUID userId, UUID skillId);
}
