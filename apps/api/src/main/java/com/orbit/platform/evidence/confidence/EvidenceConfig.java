package com.orbit.platform.evidence.confidence;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wiring for evidence-backed confidence (ADR-026 §1). */
@Configuration
@EnableConfigurationProperties(EvidenceProperties.class)
public class EvidenceConfig {

    private static final Logger log = LoggerFactory.getLogger(EvidenceConfig.class);

    @Bean
    ApplicationRunner evidenceThresholdsCheck(EvidenceProperties properties) {
        return args -> {
            properties.validate();
            log.info("Evidence confidence thresholds: partial={} verified={} (+{} types).",
                    properties.partialThreshold(), properties.verifiedThreshold(),
                    properties.verifiedMinTypes());
        };
    }
}
