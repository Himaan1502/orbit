package com.orbit.platform.execution;

import com.orbit.platform.execution.priority.PriorityWeights;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/** Wiring for the execution domain (tasks, daily plans, priority engine). */
@Configuration
@EnableConfigurationProperties({PriorityWeights.class, PlanningProperties.class})
public class ExecutionConfig {

    /** Injectable clock so scoring / plan generation are testable at a fixed instant. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
