package com.orbit.platform.roadmaps.web;

import com.orbit.platform.common.dto.ApiResponse;
import com.orbit.platform.roadmaps.service.RoadmapAdoptionService;
import com.orbit.platform.roadmaps.service.RoadmapIntersectionService;
import com.orbit.platform.roadmaps.service.RoadmapService;
import com.orbit.platform.roadmaps.web.dto.RoadmapDtos.AdoptRoadmapRequest;
import com.orbit.platform.roadmaps.web.dto.RoadmapDtos.AdoptionResponse;
import com.orbit.platform.roadmaps.web.dto.RoadmapDtos.IntersectionResponse;
import com.orbit.platform.roadmaps.web.dto.RoadmapDtos.RoadmapDetailResponse;
import com.orbit.platform.roadmaps.web.dto.RoadmapDtos.RoadmapSummaryResponse;
import com.orbit.platform.roadmaps.web.dto.RoadmapDtos.TopicResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * {@code /api/v1/roadmaps} (Blueprint §20). The roadmap catalog is global and
 * read-only; adoption is the caller's own (ADR-021 §5).
 */
@RestController
@RequestMapping("/api/v1/roadmaps")
public class RoadmapController {

    private final RoadmapService roadmaps;
    private final RoadmapAdoptionService adoption;
    private final RoadmapIntersectionService intersection;

    public RoadmapController(RoadmapService roadmaps, RoadmapAdoptionService adoption,
                             RoadmapIntersectionService intersection) {
        this.roadmaps = roadmaps;
        this.adoption = adoption;
        this.intersection = intersection;
    }

    @GetMapping
    public ApiResponse<List<RoadmapSummaryResponse>> list() {
        return ApiResponse.of(roadmaps.listForUser().stream()
                .map(RoadmapSummaryResponse::from).toList());
    }

    @GetMapping("/intersections")
    public ApiResponse<IntersectionResponse> intersections(
            @RequestParam(name = "roadmapIds", required = false) List<UUID> roadmapIds) {
        return ApiResponse.of(IntersectionResponse.from(intersection.compute(roadmapIds)));
    }

    @GetMapping("/{id}")
    public ApiResponse<RoadmapDetailResponse> get(@PathVariable UUID id) {
        return ApiResponse.of(RoadmapDetailResponse.from(roadmaps.getForUser(id)));
    }

    @GetMapping("/{id}/topics")
    public ApiResponse<List<TopicResponse>> topics(@PathVariable UUID id) {
        return ApiResponse.of(roadmaps.topicsForRoadmap(id).stream().map(TopicResponse::from).toList());
    }

    @PutMapping("/{id}/adoption")
    public ApiResponse<AdoptionResponse> adopt(@PathVariable UUID id,
                                               @Valid @RequestBody(required = false) AdoptRoadmapRequest req) {
        String status = req == null ? null : req.status();
        var targetDate = req == null ? null : req.targetDate();
        return ApiResponse.of(AdoptionResponse.from(adoption.adopt(id, status, targetDate)));
    }

    @DeleteMapping("/{id}/adoption")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void abandon(@PathVariable UUID id) {
        adoption.abandon(id);
    }
}
