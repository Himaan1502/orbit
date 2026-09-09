package com.orbit.platform.ai.service;

import java.util.Map;

/**
 * A state change OSCAR <b>proposes</b> — never executes (ADR-032 §3/§4, PRD §10,
 * UX §25.4). Built by the backend from a mutation-tool call the model emitted;
 * the args are validated against the target endpoint's request schema first.
 *
 * <p>The frontend renders this as a UX §25.2 card ({@code [ Start ] [ Explain ]
 * [ Dismiss ]}); <b>"Start" calls {@code call} directly</b> — the existing ORBIT
 * endpoint, which already enforces ownership, validation and re-scoring. OSCAR
 * holds zero mutation capability at any point.
 *
 * @param tool        the mutation tool name (e.g. {@code create_task})
 * @param description human-readable summary of what would happen
 * @param call        the exact existing-API call the "Start" button makes
 * @param reasoning   why OSCAR is proposing it (cites context)
 */
public record ProposedAction(String tool, String description, ApiCall call, String reasoning) {

    /** A concrete call against the existing {@code /api/v1} surface. */
    public record ApiCall(String method, String path, Map<String, Object> body) {
    }
}
