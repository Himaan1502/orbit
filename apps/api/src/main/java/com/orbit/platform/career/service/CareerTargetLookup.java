package com.orbit.platform.career.service;

import java.util.UUID;

/**
 * Minimal read port other domains use to reference a career target without
 * importing the {@code career} entities (Blueprint §5). Career targets are
 * user-scoped, so ownership is part of the contract — a target the user does not
 * own reads as absent.
 */
public interface CareerTargetLookup {

    boolean existsForUser(UUID careerTargetId, UUID userId);
}
