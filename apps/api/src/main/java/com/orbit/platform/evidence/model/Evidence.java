package com.orbit.platform.evidence.model;

import com.orbit.platform.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * A concrete artifact the user points at to back a capability claim (Blueprint
 * §13, UX §23). Per-user, soft-deletable. Creating one <em>never</em> writes to
 * {@code skill_assessments} (ADR-025 §5) — evidence informs an assessment's
 * {@code confidence}, not its score.
 */
@Entity
@Table(name = "evidence", schema = "orbit")
@Getter
@Setter
@NoArgsConstructor
public class Evidence extends BaseEntity {

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "type", nullable = false, length = 32)
    @Convert(converter = EvidenceType.JpaConverter.class)
    private EvidenceType type;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "source_url", columnDefinition = "text")
    private String sourceUrl;

    @Column(name = "external_identifier", length = 120)
    private String externalIdentifier;

    @Column(name = "verification_status", nullable = false, length = 20)
    @Convert(converter = VerificationStatus.JpaConverter.class)
    private VerificationStatus verificationStatus = VerificationStatus.SELF_ATTESTED;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata = new HashMap<>();

    @Column(name = "occurred_at")
    private Instant occurredAt;

    public static Evidence create(UUID userId, EvidenceType type, String title, String description,
                                  String sourceUrl, String externalIdentifier, Instant occurredAt,
                                  Map<String, Object> metadata) {
        Evidence e = new Evidence();
        e.userId = userId;
        e.type = type;
        e.title = title;
        e.description = description;
        e.sourceUrl = sourceUrl;
        e.externalIdentifier = externalIdentifier;
        e.occurredAt = occurredAt;
        e.verificationStatus = VerificationStatus.SELF_ATTESTED;
        e.metadata = metadata != null ? metadata : new HashMap<>();
        return e;
    }
}
