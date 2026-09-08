package com.orbit.platform.roadmaps.service;

import com.orbit.platform.common.exception.ApiException;
import com.orbit.platform.identity.service.CurrentUserService;
import com.orbit.platform.roadmaps.model.UserRoadmap;
import com.orbit.platform.roadmaps.repository.RoadmapRepository;
import com.orbit.platform.roadmaps.repository.UserRoadmapRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * The caller's roadmap adoptions (ADR-021 §5) — the only per-user roadmap state.
 * Ownership is structural (the {@code (user_id, roadmap_id)} PK); a
 * {@code DELETE} for a roadmap the caller has not adopted answers {@code 404}
 * (the M1 pattern).
 */
@Service
public class RoadmapAdoptionService {

    private final UserRoadmapRepository adoptions;
    private final RoadmapRepository roadmaps;
    private final CurrentUserService currentUser;

    public RoadmapAdoptionService(UserRoadmapRepository adoptions, RoadmapRepository roadmaps,
                                  CurrentUserService currentUser) {
        this.adoptions = adoptions;
        this.roadmaps = roadmaps;
        this.currentUser = currentUser;
    }

    @Transactional(readOnly = true)
    public List<UserRoadmap> listMine() {
        return adoptions.findByUserIdOrderByStartedAtDesc(currentUser.currentUserId());
    }

    @Transactional(readOnly = true)
    public Set<UUID> adoptedRoadmapIds(UUID userId) {
        return adoptions.findByUserIdOrderByStartedAtDesc(userId).stream()
                .map(UserRoadmap::getRoadmapId).collect(Collectors.toSet());
    }

    @Transactional
    public UserRoadmap adopt(UUID roadmapId, String status, LocalDate targetDate) {
        if (roadmaps.findById(roadmapId).isEmpty()) {
            throw ApiException.notFound("Roadmap not found.");
        }
        UUID userId = currentUser.getOrProvision().getId();
        UserRoadmap row = adoptions.findByUserIdAndRoadmapId(userId, roadmapId)
                .orElseGet(() -> UserRoadmap.start(userId, roadmapId, targetDate));
        if (status != null && !status.isBlank()) {
            row.setStatus(status);
        }
        if (targetDate != null) {
            row.setTargetDate(targetDate);
        }
        return adoptions.save(row);
    }

    @Transactional
    public void abandon(UUID roadmapId) {
        UUID userId = currentUser.currentUserId();
        if (adoptions.findByUserIdAndRoadmapId(userId, roadmapId).isEmpty()) {
            throw ApiException.notFound("Roadmap not adopted.");
        }
        adoptions.deleteByUserIdAndRoadmapId(userId, roadmapId);
    }
}
