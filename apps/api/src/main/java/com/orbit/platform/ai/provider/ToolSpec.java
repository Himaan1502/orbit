package com.orbit.platform.ai.provider;

/**
 * A tool declaration handed to the provider (Blueprint §27). {@code parametersJson}
 * is a JSON-Schema object ({@code {"type":"object","properties":{…},"required":[…]}}) —
 * both Gemini ({@code functionDeclarations[].parameters}) and the OpenAI-compatible
 * shape ({@code tools[].function.parameters}) take it verbatim.
 *
 * <p>{@link Kind#READ} tools are dispatched to domain services by the backend and
 * their result fed back to the model. {@link Kind#MUTATION} tools are declared so
 * the model can express intent, but are <b>never executed</b> — a mutation tool
 * call becomes a {@code ProposedAction} the user confirms (ADR-032 §3/§4).
 */
public record ToolSpec(String name, String description, String parametersJson, Kind kind) {

    public enum Kind { READ, MUTATION }

    public boolean isMutation() {
        return kind == Kind.MUTATION;
    }
}
