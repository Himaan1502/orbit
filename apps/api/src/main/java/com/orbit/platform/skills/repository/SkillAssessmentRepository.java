package com.orbit.platform.skills.repository;

import com.orbit.platform.skills.model.SkillAssessment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SkillAssessmentRepository extends JpaRepository<SkillAssessment, UUID> {

    Optional<SkillAssessment> findFirstByUserIdAndSkillIdOrderByAssessedAtDesc(UUID userId, UUID skillId);

    List<SkillAssessment> findByUserIdAndSkillIdOrderByAssessedAtDesc(UUID userId, UUID skillId);

    List<SkillAssessment> findByUserIdOrderByAssessedAtDesc(UUID userId);
}
