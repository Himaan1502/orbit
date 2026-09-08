package com.orbit.platform.projects.service;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * Read port for resolving project ids to names without importing the
 * {@code projects} entities (Blueprint §5). Not user-scoped — callers pass ids
 * they already have from an owned context.
 */
public interface ProjectNames {

    Map<UUID, String> namesByIds(Collection<UUID> projectIds);
}
