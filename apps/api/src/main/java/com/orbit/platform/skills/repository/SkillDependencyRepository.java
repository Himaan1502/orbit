package com.orbit.platform.skills.repository;

import com.orbit.platform.skills.model.SkillDependency;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SkillDependencyRepository
        extends JpaRepository<SkillDependency, SkillDependency.Key> {
}
