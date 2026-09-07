package com.orbit.platform.career.repository;

import com.orbit.platform.career.model.CareerTargetSkill;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CareerTargetSkillRepository
        extends JpaRepository<CareerTargetSkill, CareerTargetSkill.Key> {

    List<CareerTargetSkill> findByCareerTargetId(UUID careerTargetId);

    void deleteByCareerTargetId(UUID careerTargetId);
}
