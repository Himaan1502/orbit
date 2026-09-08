package com.orbit.platform.roadmaps.progress;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Tunables for derived roadmap progress and the intersection view (ADR-023 §4).
 * Starting values; tune once the roadmaps have been lived with. All are
 * fractions in {@code [0,1]} and validated at startup ({@link RoadmapsConfig}).
 */
@ConfigurationProperties(prefix = "orbit.roadmaps")
public record RoadmapProperties(
        /** Weighted capability at/above which a topic reads as "covered". */
        @DefaultValue("0.70") double topicCoveredThreshold,
        /** Career-target importance at/above which intersection relevance reads HIGH. */
        @DefaultValue("0.66") double careerRelevanceHigh,
        /** …and MEDIUM (below this is LOW). */
        @DefaultValue("0.40") double careerRelevanceMedium,
        /** recommendedAction band edges (ADR-023 §3): [start, build, operate). */
        @DefaultValue("0.30") double actionBandBuild,
        @DefaultValue("0.60") double actionBandOperate,
        @DefaultValue("0.85") double actionBandMaintain) {

    public void validate() {
        checkFraction("topicCoveredThreshold", topicCoveredThreshold);
        checkFraction("careerRelevanceHigh", careerRelevanceHigh);
        checkFraction("careerRelevanceMedium", careerRelevanceMedium);
        checkFraction("actionBandBuild", actionBandBuild);
        checkFraction("actionBandOperate", actionBandOperate);
        checkFraction("actionBandMaintain", actionBandMaintain);
        if (!(actionBandBuild < actionBandOperate && actionBandOperate < actionBandMaintain)) {
            throw new IllegalStateException(
                    "orbit.roadmaps action bands must be strictly increasing: "
                            + actionBandBuild + " < " + actionBandOperate + " < " + actionBandMaintain);
        }
        if (careerRelevanceMedium >= careerRelevanceHigh) {
            throw new IllegalStateException(
                    "orbit.roadmaps.career-relevance-medium must be below career-relevance-high");
        }
    }

    private static void checkFraction(String name, double v) {
        if (v < 0 || v > 1) {
            throw new IllegalStateException("orbit.roadmaps." + name + " must be within [0,1] but was " + v);
        }
    }
}
