package com.orbit.platform.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orbit.platform.ai.service.OscarResponse.EvidenceKind;
import com.orbit.platform.ai.service.OscarResponse.EvidenceRef;
import com.orbit.platform.ai.service.OscarResponse.Kind;
import com.orbit.platform.ai.service.OscarResponse.Sections;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Parses a raw model reply into an {@link OscarResponse} (ADR-032 §6, Blueprint
 * §13 "never treat model output as trusted executable code"). The model is
 * asked to return a JSON object matching the schema; this tolerates markdown
 * fences and leading/trailing prose. Anything that cannot be made into a
 * well-formed response with a non-blank message returns
 * {@link Optional#empty()} — the caller then uses {@link OscarResponse#safeFallback()}
 * and does <b>not</b> persist the turn.
 */
@Component
public class ResponseValidator {

    private static final Logger log = LoggerFactory.getLogger(ResponseValidator.class);
    private static final int MAX_MESSAGE = 8000;

    private final ObjectMapper json;

    public ResponseValidator(ObjectMapper json) {
        this.json = json;
    }

    public Optional<OscarResponse> parse(String rawModelText) {
        if (rawModelText == null || rawModelText.isBlank()) {
            return Optional.empty();
        }
        JsonNode root = extractJsonObject(rawModelText);
        if (root == null || !root.isObject()) {
            log.warn("event=oscar.response.unparsable len={}", rawModelText.length());
            return Optional.empty();
        }

        String message = text(root, "message");
        if (message == null || message.isBlank()) {
            // some models put the prose in "answer" or "text"
            message = firstNonBlank(text(root, "answer"), text(root, "text"), text(root, "response"));
        }
        if (message == null || message.isBlank()) {
            return Optional.empty();
        }
        if (message.length() > MAX_MESSAGE) {
            message = message.substring(0, MAX_MESSAGE);
        }

        Kind kind = parseKind(text(root, "kind"), root.has("sections"));
        Sections sections = parseSections(root.path("sections"));
        if (kind == Kind.recommendation && sections == null) {
            kind = Kind.answer;
        }
        List<String> citations = parseStringList(root.path("citations"));

        return Optional.of(new OscarResponse(kind, message.strip(), sections, null,
                citations.isEmpty() ? null : citations, null));
    }

    private Kind parseKind(String raw, boolean hasSections) {
        if (raw != null) {
            try {
                return Kind.valueOf(raw.trim().toLowerCase(java.util.Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                // fall through
            }
        }
        return hasSections ? Kind.recommendation : Kind.answer;
    }

    private Sections parseSections(JsonNode node) {
        if (node == null || !node.isObject()) {
            return null;
        }
        String action = text(node, "action");
        String why = firstNonBlank(text(node, "why"), text(node, "whyThis"), text(node, "reasoning"));
        String impact = text(node, "impact");
        List<EvidenceRef> evidence = new ArrayList<>();
        for (JsonNode e : node.path("evidence")) {
            String label = text(e, "label");
            String value = firstNonBlank(text(e, "value"), text(e, "detail"));
            EvidenceKind ek = parseEvidenceKind(text(e, "kind"));
            if (label != null || value != null) {
                evidence.add(new EvidenceRef(
                        label == null ? "" : label,
                        ek,
                        value == null ? "" : value));
            }
        }
        if (action == null && why == null && impact == null && evidence.isEmpty()) {
            return null;
        }
        return new Sections(action, why, impact, evidence.isEmpty() ? null : evidence);
    }

    private EvidenceKind parseEvidenceKind(String raw) {
        if (raw == null) {
            return EvidenceKind.fact;
        }
        return switch (raw.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "metric", "calculated", "calculated-metric" -> EvidenceKind.metric;
            case "inference", "inferred", "recommendation" -> EvidenceKind.inference;
            default -> EvidenceKind.fact;
        };
    }

    private List<String> parseStringList(JsonNode node) {
        List<String> out = new ArrayList<>();
        if (node != null && node.isArray()) {
            for (JsonNode n : node) {
                String s = n.asText(null);
                if (s != null && !s.isBlank()) {
                    out.add(s.strip());
                }
            }
        }
        return out;
    }

    /** Find the first balanced {@code { … }} block, tolerating ```json fences and stray prose. */
    JsonNode extractJsonObject(String raw) {
        String s = raw.strip();
        int start = s.indexOf('{');
        if (start < 0) {
            return null;
        }
        int depth = 0;
        boolean inString = false;
        boolean escape = false;
        for (int i = start; i < s.length(); i++) {
            char c = s.charAt(i);
            if (inString) {
                if (escape) {
                    escape = false;
                } else if (c == '\\') {
                    escape = true;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            if (c == '"') {
                inString = true;
            } else if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    try {
                        return json.readTree(s.substring(start, i + 1));
                    } catch (com.fasterxml.jackson.core.JacksonException e) {
                        return null;
                    }
                }
            }
        }
        return null;
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.path(field);
        return v.isTextual() && !v.asText().isBlank() ? v.asText() : null;
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return null;
    }
}
