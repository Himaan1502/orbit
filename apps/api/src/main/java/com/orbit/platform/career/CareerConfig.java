package com.orbit.platform.career;

import com.orbit.platform.career.readiness.ReadinessProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wiring for the career domain (targets, readiness). */
@Configuration
@EnableConfigurationProperties(ReadinessProperties.class)
public class CareerConfig {

    private static final Logger log = LoggerFactory.getLogger(CareerConfig.class);

    /** The readiness component blend must sum to 1 — the context refuses to start otherwise (ADR-026 §2). */
    @Bean
    ApplicationRunner readinessWeightsCheck(ReadinessProperties properties) {
        return args -> {
            properties.validate();
            log.info("Career readiness blend: skillCapability={} interviewReadiness={} "
                            + "evidenceCoverage={} projectCoverage={}.",
                    properties.skillCapabilityWeight(), properties.interviewReadinessWeight(),
                    properties.evidenceCoverageWeight(), properties.projectCoverageWeight());
        };
    }
}
