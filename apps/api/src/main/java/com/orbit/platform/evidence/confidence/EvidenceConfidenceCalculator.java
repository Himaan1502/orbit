package com.orbit.platform.evidence.confidence;

import com.orbit.platform.evidence.model.EvidenceType;
import com.orbit.platform.skills.scoring.SkillScore.Confidence;

import java.util.Collection;
import java.util.EnumSet;
import java.util.Set;

/**
 * Pure — turns the evidence linked to a skill into an assessment
 * {@code confidence} level (ADR-026 §1). Evidence raises <em>confidence</em>,
 * never a dimension score. No I/O; unit-tested directly.
 */
public final class EvidenceConfidenceCalculator {

    private EvidenceConfidenceCalculator() {
    }

    /** One {@code skill_evidence} link, reduced to what the confidence rule needs. */
    public record Link(double weight, EvidenceType type) {
    }

    /** Summed contribution weight of the given links, capped at 1.0. */
    public static double strength(Collection<Link> links) {
        double s = links.stream().mapToDouble(Link::weight).sum();
        return Math.min(1.0, Math.round(s * 1000) / 1000.0);
    }

    public static Confidence confidence(Collection<Link> links, EvidenceProperties props) {
        double strength = strength(links);
        Set<EvidenceType> types = links.isEmpty() ? Set.of()
                : EnumSet.copyOf(links.stream().map(Link::type).toList());

        if (strength >= props.verifiedThreshold() && types.size() >= props.verifiedMinTypes()) {
            return Confidence.VERIFIED;
        }
        if (strength >= props.partialThreshold()) {
            return Confidence.PARTIALLY_VERIFIED;
        }
        return Confidence.SELF_REPORTED;
    }

    /** Convenience: the {@code String} value ({@code self-reported} / …). */
    public static String confidenceValue(Collection<Link> links, EvidenceProperties props) {
        return confidence(links, props).value();
    }
}
