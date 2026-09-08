package com.orbit.platform.roadmaps.repository;

import com.orbit.platform.roadmaps.model.RoadmapTopic;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RoadmapTopicRepository extends JpaRepository<RoadmapTopic, UUID> {

    List<RoadmapTopic> findByRoadmapIdOrderBySortOrderAscNameAsc(UUID roadmapId);
}
