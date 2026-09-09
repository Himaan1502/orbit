package com.orbit.platform.ai.service;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * The structured OSCAR contract (UX §25.2 / §25.3, PRD §10, ADR-032 §6).
 * {@code kind}, {@code message}, {@code sections} and {@code citations} come from
 * the model (via {@link ResponseValidator}); {@code proposedActions} are attached
 * by the backend from the turn's mutation-tool calls (ADR-032 §4). A response
 * that does not parse degrades to {@code kind="answer"} with a safe message and
 * is not persisted.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OscarResponse(
        Kind kind,
        String message,
        Sections sections,
        List<ProposedAction> proposedActions,
        List<String> citations,
        Meta meta) {

    public enum Kind { answer, recommendation, unavailable }

    /** Recommendation body — UX §25.2 (Action / Why this? / Impact / Evidence). */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Sections(String action, String why, String impact, List<EvidenceRef> evidence) {
    }

    /** One supporting datum, tagged by how it was arrived at (UX §25.3). */
    public record EvidenceRef(String label, EvidenceKind kind, String value) {
    }

    public enum EvidenceKind { fact, metric, inference }

    /** Non-content metadata surfaced to the UI (not from the model). */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Meta(String provider, String model, String promptVersion, String outcome, boolean degraded) {
    }

    public static OscarResponse unavailable(String reason) {
        return new OscarResponse(Kind.unavailable,
                "OSCAR is temporarily unavailable. Your tasks, objectives, roadmaps and analytics remain available.",
                null, null, null,
                new Meta(null, null, null, reason, true));
    }

    public static OscarResponse safeFallback() {
        return new OscarResponse(Kind.answer,
                "I couldn't put that together — try rephrasing the question.",
                null, null, null, null);
    }

    public OscarResponse withActions(List<ProposedAction> actions) {
        return new OscarResponse(kind, message, sections,
                actions == null || actions.isEmpty() ? null : actions, citations, meta);
    }

    public OscarResponse withMeta(Meta m) {
        return new OscarResponse(kind, message, sections, proposedActions, citations, m);
    }
}
