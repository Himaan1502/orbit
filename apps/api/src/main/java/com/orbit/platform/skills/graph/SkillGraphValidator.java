package com.orbit.platform.skills.graph;

import com.orbit.platform.skills.graph.SkillGraph.Edge;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Keeps the skill dependency graph acyclic (Blueprint §12, ADR-019 §2). Pure —
 * used at seed load and any future admin edit. No "justified cycle" escape hatch
 * in Milestone 3.
 */
public final class SkillGraphValidator {

    private SkillGraphValidator() {
    }

    /** The first directed cycle found (as an ordered skill-id path), or empty if the graph is acyclic. */
    public static Optional<List<UUID>> firstCycle(List<Edge> edges) {
        Map<UUID, List<UUID>> adj = new HashMap<>();
        for (Edge e : edges) {
            adj.computeIfAbsent(e.prerequisite(), k -> new ArrayList<>()).add(e.dependent());
        }
        Set<UUID> visited = new HashSet<>();
        for (UUID node : adj.keySet()) {
            if (!visited.contains(node)) {
                List<UUID> cycle = dfs(node, adj, visited, new HashSet<>(), new ArrayList<>());
                if (cycle != null) {
                    return Optional.of(cycle);
                }
            }
        }
        return Optional.empty();
    }

    /** Would adding {@code prerequisite → dependent} to {@code existing} create a cycle? */
    public static boolean wouldCreateCycle(List<Edge> existing, UUID prerequisite, UUID dependent) {
        if (prerequisite.equals(dependent)) {
            return true;
        }
        List<Edge> withCandidate = new ArrayList<>(existing);
        withCandidate.add(new Edge(prerequisite, dependent, 0));
        return firstCycle(withCandidate).isPresent();
    }

    private static List<UUID> dfs(UUID node, Map<UUID, List<UUID>> adj, Set<UUID> visited,
                                  Set<UUID> stack, List<UUID> path) {
        visited.add(node);
        stack.add(node);
        path.add(node);
        for (UUID next : adj.getOrDefault(node, List.of())) {
            if (stack.contains(next)) {
                List<UUID> cycle = new ArrayList<>(path.subList(path.indexOf(next), path.size()));
                cycle.add(next);
                return cycle;
            }
            if (!visited.contains(next)) {
                List<UUID> found = dfs(next, adj, visited, stack, path);
                if (found != null) {
                    return found;
                }
            }
        }
        stack.remove(node);
        path.remove(path.size() - 1);
        return null;
    }
}
