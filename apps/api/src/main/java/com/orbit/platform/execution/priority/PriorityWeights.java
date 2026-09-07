package com.orbit.platform.execution.priority;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The eight Priority Engine weights (Blueprint §22, ADR-014). Bound from
 * {@code orbit.priority.weights.*}; defaults are Blueprint §22's exact values.
 * The context fails to start if they do not sum to 1.
 */
@ConfigurationProperties(prefix = "orbit.priority.weights")
public class PriorityWeights {

    private static final Logger log = LoggerFactory.getLogger(PriorityWeights.class);

    private double careerRelevance = 0.20;
    private double skillGap = 0.20;
    private double dependencyImpact = 0.15;
    private double projectImpact = 0.15;
    private double interviewValue = 0.10;
    private double urgency = 0.10;
    private double availableTimeFit = 0.05;
    private double historicalCompletionProbability = 0.05;

    @PostConstruct
    void validate() {
        double sum = careerRelevance + skillGap + dependencyImpact + projectImpact
                + interviewValue + urgency + availableTimeFit + historicalCompletionProbability;
        if (Math.abs(sum - 1.0) > 0.001) {
            throw new IllegalStateException(
                    "orbit.priority.weights must sum to 1.0 (got " + sum + ")");
        }
        log.info("Priority Engine weights loaded (fingerprint={}, sum={})", fingerprint(), sum);
    }

    /** Short, stable hash of the ordered weights — stored in every priority_reason. */
    public String fingerprint() {
        long bits = 17;
        for (double w : new double[] {careerRelevance, skillGap, dependencyImpact, projectImpact,
                interviewValue, urgency, availableTimeFit, historicalCompletionProbability}) {
            bits = bits * 31 + Double.doubleToLongBits(Math.round(w * 10_000) / 10_000.0);
        }
        return Long.toHexString(bits & 0xFFFFFFFFL);
    }

    public double getCareerRelevance() {
        return careerRelevance;
    }

    public void setCareerRelevance(double v) {
        this.careerRelevance = v;
    }

    public double getSkillGap() {
        return skillGap;
    }

    public void setSkillGap(double v) {
        this.skillGap = v;
    }

    public double getDependencyImpact() {
        return dependencyImpact;
    }

    public void setDependencyImpact(double v) {
        this.dependencyImpact = v;
    }

    public double getProjectImpact() {
        return projectImpact;
    }

    public void setProjectImpact(double v) {
        this.projectImpact = v;
    }

    public double getInterviewValue() {
        return interviewValue;
    }

    public void setInterviewValue(double v) {
        this.interviewValue = v;
    }

    public double getUrgency() {
        return urgency;
    }

    public void setUrgency(double v) {
        this.urgency = v;
    }

    public double getAvailableTimeFit() {
        return availableTimeFit;
    }

    public void setAvailableTimeFit(double v) {
        this.availableTimeFit = v;
    }

    public double getHistoricalCompletionProbability() {
        return historicalCompletionProbability;
    }

    public void setHistoricalCompletionProbability(double v) {
        this.historicalCompletionProbability = v;
    }
}
