package com.orbit.platform.ai.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Adapts {@link StreamSink} events to SSE frames on an {@link SseEmitter}
 * (ADR-033 §4). If a {@code send} fails (the client disconnected) the sink goes
 * dead and further events are dropped — the orchestrator finishes its bounded
 * work and the turn is simply not persisted.
 */
public final class SseStreamSink implements StreamSink {

    private static final Logger log = LoggerFactory.getLogger(SseStreamSink.class);

    private final SseEmitter emitter;
    private volatile boolean dead;

    public SseStreamSink(SseEmitter emitter) {
        this.emitter = emitter;
    }

    public boolean isDead() {
        return dead;
    }

    @Override
    public void status(String text) {
        send("status", Map.of("text", text));
    }

    @Override
    public void token(String delta) {
        send("token", Map.of("text", delta));
    }

    @Override
    public void proposedAction(ProposedAction action) {
        send("proposedAction", action);
    }

    @Override
    public void meta(OscarResponse response) {
        send("meta", response);
    }

    @Override
    public void error(boolean recoverable, String note) {
        send("error", Map.of("recoverable", recoverable, "note", note));
    }

    /** The terminal frame — written by {@code ConversationService} after it persists. */
    public void done(boolean persisted, String messageId) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("persisted", persisted);
        m.put("messageId", messageId == null ? "" : messageId);
        send("done", m);
    }

    private void send(String event, Object data) {
        if (dead) {
            return;
        }
        try {
            emitter.send(SseEmitter.event().name(event).data(data, MediaType.APPLICATION_JSON));
        } catch (RuntimeException | java.io.IOException e) {
            dead = true;
            log.debug("event=oscar.sse.client_gone event_dropped={}", event);
        }
    }
}
