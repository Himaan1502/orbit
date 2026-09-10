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
import java.util.Locale;
import java.util.Optional;

/**
 * Parses an OSCAR model reply in the {@code oscar-v2} format (ADR-033 §2):
 *
 * <pre>
 *   &lt;the answer as plain prose&gt;
 *   ===OSCAR-META===
 *   {"kind":"answer"|"recommendation","sections":{…},"citations":[…]}
 * </pre>
 *
 * The prose is the assistant message; the block after the sentinel carries the
 * UX §25.2 {@code sections} + citations and is optional. <b>Prose that arrived
 * is always real</b> — {@link #parse} returns {@link Optional#empty()} (→ safe
 * fallback, unpersisted, Blueprint §13) only when there is no usable prose at
 * all. A pure-JSON reply (a model that ignored "prose first") is still handled.
 */
@Component
public class ResponseValidator {

    public static final String SENTINEL = "===OSCAR-META===";
    private static final Logger log = LoggerFactory.getLogger(ResponseValidator.class);
    private static final int MAX_MESSAGE = 8000;

    private final ObjectMapper json;

    public ResponseValidator(ObjectMapper json) {
        this.json = json;
    }

    /** The prose portion of a reply (before the sentinel) — what the streaming path streams as tokens. */
    public record Split(String prose, String metaJson) {
    }

    /** The structured extras from the block after the sentinel. */
    public record Meta(Kind kind, Sections sections, List<String> citations) {
    }

    /** Split a full reply on the {@code ===OSCAR-META===} sentinel. */
    public Split split(String fullText) {
        if (fullText == null) {
            return new Split("", null);
        }
        int i = fullText.indexOf(SENTINEL);
        if (i < 0) {
            return new Split(fullText.strip(), null);
        }
        return new Split(fullText.substring(0, i).strip(),
                fullText.substring(i + SENTINEL.length()).strip());
    }

    /** Full-reply parse — used by the non-streaming path. */
    public Optional<OscarResponse> parse(String rawModelText) {
        if (rawModelText == null || rawModelText.isBlank()) {
            return Optional.empty();
        }
        Split s = split(rawModelText);
        String prose = s.prose();

        // a model that ignored "prose first" and emitted only a JSON object
        if ((prose.isBlank() || prose.startsWith("{")) && s.metaJson() == null) {
            Optional<OscarResponse> legacy = parseJsonOnly(prose.isBlank() ? rawModelText : prose);
            if (legacy.isPresent()) {
                return legacy;
            }
        }
        if (prose.isBlank()) {
            log.warn("event=oscar.response.no_prose len={}", rawModelText.length());
            return Optional.empty();
        }
        if (prose.length() > MAX_MESSAGE) {
            prose = prose.substring(0, MAX_MESSAGE);
        }

        Meta meta = parseMeta(s.metaJson()).orElse(new Meta(Kind.answer, null, null));
        Kind kind = meta.sections() != null ? Kind.recommendation
                : (meta.kind() != null ? meta.kind() : Kind.answer);
        return Optional.of(new OscarResponse(kind, prose, meta.sections(), null,
                meta.citations() == null || meta.citations().isEmpty() ? null : meta.citations(), null));
    }

    /** Parse just the block after the sentinel — used by the streaming path (prose already streamed). */
    public Optional<Meta> parseMeta(String metaJson) {
        if (metaJson == null || metaJson.isBlank()) {
            return Optional.empty();
        }
        JsonNode root = extractJsonObject(metaJson);
        if (root == null || !root.isObject()) {
            log.warn("event=oscar.meta.unparsable len={}", metaJson.length());
            return Optional.empty();
        }
        Kind kind = parseKind(text(root, "kind"), root.has("sections"));
        Sections sections = parseSections(root.path("sections"));
        List<String> citations = parseStringList(root.path("citations"));
        return Optional.of(new Meta(kind, sections, citations));
    }

    // ----- legacy pure-JSON reply (backward compatible) -------------------

    private Optional<OscarResponse> parseJsonOnly(String raw) {
        JsonNode root = extractJsonObject(raw);
        if (root == null || !root.isObject()) {
            return Optional.empty();
        }
        String message = firstNonBlank(text(root, "message"), text(root, "answer"), text(root, "text"));
        if (message == null || message.isBlank()) {
            return Optional.empty();
        }
        if (message.length() > MAX_MESSAGE) {
            message = message.substring(0, MAX_MESSAGE);
        }
        Sections sections = parseSections(root.path("sections"));
        Kind kind = sections != null ? Kind.recommendation
                : parseKind(text(root, "kind"), false);
        List<String> citations = parseStringList(root.path("citations"));
        return Optional.of(new OscarResponse(kind, message.strip(), sections, null,
                citations.isEmpty() ? null : citations, null));
    }

    // ----- helpers ------------------------------------------------------

    private Kind parseKind(String raw, boolean hasSections) {
        if (raw != null) {
            try {
                return Kind.valueOf(raw.trim().toLowerCase(Locale.ROOT));
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
            if (label != null || value != null) {
                evidence.add(new EvidenceRef(label == null ? "" : label,
                        parseEvidenceKind(text(e, "kind")), value == null ? "" : value));
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
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
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

    /** First balanced {@code { … }} block, tolerating ```json fences and stray prose. */
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
            } else if (c == '}' && --depth == 0) {
                try {
                    return json.readTree(s.substring(start, i + 1));
                } catch (com.fasterxml.jackson.core.JacksonException e) {
                    return null;
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
