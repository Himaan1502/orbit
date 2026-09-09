package com.orbit.platform.common.jobs;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * Enables {@code @Scheduled} (ADR-029). A small dedicated pool so a slow job
 * cannot starve the others; jobs go through {@link JobRunner} for durability.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {

    @Bean
    TaskScheduler orbitTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("orbit-job-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(30);
        return scheduler;
    }
}
