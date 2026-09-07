package com.orbit.platform.career;

import com.orbit.platform.career.readiness.ReadinessProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Wiring for the career domain (targets, readiness). */
@Configuration
@EnableConfigurationProperties(ReadinessProperties.class)
public class CareerConfig {
}
