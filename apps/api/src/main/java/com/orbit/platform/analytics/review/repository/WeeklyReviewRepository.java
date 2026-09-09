package com.orbit.platform.analytics.review.repository;

import com.orbit.platform.analytics.review.model.WeeklyReview;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WeeklyReviewRepository extends JpaRepository<WeeklyReview, UUID> {

    List<WeeklyReview> findByUserIdOrderByWeekStartDesc(UUID userId);

    Optional<WeeklyReview> findByUserIdAndWeekStart(UUID userId, LocalDate weekStart);

    Optional<WeeklyReview> findByIdAndUserId(UUID id, UUID userId);
}
