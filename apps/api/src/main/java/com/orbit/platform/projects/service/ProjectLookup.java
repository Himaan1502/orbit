package com.orbit.platform.projects.service;

import java.util.UUID;

/**
 * Minimal read port other domains use to reference a project without importing
 * the {@code projects} entities (Blueprint §5). Projects are user-scoped, so
 * ownership is part of the contract.
 */
public interface ProjectLookup {

    boolean existsForUser(UUID projectId, UUID userId);
}
