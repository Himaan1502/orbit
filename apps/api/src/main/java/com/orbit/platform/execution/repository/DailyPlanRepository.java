package com.orbit.platform.execution.repository;

import com.orbit.platform.execution.model.DailyPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DailyPlanRepository extends JpaRepository<DailyPlan, UUID> {

    Optional<DailyPlan> findByUserIdAndPlanDate(UUID userId, LocalDate planDate);

    Optional<DailyPlan> findByIdAndUserId(UUID id, UUID userId);

    java.util.List<DailyPlan> findByUserIdAndPlanDateBetweenOrderByPlanDateAsc(
            UUID userId, LocalDate from, LocalDate to);
}
