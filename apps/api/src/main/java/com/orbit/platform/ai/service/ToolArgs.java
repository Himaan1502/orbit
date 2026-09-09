package com.orbit.platform.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.UUID;

/** Small shared helpers for reading + validating tool-call arguments (JSON objects). */
final class ToolArgs {

    private ToolArgs() {
    }

    static JsonNode parse(ObjectMapper json, String argsJson) {
        try {
            JsonNode n = json.readTree(argsJson == null || argsJson.isBlank() ? "{}" : argsJson);
            return n.isObject() ? n : json.createObjectNode();
        } catch (com.fasterxml.jackson.core.JacksonException e) {
            return json.createObjectNode();
        }
    }

    static String text(JsonNode n, String field) {
        JsonNode v = n.path(field);
        return v.isTextual() && !v.asText().isBlank() ? v.asText().strip() : null;
    }

    static String requireText(JsonNode n, String field) {
        String v = text(n, field);
        if (v == null) {
            throw new IllegalArgumentException("'" + field + "' is required");
        }
        return v;
    }

    static UUID requireUuid(JsonNode n, String field) {
        try {
            return UUID.fromString(requireText(n, field));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("'" + field + "' must be a UUID");
        }
    }

    static UUID optionalUuid(JsonNode n, String field) {
        String v = text(n, field);
        if (v == null) {
            return null;
        }
        try {
            return UUID.fromString(v);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    static void putIfText(Map<String, Object> body, String key, JsonNode n, String field) {
        String v = text(n, field);
        if (v != null) {
            body.put(key, v);
        }
    }

    static void putIfInt(Map<String, Object> body, String key, JsonNode n, String field) {
        JsonNode v = n.path(field);
        if (v.isNumber()) {
            body.put(key, v.asInt());
        }
    }
}
