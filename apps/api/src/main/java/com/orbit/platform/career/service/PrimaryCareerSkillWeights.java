package com.orbit.platform.career.service;

import java.util.Map;
import java.util.UUID;

/**
 * Read port: the skill importance weights of a user's <em>primary</em> career
 * target (highest-priority active one), for other domains that need
 * "career relevance" without importing the {@code career} entities — e.g. the
 * Roadmap Intersection (ADR-023 §2). Empty map when the user has no career target.
 */
public interface PrimaryCareerSkillWeights {

    Map<UUID, Double> importanceBySkill(UUID userId);
}
