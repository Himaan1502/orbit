package com.orbit.platform.career.repository;

import com.orbit.platform.career.model.CareerTarget;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CareerTargetRepository extends JpaRepository<CareerTarget, UUID> {

    List<CareerTarget> findByUserIdAndDeletedAtIsNullOrderByPriorityDescCreatedAtDesc(UUID userId);

    Optional<CareerTarget> findByIdAndUserIdAndDeletedAtIsNull(UUID id, UUID userId);
}
