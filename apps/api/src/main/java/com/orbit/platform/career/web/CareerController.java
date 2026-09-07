package com.orbit.platform.career.web;

import com.orbit.platform.career.service.CareerReadinessService;
import com.orbit.platform.career.service.CareerTargetService;
import com.orbit.platform.career.web.dto.CareerDtos.CareerTargetResponse;
import com.orbit.platform.career.web.dto.CareerDtos.CreateCareerTargetRequest;
import com.orbit.platform.career.web.dto.CareerDtos.GapsResponse;
import com.orbit.platform.career.web.dto.CareerDtos.ReadinessResponse;
import com.orbit.platform.career.web.dto.CareerDtos.UpdateCareerTargetRequest;
import com.orbit.platform.common.dto.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** {@code /api/v1/career} (Blueprint §20). Targets are the caller's; the skill catalog is global. */
@RestController
@RequestMapping("/api/v1/career")
public class CareerController {

    private final CareerTargetService targets;
    private final CareerReadinessService readiness;

    public CareerController(CareerTargetService targets, CareerReadinessService readiness) {
        this.targets = targets;
        this.readiness = readiness;
    }

    @GetMapping("/targets")
    public ApiResponse<List<CareerTargetResponse>> listTargets() {
        return ApiResponse.of(targets.listMineWithSkills().stream()
                .map(CareerTargetResponse::from).toList());
    }

    @PostMapping("/targets")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CareerTargetResponse> createTarget(@Valid @RequestBody CreateCareerTargetRequest req) {
        return ApiResponse.of(CareerTargetResponse.from(targets.create(req)));
    }

    @GetMapping("/targets/{id}")
    public ApiResponse<CareerTargetResponse> getTarget(@PathVariable UUID id) {
        return ApiResponse.of(CareerTargetResponse.from(targets.getMine(id)));
    }

    @PatchMapping("/targets/{id}")
    public ApiResponse<CareerTargetResponse> updateTarget(@PathVariable UUID id,
                                                          @Valid @RequestBody UpdateCareerTargetRequest req) {
        return ApiResponse.of(CareerTargetResponse.from(targets.update(id, req)));
    }

    @DeleteMapping("/targets/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteTarget(@PathVariable UUID id) {
        targets.delete(id);
    }

    @GetMapping("/readiness")
    public ApiResponse<ReadinessResponse> readiness(
            @RequestParam(name = "careerTargetId", required = false) UUID careerTargetId) {
        return ApiResponse.of(ReadinessResponse.from(readiness.readiness(careerTargetId)));
    }

    @GetMapping("/gaps")
    public ApiResponse<GapsResponse> gaps(
            @RequestParam(name = "careerTargetId", required = false) UUID careerTargetId) {
        return ApiResponse.of(GapsResponse.from(readiness.readiness(careerTargetId)));
    }
}
