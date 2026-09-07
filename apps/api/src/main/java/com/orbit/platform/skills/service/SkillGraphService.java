package com.orbit.platform.skills.service;

import com.orbit.platform.skills.graph.SkillGraph;
import com.orbit.platform.skills.model.SkillDependency;
import com.orbit.platform.skills.repository.SkillDependencyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Builds the in-memory {@link SkillGraph} from {@code skill_dependencies}
 * (ADR-019 §3). The catalog is immutable at runtime in Milestone 3 (ADR-020),
 * so the graph is loaded once and cached for the JVM's lifetime; a future
 * edit/ingestion path calls {@link #invalidate()} after a change.
 */
@Service
public class SkillGraphService {

    private final SkillDependencyRepository dependencies;
    private volatile SkillGraph cached;

    public SkillGraphService(SkillDependencyRepository dependencies) {
        this.dependencies = dependencies;
    }

    @Transactional(readOnly = true)
    public SkillGraph graph() {
        SkillGraph local = cached;
        if (local == null) {
            synchronized (this) {
                if (cached == null) {
                    List<SkillGraph.Edge> edges = dependencies.findAll().stream()
                            .map(d -> new SkillGraph.Edge(
                                    d.getPrerequisiteSkillId(), d.getDependentSkillId(), d.strength()))
                            .toList();
                    cached = new SkillGraph(edges);
                }
                local = cached;
            }
        }
        return local;
    }

    public void invalidate() {
        cached = null;
    }

    @Transactional(readOnly = true)
    public List<SkillDependency> allEdges() {
        return dependencies.findAll();
    }
}
