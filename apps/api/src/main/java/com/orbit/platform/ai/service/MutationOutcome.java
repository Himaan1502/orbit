package com.orbit.platform.ai.service;

/**
 * The result of turning a mutation-tool call into something the user can act on
 * (ADR-032 §4). Either a {@link Proposed} action (validated, ownership-checked,
 * ready for the confirm button) or a {@link Rejected} one (bad args / not the
 * caller's resource) — which is fed back to the model so it can tell the user
 * what's missing. Neither branch writes anything.
 */
public sealed interface MutationOutcome {

    record Proposed(ProposedAction action) implements MutationOutcome { }

    record Rejected(String tool, String reason) implements MutationOutcome { }
}
