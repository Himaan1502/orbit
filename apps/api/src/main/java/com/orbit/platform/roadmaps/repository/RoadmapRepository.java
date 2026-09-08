package com.orbit.platform.roadmaps.repository;

import com.orbit.platform.roadmaps.model.Roadmap;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RoadmapRepository extends JpaRepository<Roadmap, UUID> {

    List<Roadmap> findByStatusOrderByPriorityTierAscNameAsc(String status);

    Optional<Roadmap> findByExternalIdentifier(String externalIdentifier);
}
