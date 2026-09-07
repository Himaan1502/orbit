package com.orbit.platform.skills;

import com.orbit.platform.skills.graph.SkillGraphValidator;
import com.orbit.platform.skills.scoring.SkillScoringWeights;
import com.orbit.platform.skills.service.SkillGraphService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wiring for the skills domain (catalog, graph, scoring). */
@Configuration
@EnableConfigurationProperties(SkillScoringWeights.class)
public class SkillsConfig {

    private static final Logger log = LoggerFactory.getLogger(SkillsConfig.class);

    /** Fail fast if the seeded (or later edited) skill graph contains a cycle (ADR-019 §2). */
    @Bean
    ApplicationRunner skillGraphAcyclicCheck(SkillGraphService graphService) {
        return (ApplicationArguments args) -> {
            var edges = graphService.allEdges().stream()
                    .map(d -> new com.orbit.platform.skills.graph.SkillGraph.Edge(
                            d.getPrerequisiteSkillId(), d.getDependentSkillId(), d.strength()))
                    .toList();
            SkillGraphValidator.firstCycle(edges).ifPresent(cycle -> {
                throw new IllegalStateException("Skill dependency graph contains a cycle: " + cycle);
            });
            log.info("Skill dependency graph verified acyclic ({} edges).", edges.size());
        };
    }
}
