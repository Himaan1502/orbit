package com.orbit.platform.objectives.web;

import com.orbit.platform.common.dto.ApiResponse;
import com.orbit.platform.objectives.service.MilestoneService;
import com.orbit.platform.objectives.service.MilestoneService.MilestoneView;
import com.orbit.platform.objectives.web.dto.MilestoneDtos.CreateMilestoneRequest;
import com.orbit.platform.objectives.web.dto.MilestoneDtos.MilestoneResponse;
import com.orbit.platform.objectives.web.dto.MilestoneDtos.UpdateMilestoneRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** {@code /api/v1/objectives/{objectiveId}/milestones} (Blueprint §20, §9). */
@RestController
@RequestMapping("/api/v1/objectives/{objectiveId}/milestones")
public class MilestoneController {

    private final MilestoneService milestones;

    public MilestoneController(MilestoneService milestones) {
        this.milestones = milestones;
    }

    @GetMapping
    public ApiResponse<List<MilestoneResponse>> list(@PathVariable UUID objectiveId) {
        return ApiResponse.of(milestones.list(objectiveId).stream().map(MilestoneController::toResponse).toList());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<MilestoneResponse> create(@PathVariable UUID objectiveId,
                                                 @Valid @RequestBody CreateMilestoneRequest request) {
        return ApiResponse.of(toResponse(milestones.create(objectiveId, request)));
    }

    @GetMapping("/{milestoneId}")
    public ApiResponse<MilestoneResponse> get(@PathVariable UUID objectiveId,
                                              @PathVariable UUID milestoneId) {
        return ApiResponse.of(toResponse(milestones.get(objectiveId, milestoneId)));
    }

    @PatchMapping("/{milestoneId}")
    public ApiResponse<MilestoneResponse> update(@PathVariable UUID objectiveId,
                                                 @PathVariable UUID milestoneId,
                                                 @Valid @RequestBody UpdateMilestoneRequest request) {
        return ApiResponse.of(toResponse(milestones.update(objectiveId, milestoneId, request)));
    }

    @DeleteMapping("/{milestoneId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID objectiveId, @PathVariable UUID milestoneId) {
        milestones.delete(objectiveId, milestoneId);
    }

    private static MilestoneResponse toResponse(MilestoneView v) {
        return MilestoneResponse.from(v.milestone(), v.progress());
    }
}
