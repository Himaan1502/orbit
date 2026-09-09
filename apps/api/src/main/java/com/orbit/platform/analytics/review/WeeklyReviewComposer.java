package com.orbit.platform.analytics.review;

import com.orbit.platform.analytics.review.WeeklyReviewContent.Attribution;
import com.orbit.platform.analytics.review.WeeklyReviewContent.Change;
import com.orbit.platform.analytics.review.WeeklyReviewContent.Happened;
import com.orbit.platform.analytics.review.WeeklyReviewContent.Improvement;
import com.orbit.platform.analytics.review.WeeklyReviewContent.MissedTask;
import com.orbit.platform.analytics.review.WeeklyReviewContent.NextAction;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure, deterministic composition of a weekly review from that week's analytics
 * aggregates (ADR-028 §2). No LLM, no I/O, no security context. The "why"
 * section is <em>mechanical attribution</em> — it names the events behind a
 * delta, it does not infer causes. OSCAR (M7+) layers narrative over the same
 * {@link WeeklyReviewContent} shape.
 */
public final class WeeklyReviewComposer {

    private WeeklyReviewComposer() {
    }

    /** Everything the composer needs for one week, assembled by the caller. */
    public record WeeklyInputs(
            int tasksCompleted,
            int tasksPlanned,
            int evidenceLogged,
            int projectsAdvanced,
            int milestonesHit,
            List<SkillMove> skillMoves,
            List<ReadinessMove> readinessMoves,
            List<ObjectiveMove> objectiveMoves,
            List<MissedTask> missed,
            List<NextAction> next) {
    }

    public record SkillMove(java.util.UUID skillId, String name, double from, double to) {
    }

    public record ReadinessMove(String targetName, double from, double to) {
    }

    public record ObjectiveMove(String title, double progressFrom, double progressTo,
                                String statusFrom, String statusTo) {
    }

    public record Composed(String summary, WeeklyReviewContent content) {
    }

    public static Composed compose(WeeklyInputs in) {
        Happened happened = new Happened(in.tasksCompleted(), in.tasksPlanned(), in.evidenceLogged(),
                in.projectsAdvanced(), in.milestonesHit());

        List<Improvement> improved = new ArrayList<>();
        List<Attribution> why = new ArrayList<>();

        for (SkillMove m : in.skillMoves()) {
            if (m.to() > m.from() + 0.005) {
                double delta = round(m.to() - m.from());
                improved.add(new Improvement("skill", m.name(), delta, m.skillId()));
                why.add(new Attribution(
                        m.name() + " capability +" + pct(delta),
                        "re-assessed this week (" + pct(m.from()) + " → " + pct(m.to()) + ")"));
            }
        }
        for (ReadinessMove m : in.readinessMoves()) {
            if (m.to() > m.from() + 0.005) {
                double delta = round(m.to() - m.from());
                improved.add(new Improvement("readiness", m.targetName(), delta, null));
                why.add(new Attribution(
                        m.targetName() + " readiness +" + pct(delta),
                        "driven by this week's assessments / evidence"));
            }
        }
        for (ObjectiveMove m : in.objectiveMoves()) {
            if (m.progressTo() > m.progressFrom() + 0.005) {
                double delta = round(m.progressTo() - m.progressFrom());
                improved.add(new Improvement("objective", m.title(), delta, null));
            }
        }

        List<Change> changed = new ArrayList<>();
        for (ObjectiveMove m : in.objectiveMoves()) {
            if (m.statusFrom() != null && !m.statusFrom().equals(m.statusTo())) {
                changed.add(new Change("objective", m.title(), m.statusFrom(), m.statusTo()));
            }
        }

        String summary = summarise(happened, improved);
        return new Composed(summary,
                new WeeklyReviewContent(happened, improved, in.missed(), why, changed, in.next()));
    }

    private static String summarise(Happened h, List<Improvement> improved) {
        StringBuilder s = new StringBuilder();
        if (h.tasksPlanned() > 0) {
            s.append("Completed ").append(h.tasksCompleted()).append(" of ")
                    .append(h.tasksPlanned()).append(" planned tasks");
        } else {
            s.append("Completed ").append(h.tasksCompleted()).append(" task")
                    .append(h.tasksCompleted() == 1 ? "" : "s");
        }
        if (h.evidenceLogged() > 0) {
            s.append("; logged ").append(h.evidenceLogged()).append(" evidence");
        }
        improved.stream()
                .filter(i -> "readiness".equals(i.kind()))
                .findFirst()
                .ifPresent(i -> s.append("; ").append(i.label()).append(" readiness +").append(pct(i.delta())));
        s.append('.');
        return s.toString();
    }

    private static String pct(double v) {
        return Math.round(v * 100) + "%";
    }

    private static double round(double v) {
        return Math.round(v * 1000) / 1000.0;
    }
}
