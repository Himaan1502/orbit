package com.orbit.platform.roadmaps.repository;

import com.orbit.platform.roadmaps.model.RoadmapTopicSkill;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface RoadmapTopicSkillRepository
        extends JpaRepository<RoadmapTopicSkill, RoadmapTopicSkill.Key> {

    /** Every topic→skill mapping for one roadmap (topic id carried on the row). */
    @Query("""
            select rts from RoadmapTopicSkill rts
              join RoadmapTopic rt on rt.id = rts.roadmapTopicId
            where rt.roadmapId = :roadmapId
            """)
    List<RoadmapTopicSkill> findAllForRoadmap(@Param("roadmapId") UUID roadmapId);

    /** Distinct (skill, roadmap) pairs across a set of roadmaps — the intersection input (ADR-023 §2). */
    @Query("""
            select distinct rts.skillId as skillId, rt.roadmapId as roadmapId
            from RoadmapTopicSkill rts
              join RoadmapTopic rt on rt.id = rts.roadmapTopicId
            where rt.roadmapId in :roadmapIds
            """)
    List<SkillRoadmapPair> findSkillRoadmapPairs(@Param("roadmapIds") Collection<UUID> roadmapIds);

    /** Roadmap ids whose topics map to a given skill — the Skill → roadmaps reverse lookup (Blueprint §11). */
    @Query("""
            select distinct rt.roadmapId
            from RoadmapTopicSkill rts
              join RoadmapTopic rt on rt.id = rts.roadmapTopicId
            where rts.skillId = :skillId
            """)
    List<UUID> findRoadmapIdsForSkill(@Param("skillId") UUID skillId);

    interface SkillRoadmapPair {
        UUID getSkillId();

        UUID getRoadmapId();
    }
}
