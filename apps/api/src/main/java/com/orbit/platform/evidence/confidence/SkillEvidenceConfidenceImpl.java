package com.orbit.platform.evidence.confidence;

import com.orbit.platform.evidence.confidence.EvidenceConfidenceCalculator.Link;
import com.orbit.platform.evidence.service.SkillEvidenceSignals;
import com.orbit.platform.evidence.service.SkillEvidenceSignals.Entry;
import com.orbit.platform.skills.service.SkillEvidenceConfidence;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * {@code evidence}-side implementation of the {@code skills}-owned
 * {@link SkillEvidenceConfidence} port (Blueprint §5, ADR-026 §1).
 */
@Component
class SkillEvidenceConfidenceImpl implements SkillEvidenceConfidence {

    private final SkillEvidenceSignals signals;
    private final EvidenceProperties properties;

    SkillEvidenceConfidenceImpl(SkillEvidenceSignals signals, EvidenceProperties properties) {
        this.signals = signals;
        this.properties = properties;
    }

    @Override
    @Transactional(readOnly = true)
    public String forSkill(UUID userId, UUID skillId) {
        return EvidenceConfidenceCalculator.confidenceValue(
                links(signals.forUserAndSkill(userId, skillId)), properties);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, String> byUser(UUID userId) {
        return signals.forUser(userId).stream()
                .collect(Collectors.groupingBy(Entry::skillId)).entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey,
                        e -> EvidenceConfidenceCalculator.confidenceValue(links(e.getValue()), properties)));
    }

    private static List<Link> links(List<Entry> entries) {
        return entries.stream().map(e -> new Link(e.weight(), e.type())).toList();
    }
}
