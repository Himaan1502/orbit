package com.orbit.platform.projects.service;

import java.util.UUID;

/**
 * How many evidence items are linked to a project — for the project list/detail.
 * Implemented in the {@code evidence} domain (Blueprint §5). Projects builds
 * before Evidence in isolation, but at runtime the Evidence bean is always
 * present (single deployable, modular monolith — ADR-001).
 */
public interface ProjectEvidenceCount {

    int forProject(UUID projectId);
}
