package com.orbit.platform.analytics.review.web;

import com.orbit.platform.analytics.review.WeeklyReviewService;
import com.orbit.platform.analytics.review.web.WeeklyReviewDtos.GenerateRequest;
import com.orbit.platform.analytics.review.web.WeeklyReviewDtos.UpdateRequest;
import com.orbit.platform.analytics.review.web.WeeklyReviewDtos.WeeklyReviewResponse;
import com.orbit.platform.common.dto.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * {@code /api/v1/weekly-reviews} (Blueprint §16/§20, UX Journey E, ADR-028).
 * Reached from Analytics in the UI — not a nav item (ADR-008 unchanged).
 */
@RestController
@RequestMapping("/api/v1/weekly-reviews")
public class WeeklyReviewController {

    private final WeeklyReviewService reviews;

    public WeeklyReviewController(WeeklyReviewService reviews) {
        this.reviews = reviews;
    }

    @GetMapping
    public ApiResponse<List<WeeklyReviewResponse>> list() {
        return ApiResponse.of(reviews.listMine().stream().map(WeeklyReviewResponse::from).toList());
    }

    @GetMapping("/{weekStart}")
    public ApiResponse<WeeklyReviewResponse> forWeek(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart) {
        return ApiResponse.of(WeeklyReviewResponse.from(reviews.getMine(weekStart)));
    }

    @PostMapping("/generate")
    public ApiResponse<WeeklyReviewResponse> generate(@RequestBody(required = false) GenerateRequest req) {
        LocalDate weekStart = req == null ? null : req.weekStart();
        return ApiResponse.of(WeeklyReviewResponse.from(reviews.generateMine(weekStart)));
    }

    @PatchMapping("/{id}")
    public ApiResponse<WeeklyReviewResponse> update(@PathVariable UUID id,
                                                    @Valid @RequestBody UpdateRequest req) {
        return ApiResponse.of(WeeklyReviewResponse.from(reviews.update(id, req.userNotes(), req.status())));
    }
}
