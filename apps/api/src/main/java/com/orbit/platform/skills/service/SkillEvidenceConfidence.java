package com.orbit.platform.skills.service;

import java.util.Map;
import java.util.UUID;

/**
 * Read port the {@code skills} domain uses to fold logged evidence into a skill
 * assessment's {@code confidence} label (ADR-026 §1) without depending on the
 * {@code evidence} domain (Blueprint §5 — {@code evidence} depends on
 * {@code skills}, not the other way round). Implemented in {@code evidence}.
 *
 * <p>Values are {@code self-reported} / {@code partially-verified} /
 * {@code verified} — the {@code caller} decides whether to show {@code null}
 * instead when the skill is un-assessed.
 */
public interface SkillEvidenceConfidence {

    /** Evidence-derived confidence for one skill (never {@code null} — {@code self-reported} with no evidence). */
    String forSkill(UUID userId, UUID skillId);

    /** …for every skill the user has any evidence for. Skills absent from the map are {@code self-reported}. */
    Map<UUID, String> byUser(UUID userId);
}
