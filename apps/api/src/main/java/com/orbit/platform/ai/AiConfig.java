package com.orbit.platform.ai;

import com.orbit.platform.ai.provider.AiProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Wiring for the {@code ai} domain / OSCAR (ADR-031, ADR-032). Validates the
 * tunables at startup and logs the feature-flag state — <b>without ever logging
 * a key</b> (ADR-031 §4).
 */
@Configuration
@EnableConfigurationProperties(AiProperties.class)
public class AiConfig {

    private static final Logger log = LoggerFactory.getLogger(AiConfig.class);

    @Bean
    ApplicationRunner aiStartupCheck(AiProperties props, List<AiProvider> providers) {
        return args -> {
            props.validate();
            String provStatus = providers.stream()
                    .map(p -> p.name() + "=" + (p.isConfigured() ? "configured" : "no-key"))
                    .collect(Collectors.joining(", "));
            if (props.anyProviderConfigured()) {
                log.info("OSCAR enabled — primary={}, fallback={}, providers[{}], "
                                + "dailyRequestCap={}, promptTokenBudget={}, maxToolRounds={}",
                        props.primary(), props.fallback(), provStatus,
                        props.dailyRequestCap(), props.promptTokenBudget(), props.maxToolRounds());
            } else {
                log.info("OSCAR disabled — no GEMINI_API_KEY / GROQ_API_KEY present. "
                        + "/oscar will show the unavailable state; the rest of ORBIT is unaffected.");
            }
        };
    }
}
