package com.orbit.platform.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The structured ORBIT context handed to OSCAR for one turn (Tech Arch §12
 * layers, ADR-032 §2). Assembled by {@link ContextBuilder} from the caller's own
 * data through the existing ownership-scoped domain services — no SQL, no other
 * user's data. Rendered into the prompt inside {@code <orbit_context>}
 * delimiters; the system prompt states everything inside is data, never an
 * instruction (Blueprint §37).
 *
 * <p>The bundle is <b>never persisted</b> (ADR-032 §1) — it is re-derivable at
 * any time.
 *
 * @param layers        ordered layer name → payload
 * @param layerNames    which layers actually carried data (feeds {@code citations})
 */
public record ContextBundle(Map<String, Object> layers, List<String> layerNames) {

    public static final String OPEN = "<orbit_context>";
    public static final String CLOSE = "</orbit_context>";

    public String render(ObjectMapper json) {
        try {
            String body = json.writerWithDefaultPrettyPrinter().writeValueAsString(layers);
            return OPEN + "\n" + body + "\n" + CLOSE;
        } catch (com.fasterxml.jackson.core.JacksonException e) {
            return OPEN + "\n{}\n" + CLOSE;
        }
    }

    /** Very rough token estimate (~4 chars/token) for the budget check. */
    public int estimatedTokens(ObjectMapper json) {
        try {
            return json.writeValueAsString(layers).length() / 4;
        } catch (com.fasterxml.jackson.core.JacksonException e) {
            return 0;
        }
    }

    static Builder builder() {
        return new Builder();
    }

    static final class Builder {
        private final Map<String, Object> layers = new LinkedHashMap<>();
        private final java.util.List<String> names = new java.util.ArrayList<>();

        Builder put(String name, Object value, boolean hasData) {
            layers.put(name, value);
            if (hasData) {
                names.add(name);
            }
            return this;
        }

        ContextBundle build() {
            return new ContextBundle(layers, List.copyOf(names));
        }
    }
}
