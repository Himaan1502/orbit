package com.orbit.platform.evidence.repository;

import com.orbit.platform.evidence.model.Evidence;
import com.orbit.platform.evidence.model.EvidenceType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EvidenceRepository extends JpaRepository<Evidence, UUID> {

    List<Evidence> findByUserIdAndDeletedAtIsNullOrderByOccurredAtDescCreatedAtDesc(UUID userId);

    List<Evidence> findByUserIdAndTypeAndDeletedAtIsNullOrderByOccurredAtDescCreatedAtDesc(
            UUID userId, EvidenceType type);

    Optional<Evidence> findByIdAndUserIdAndDeletedAtIsNull(UUID id, UUID userId);
}
