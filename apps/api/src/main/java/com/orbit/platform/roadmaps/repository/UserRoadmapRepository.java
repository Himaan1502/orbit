package com.orbit.platform.roadmaps.repository;

import com.orbit.platform.roadmaps.model.UserRoadmap;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRoadmapRepository extends JpaRepository<UserRoadmap, UserRoadmap.Key> {

    List<UserRoadmap> findByUserIdOrderByStartedAtDesc(UUID userId);

    Optional<UserRoadmap> findByUserIdAndRoadmapId(UUID userId, UUID roadmapId);

    void deleteByUserIdAndRoadmapId(UUID userId, UUID roadmapId);
}
