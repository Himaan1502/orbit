package com.orbit.platform.projects.repository;

import com.orbit.platform.projects.model.ProjectSkill;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface ProjectSkillRepository extends JpaRepository<ProjectSkill, ProjectSkill.Key> {

    List<ProjectSkill> findByProjectId(UUID projectId);

    void deleteByProjectId(UUID projectId);

    List<ProjectSkill> findBySkillId(UUID skillId);

    /**
     * Every {@code project_skills} row for a set of the caller's non-archived,
     * non-deleted projects — the input to Career Readiness {@code projectCoverage}
     * (ADR-026 §2).
     */
    @Query("""
            select ps from ProjectSkill ps
              join Project p on p.id = ps.projectId
            where p.userId = :userId and p.deletedAt is null
            """)
    List<ProjectSkill> findAllForUser(@Param("userId") UUID userId);

    @Query("""
            select ps from ProjectSkill ps
              join Project p on p.id = ps.projectId
            where p.userId = :userId and p.deletedAt is null and ps.skillId in :skillIds
            """)
    List<ProjectSkill> findForUserAndSkills(@Param("userId") UUID userId,
                                            @Param("skillIds") Collection<UUID> skillIds);

    /**
     * Skill rows for the caller's projects completed at or before {@code asOf}
     * (ADR-027 §2, readiness trend). {@code completed_at} is only set while a
     * project is completed — cleared on re-open — so it alone gates completion.
     */
    @Query("""
            select ps from ProjectSkill ps
              join Project p on p.id = ps.projectId
            where p.userId = :userId and p.deletedAt is null and ps.skillId in :skillIds
              and p.completedAt is not null and p.completedAt <= :asOf
            """)
    List<ProjectSkill> findForUserAndSkillsCompletedBy(@Param("userId") UUID userId,
                                                       @Param("skillIds") Collection<UUID> skillIds,
                                                       @Param("asOf") java.time.Instant asOf);
}
