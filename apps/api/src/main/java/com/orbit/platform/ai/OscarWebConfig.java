package com.orbit.platform.ai;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Async plumbing for the OSCAR SSE endpoint (ADR-033 §1). A streamed turn holds
 * a worker thread for its duration (~30-60s); a small bounded pool caps
 * concurrency (single-instance pilot), and the MVC async timeout is lifted just
 * above the per-emitter timeout.
 */
@Configuration
public class OscarWebConfig implements WebMvcConfigurer {

    /** Per-emitter timeout; the client's fetch reader ends when the stream closes. */
    public static final long SSE_TIMEOUT_MS = 120_000L;

    @Bean(name = "oscarStreamExecutor")
    ThreadPoolTaskExecutor oscarStreamExecutor() {
        ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
        ex.setCorePoolSize(2);
        ex.setMaxPoolSize(4);
        ex.setQueueCapacity(8);
        ex.setThreadNamePrefix("oscar-sse-");
        ex.setRejectedExecutionHandler((r, e) -> {
            throw new org.springframework.core.task.TaskRejectedException("OSCAR is busy — try again in a moment.");
        });
        ex.initialize();
        return ex;
    }

    @Override
    public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
        configurer.setDefaultTimeout(SSE_TIMEOUT_MS + 5_000L);
        configurer.setTaskExecutor(oscarStreamExecutor());
    }
}
