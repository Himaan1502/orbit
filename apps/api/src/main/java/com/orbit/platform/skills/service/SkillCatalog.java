package com.orbit.platform.skills.service;

import java.util.UUID;

/**
 * Minimal read port other domains use to reference a catalogue skill without
 * importing the {@code skills} entities (Blueprint §5). The catalogue is global
 * and not user-scoped.
 */
public interface SkillCatalog {

    boolean exists(UUID skillId);
}
