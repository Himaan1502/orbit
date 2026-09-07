package com.orbit.platform.skills.graph;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * An in-memory view of the global skill dependency graph (ADR-019 §3). Pure — no
 * I/O; built per request from the (small) edge set. Serves the skill-detail
 * navigation, career-readiness "high-leverage skills", and the Priority Engine's
 * {@code dependencyImpact} factor.
 */
public final class SkillGraph {

    /** {@code prerequisite} → {@code dependent}: knowing the prerequisite helps toward the dependent. */
    public record Edge(UUID prerequisite, UUID dependent, double strength) {
    }

    private final Map<UUID, List<Edge>> outgoing = new HashMap<>();   // by prerequisite
    private final Map<UUID, List<Edge>> incoming = new HashMap<>();   // by dependent
    private final Map<UUID, Set<UUID>> downstreamCache = new HashMap<>();

    public SkillGraph(List<Edge> edges) {
        for (Edge e : edges) {
            outgoing.computeIfAbsent(e.prerequisite(), k -> new ArrayList<>()).add(e);
            incoming.computeIfAbsent(e.dependent(), k -> new ArrayList<>()).add(e);
        }
    }

    /** Skills that are direct prerequisites of {@code skillId}. */
    public List<Edge> prerequisites(UUID skillId) {
        return incoming.getOrDefault(skillId, List.of());
    }

    /** Skills that directly depend on {@code skillId}. */
    public List<Edge> dependents(UUID skillId) {
        return outgoing.getOrDefault(skillId, List.of());
    }

    /** All skills reachable as (transitive) dependents of {@code skillId} — what learning it unblocks. */
    public Set<UUID> downstream(UUID skillId) {
        return downstreamCache.computeIfAbsent(skillId, start -> {
            Set<UUID> seen = new HashSet<>();
            Deque<UUID> queue = new ArrayDeque<>();
            queue.add(start);
            while (!queue.isEmpty()) {
                UUID u = queue.poll();
                for (Edge e : outgoing.getOrDefault(u, List.of())) {
                    if (seen.add(e.dependent())) {
                        queue.add(e.dependent());
                    }
                }
            }
            seen.remove(start);
            return seen;
        });
    }

    /**
     * Priority Engine {@code dependencyImpact}: the fraction of the target's
     * skills that this skill is a (transitive) prerequisite for. {@code -1} when
     * the skill is not in the graph or the target has no skills — the caller
     * then uses the neutral default.
     */
    public double dependencyImpact(UUID skillId, Set<UUID> targetSkillIds) {
        if (skillId == null || targetSkillIds.isEmpty() || !known(skillId)) {
            return -1;
        }
        Set<UUID> down = downstream(skillId);
        long hits = targetSkillIds.stream().filter(down::contains).count();
        return (double) hits / targetSkillIds.size();
    }

    /**
     * Career-readiness "high-leverage" score for {@code skillId}: how much
     * outstanding, important target work it unblocks. Normalised to {@code [0,1]}
     * by the caller across the target's skills.
     */
    public double leverage(UUID skillId, Map<UUID, Double> targetImportance, Map<UUID, Double> targetGap) {
        double sum = 0;
        for (UUID d : downstream(skillId)) {
            Double imp = targetImportance.get(d);
            Double gap = targetGap.get(d);
            if (imp != null && gap != null) {
                sum += imp * gap;
            }
        }
        return sum;
    }

    public boolean known(UUID skillId) {
        return outgoing.containsKey(skillId) || incoming.containsKey(skillId);
    }
}
