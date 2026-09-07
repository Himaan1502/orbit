package com.orbit.platform.skills.scoring;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The five Skill Scoring weights (Blueprint §23, ADR-017). Bound from
 * {@code orbit.skills.scoring.weights.*}; defaults are Blueprint §23's exact
 * values. The context fails to start if they do not sum to 1.
 */
@ConfigurationProperties(prefix = "orbit.skills.scoring.weights")
public class SkillScoringWeights {

    private static final Logger log = LoggerFactory.getLogger(SkillScoringWeights.class);

    private double knowledge = 0.20;
    private double practical = 0.25;
    private double evidence = 0.20;
    private double interview = 0.15;
    private double production = 0.20;

    @PostConstruct
    void validate() {
        double sum = knowledge + practical + evidence + interview + production;
        if (Math.abs(sum - 1.0) > 0.001) {
            throw new IllegalStateException(
                    "orbit.skills.scoring.weights must sum to 1.0 (got " + sum + ")");
        }
        log.info("Skill Scoring weights loaded (fingerprint={}, sum={})", fingerprint(), sum);
    }

    public String fingerprint() {
        long bits = 17;
        for (double w : new double[] {knowledge, practical, evidence, interview, production}) {
            bits = bits * 31 + Double.doubleToLongBits(Math.round(w * 10_000) / 10_000.0);
        }
        return Long.toHexString(bits & 0xFFFFFFFFL);
    }

    public double getKnowledge() {
        return knowledge;
    }

    public void setKnowledge(double v) {
        this.knowledge = v;
    }

    public double getPractical() {
        return practical;
    }

    public void setPractical(double v) {
        this.practical = v;
    }

    public double getEvidence() {
        return evidence;
    }

    public void setEvidence(double v) {
        this.evidence = v;
    }

    public double getInterview() {
        return interview;
    }

    public void setInterview(double v) {
        this.interview = v;
    }

    public double getProduction() {
        return production;
    }

    public void setProduction(double v) {
        this.production = v;
    }
}
