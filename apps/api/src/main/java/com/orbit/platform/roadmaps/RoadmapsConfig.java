package com.orbit.platform.roadmaps;

import com.orbit.platform.roadmaps.progress.RoadmapProperties;
import com.orbit.platform.roadmaps.repository.RoadmapRepository;
import com.orbit.platform.roadmaps.repository.RoadmapTopicRepository;
import com.orbit.platform.roadmaps.repository.RoadmapTopicSkillRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wiring for the roadmaps domain (catalog, progress, intersection). */
@Configuration
@EnableConfigurationProperties(RoadmapProperties.class)
public class RoadmapsConfig {

    private static final Logger log = LoggerFactory.getLogger(RoadmapsConfig.class);

    /** Validate the roadmap tunables at startup and log what the seed loaded (ADR-022 §6). */
    @Bean
    ApplicationRunner roadmapCatalogCheck(RoadmapProperties properties, RoadmapRepository roadmaps,
                                          RoadmapTopicRepository topics,
                                          RoadmapTopicSkillRepository topicSkills) {
        return args -> {
            properties.validate();
            long roadmapCount = roadmaps.count();
            long topicCount = topics.count();
            long mappingCount = topicSkills.count();
            log.info("Roadmap catalog: {} roadmaps, {} topics, {} topic→skill mappings.",
                    roadmapCount, topicCount, mappingCount);
        };
    }
}
