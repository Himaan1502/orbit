package com.orbit.platform.evidence.repository;

import com.orbit.platform.evidence.model.SkillEvidence;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface SkillEvidenceRepository extends JpaRepository<SkillEvidence, SkillEvidence.Key> {

    List<SkillEvidence> findByEvidenceId(UUID evidenceId);

    List<SkillEvidence> findBySkillId(UUID skillId);

    List<SkillEvidence> findByEvidenceIdIn(Collection<UUID> evidenceIds);

    void deleteByEvidenceId(UUID evidenceId);

    /**
     * {@code (skillId, contribution_weight, evidence type)} rows for a user's
     * non-deleted evidence — the input to the confidence calculator (ADR-026 §1)
     * and readiness {@code evidenceCoverage} (ADR-026 §2).
     */
    @Query("""
            select se.skillId as skillId, se.contributionWeight as weight, e.type as type
            from SkillEvidence se
              join Evidence e on e.id = se.evidenceId
            where e.userId = :userId and e.deletedAt is null
            """)
    List<SkillEvidenceRow> findAllForUser(@Param("userId") UUID userId);

    @Query("""
            select se.skillId as skillId, se.contributionWeight as weight, e.type as type
            from SkillEvidence se
              join Evidence e on e.id = se.evidenceId
            where e.userId = :userId and e.deletedAt is null and se.skillId = :skillId
            """)
    List<SkillEvidenceRow> findForUserAndSkill(@Param("userId") UUID userId, @Param("skillId") UUID skillId);

    @Query("""
            select se.skillId as skillId, se.contributionWeight as weight, e.type as type
            from SkillEvidence se
              join Evidence e on e.id = se.evidenceId
            where e.userId = :userId and e.deletedAt is null and se.skillId in :skillIds
            """)
    List<SkillEvidenceRow> findForUserAndSkills(@Param("userId") UUID userId,
                                                @Param("skillIds") Collection<UUID> skillIds);

    /** Σ contribution_weight of live evidence linked to both a skill and a project (ADR-026 §3). */
    @Query("""
            select coalesce(sum(se.contributionWeight), 0)
            from SkillEvidence se
              join Evidence e on e.id = se.evidenceId
            where se.skillId = :skillId
              and e.deletedAt is null
              and se.evidenceId in (select pe.evidenceId from ProjectEvidence pe where pe.projectId = :projectId)
            """)
    java.math.BigDecimal sumWeightForProjectAndSkill(@Param("projectId") UUID projectId,
                                                     @Param("skillId") UUID skillId);

    interface SkillEvidenceRow {
        UUID getSkillId();

        java.math.BigDecimal getWeight();

        com.orbit.platform.evidence.model.EvidenceType getType();
    }
}
