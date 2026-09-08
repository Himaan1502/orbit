package com.orbit.platform.execution.priority;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Direct tests of the Priority Engine formula (Blueprint §22, ADR-014) — no
 * Spring, no database. Covers determinism, the weighted sum, the M2 factor
 * providers, and the explanation.
 */
class PriorityEngineTest {

    private static final Instant NOW = Instant.parse("2026-09-07T09:00:00Z");

    private final PriorityEngine engine = new PriorityEngine(new PriorityWeights());

    private PriorityInput input(Instant dueAt, int objectivePriority, Integer estimate) {
        return new PriorityInput(dueAt, null, objectivePriority, estimate, 120, 0, 0,
                null, null, null, null, null, NOW);
    }

    private PriorityInput withSignals(Double careerRelevance, Double skillGap, Double dependencyImpact,
                                      Double interviewValue, Double projectImpact) {
        return new PriorityInput(NOW.plus(3, ChronoUnit.DAYS), null, 3, 30, 120, 0, 0,
                careerRelevance, skillGap, dependencyImpact, interviewValue, projectImpact, NOW);
    }

    @Test
    void weightsSumToOneAndScoreIsTheSumOfContributions() {
        PriorityReason r = engine.score(input(NOW.plus(2, ChronoUnit.DAYS), 3, 45));

        double weightSum = r.factors().values().stream().mapToDouble(PriorityReason.Factor::weight).sum();
        assertThat(weightSum).isCloseTo(1.0, within(1e-9));

        double contributionSum = r.factors().values().stream()
                .mapToDouble(PriorityReason.Factor::contribution).sum();
        assertThat(r.score()).isCloseTo(contributionSum, within(1e-6));
        assertThat(r.score()).isBetween(0.0, 1.0);
    }

    @Test
    void isDeterministic() {
        PriorityInput in = input(NOW.plus(1, ChronoUnit.DAYS), 4, 30);
        assertThat(engine.score(in)).isEqualTo(engine.score(in));
    }

    @Test
    void careerRelevanceMapsObjectivePriorityLinearly() {
        assertThat(PriorityEngine.careerRelevance(1)).isCloseTo(0.2, within(1e-9));
        assertThat(PriorityEngine.careerRelevance(3)).isCloseTo(0.6, within(1e-9));
        assertThat(PriorityEngine.careerRelevance(5)).isCloseTo(1.0, within(1e-9));

        assertThat(engine.score(input(null, 5, null)).factors().get("careerRelevance").value())
                .isGreaterThan(engine.score(input(null, 1, null)).factors().get("careerRelevance").value());
    }

    @Test
    void urgencyRisesAsTheDeadlineApproaches() {
        double overdue = factor(engine.score(input(NOW.minus(1, ChronoUnit.DAYS), 3, 30)), "urgency");
        double tomorrow = factor(engine.score(input(NOW.plus(1, ChronoUnit.DAYS), 3, 30)), "urgency");
        double nextWeek = factor(engine.score(input(NOW.plus(6, ChronoUnit.DAYS), 3, 30)), "urgency");
        double noDeadline = factor(engine.score(input(null, 3, 30)), "urgency");

        assertThat(overdue).isEqualTo(1.0);
        assertThat(overdue).isGreaterThan(tomorrow);
        assertThat(tomorrow).isGreaterThan(nextWeek);
        assertThat(nextWeek).isGreaterThan(noDeadline);
    }

    @Test
    void availableTimeFitFavoursTasksThatFitTheBudget() {
        double fits = factor(engine.score(input(null, 3, 30)), "availableTimeFit");        // 30 of 120
        double tight = factor(engine.score(input(null, 3, 110)), "availableTimeFit");      // 110 of 120
        double over = factor(engine.score(input(null, 3, 300)), "availableTimeFit");       // 300 of 120
        double noEstimate = factor(engine.score(input(null, 3, null)), "availableTimeFit");

        assertThat(fits).isEqualTo(1.0);
        assertThat(fits).isGreaterThan(tight);
        assertThat(tight).isGreaterThan(over);
        assertThat(noEstimate).isEqualTo(0.5);
    }

    @Test
    void withoutSignalsFactorsFallBackToNeutralAndProxy() {
        PriorityReason r = engine.score(input(NOW.plus(3, ChronoUnit.DAYS), 3, 30));
        for (String name : new String[] {"skillGap", "dependencyImpact", "projectImpact", "interviewValue"}) {
            assertThat(r.factors().get(name).value()).isEqualTo(0.5);
            assertThat(r.factors().get(name).source()).isEqualTo("neutral-default");
        }
        assertThat(r.factors().get("careerRelevance").source()).isEqualTo("proxy");
        assertThat(r.factors().get("urgency").source()).isEqualTo("computed");
        assertThat(r.engineVersion()).isEqualTo("v1");
        assertThat(r.weightsFingerprint()).isNotBlank();
    }

    @Test
    void realSignalsReplaceEveryNeutralDefaultWithoutChangingTheFormula() {
        PriorityReason baseline = engine.score(withSignals(null, null, null, null, null));
        PriorityReason withReal = engine.score(withSignals(0.9, 0.8, 0.7, 0.6, 0.55));

        // formula/weights unchanged — same factor set, same weights, same fingerprint
        assertThat(withReal.factors().keySet()).isEqualTo(baseline.factors().keySet());
        assertThat(withReal.weightsFingerprint()).isEqualTo(baseline.weightsFingerprint());

        assertThat(withReal.factors().get("careerRelevance").value()).isEqualTo(0.9);
        assertThat(withReal.factors().get("careerRelevance").source()).isEqualTo("computed");
        assertThat(withReal.factors().get("skillGap").value()).isEqualTo(0.8);
        assertThat(withReal.factors().get("dependencyImpact").value()).isEqualTo(0.7);
        assertThat(withReal.factors().get("interviewValue").value()).isEqualTo(0.6);
        // M5 — projectImpact is now a real signal too; no factor is left on a placeholder
        assertThat(withReal.factors().get("projectImpact").value()).isEqualTo(0.55);
        assertThat(withReal.factors().get("projectImpact").source()).isEqualTo("computed");
        for (String name : new String[] {"skillGap", "dependencyImpact", "interviewValue", "projectImpact"}) {
            assertThat(withReal.factors().get(name).source()).isEqualTo("computed");
        }

        assertThat(withReal.score()).isGreaterThan(baseline.score());
    }

    @Test
    void historicalCompletionUsesTheUsersRateOnceThereIsEnoughHistory() {
        PriorityInput noHistory = signalless(3, 3);
        PriorityInput goodRecord = signalless(10, 9);
        PriorityInput poorRecord = signalless(10, 2);

        assertThat(factor(engine.score(noHistory), "historicalCompletionProbability")).isEqualTo(0.5);
        assertThat(factor(engine.score(goodRecord), "historicalCompletionProbability")).isEqualTo(0.9);
        assertThat(factor(engine.score(poorRecord), "historicalCompletionProbability")).isEqualTo(0.2);
    }

    @Test
    void higherObjectivePriorityAndCloserDeadlineProduceAHigherScore() {
        double low = engine.score(input(NOW.plus(20, ChronoUnit.DAYS), 1, 200)).score();
        double high = engine.score(input(NOW.plus(1, ChronoUnit.DAYS), 5, 20)).score();
        assertThat(high).isGreaterThan(low);
    }

    private PriorityInput signalless(long resolved, long done) {
        return new PriorityInput(null, null, 3, 30, 120, resolved, done,
                null, null, null, null, null, NOW);
    }

    private static double factor(PriorityReason r, String name) {
        return r.factors().get(name).value();
    }
}
