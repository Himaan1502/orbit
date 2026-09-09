package com.orbit.platform.analytics.review.web;

import com.orbit.platform.analytics.review.WeeklyReviewContent;
import com.orbit.platform.analytics.review.model.WeeklyReview;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Request / response shapes for {@code /api/v1/weekly-reviews} (ADR-028). */
public final class WeeklyReviewDtos {

    private WeeklyReviewDtos() {
    }

    public record GenerateRequest(LocalDate weekStart) {
    }

    public record UpdateRequest(
            @Size(max = 5000) String userNotes,
            @Pattern(regexp = "generated|reviewed", message = "Unknown status.") String status) {
    }

    public record WeeklyReviewResponse(
            UUID id, LocalDate weekStart, LocalDate weekEnd, String summary,
            WeeklyReviewContent content, String userNotes, String generatedBy, String status,
            Instant createdAt, Instant updatedAt) {

        public static WeeklyReviewResponse from(WeeklyReview r) {
            return new WeeklyReviewResponse(r.getId(), r.getWeekStart(), r.getWeekEnd(), r.getSummary(),
                    r.getContent(), r.getUserNotes(), r.getGeneratedBy(), r.getStatus(),
                    r.getCreatedAt(), r.getUpdatedAt());
        }
    }
}
