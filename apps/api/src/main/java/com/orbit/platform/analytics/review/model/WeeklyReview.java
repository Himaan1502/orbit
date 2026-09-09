package com.orbit.platform.analytics.review.model;

import com.orbit.platform.analytics.review.WeeklyReviewContent;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A generated weekly review (Blueprint §16, ADR-028). {@code content} holds the
 * six PRD §8 sections as structured data — references and deltas, not a copy of
 * the metrics. {@code userNotes} is the user's own reflection and is never
 * overwritten by regeneration.
 */
@Entity
@Table(name = "weekly_reviews", schema = "orbit")
@Getter
@NoArgsConstructor
public class WeeklyReview {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "week_start", nullable = false, updatable = false)
    private LocalDate weekStart;

    @Column(name = "week_end", nullable = false)
    private LocalDate weekEnd;

    @Column(name = "summary", columnDefinition = "text")
    private String summary;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "content", columnDefinition = "jsonb", nullable = false)
    private WeeklyReviewContent content = WeeklyReviewContent.empty();

    @Column(name = "user_notes", columnDefinition = "text")
    private String userNotes;

    @Column(name = "generated_by", nullable = false, length = 16)
    private String generatedBy = "system";

    @Column(name = "status", nullable = false, length = 16)
    private String status = "generated";

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static WeeklyReview create(UUID userId, LocalDate weekStart, LocalDate weekEnd,
                                      String summary, WeeklyReviewContent content, Instant now) {
        WeeklyReview r = new WeeklyReview();
        r.id = UUID.randomUUID();
        r.userId = userId;
        r.weekStart = weekStart;
        r.weekEnd = weekEnd;
        r.summary = summary;
        r.content = content;
        r.generatedBy = "system";
        r.status = "generated";
        r.createdAt = now;
        r.updatedAt = now;
        return r;
    }

    /** Regenerate: replace the generated parts, keep {@code userNotes} + {@code status} (ADR-028 §3). */
    public void regenerate(String summary, WeeklyReviewContent content, Instant now) {
        this.summary = summary;
        this.content = content;
        this.updatedAt = now;
    }

    public void setUserNotes(String notes, Instant now) {
        this.userNotes = notes;
        this.updatedAt = now;
    }

    public void markReviewed(Instant now) {
        this.status = "reviewed";
        this.updatedAt = now;
    }
}
