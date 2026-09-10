package com.orbit.platform.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orbit.platform.ai.provider.ChatItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Loads the versioned OSCAR system prompt ({@code oscar-v2}) and assembles a
 * turn's opening history — prior turns plus the {@code <orbit_context>} bundle
 * and the user's question. Context-build failure is caught, not fatal
 * (PRD §10 — OSCAR down never breaks a request). Shared by the blocking and
 * streaming orchestrators.
 */
@Component
class OscarPrompt {

    static final String VERSION = "oscar-v2";
    private static final Logger log = LoggerFactory.getLogger(OscarPrompt.class);

    private final ContextBuilder contextBuilder;
    private final ObjectMapper json;
    private final String systemPrompt;

    OscarPrompt(ContextBuilder contextBuilder, ObjectMapper json,
                @Value("classpath:oscar/system-prompt-v2.md") Resource promptResource) {
        this.contextBuilder = contextBuilder;
        this.json = json;
        try {
            this.systemPrompt = new String(promptResource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("OSCAR system prompt resource missing: " + promptResource, e);
        }
    }

    String systemPrompt() {
        return systemPrompt;
    }

    record Opening(List<ChatItem> history, List<String> citationLayers) {
    }

    Opening open(AiTurn turn) {
        ContextBundle context;
        try {
            context = contextBuilder.forTurn(turn.userId(), turn.userMessage());
        } catch (RuntimeException e) {
            log.warn("event=oscar.context.failed user_id={} error={}", turn.userId(), e.toString());
            context = new ContextBundle(Map.of("status", "context-unavailable"), List.of());
        }
        List<ChatItem> history = new ArrayList<>(turn.priorTurns());
        history.add(new ChatItem.UserText(
                context.render(json) + "\n\n---\n\nUser question:\n" + turn.userMessage()));
        return new Opening(history, context.layerNames());
    }
}
