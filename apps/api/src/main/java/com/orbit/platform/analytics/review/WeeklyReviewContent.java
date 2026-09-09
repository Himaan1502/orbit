package com.orbit.platform.analytics.review;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The six PRD §8 sections of a weekly review, as <em>structured data</em>
 * (ADR-028 §1). Persisted as the {@code weekly_reviews.content} JSONB. Stable
 * keys — OSCAR (M7+) enriches the prose over this same shape.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record WeeklyReviewContent(
        Happened happened,
        List<Improvement> improved,
        List<MissedTask> missed,
        List<Attribution> why,
        List<Change> changed,
        List<NextAction> next) {

    public static WeeklyReviewContent empty() {
        return new WeeklyReviewContent(
                new Happened(0, 0, 0, 0, 0), List.of(), List.of(), List.of(), List.of(), List.of());
    }

    /** "What happened" — the week's counts. */
    public record Happened(int tasksCompleted, int tasksPlanned, int evidenceLogged,
                           int projectsAdvanced, int milestonesHit) {
    }

    /** "What improved" — a positive delta the user's own actions produced. */
    public record Improvement(String kind, String label, double delta, UUID skillId) {
    }

    /** "What was missed" — planned or due, still not done. */
    public record MissedTask(UUID taskId, String title, String objectiveTitle, Instant dueAt) {
    }

    /** "Why" — mechanical attribution for an improvement (no inference). */
    public record Attribution(String claim, String basis) {
    }

    /** "What changed" — a status transition / new adoption in the week. */
    public record Change(String kind, String label, String from, String to) {
    }

    /** "What to prioritize next" — the top of the current Priority Engine ordering. */
    public record NextAction(UUID taskId, String title, Double priorityScore, String reason) {
    }
}
