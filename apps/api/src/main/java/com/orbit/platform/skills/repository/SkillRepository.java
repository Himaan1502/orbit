package com.orbit.platform.skills.repository;

import com.orbit.platform.skills.model.Skill;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SkillRepository extends JpaRepository<Skill, UUID> {

    List<Skill> findByStatusOrderByCategoryAscCanonicalNameAsc(String status);
}
