package com.orbit.platform.skills.web;

import com.orbit.platform.common.dto.ApiResponse;
import com.orbit.platform.identity.service.CurrentUserService;
import com.orbit.platform.skills.service.SkillAssessmentService;
import com.orbit.platform.skills.service.SkillAssessmentService.AssessmentInput;
import com.orbit.platform.skills.service.SkillAssessmentService.AssessmentResult;
import com.orbit.platform.skills.service.SkillService;
import com.orbit.platform.skills.web.dto.SkillDtos.AssessSkillRequest;
import com.orbit.platform.skills.web.dto.SkillDtos.AssessmentResponse;
import com.orbit.platform.skills.web.dto.SkillDtos.SkillDependenciesResponse;
import com.orbit.platform.skills.web.dto.SkillDtos.SkillDetail;
import com.orbit.platform.skills.web.dto.SkillDtos.SkillSummary;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** {@code /api/v1/skills} (Blueprint §20). The catalog is global; assessments are the caller's. */
@RestController
@RequestMapping("/api/v1/skills")
public class SkillController {

    private final SkillService skills;
    private final SkillAssessmentService assessmentService;
    private final CurrentUserService currentUser;

    public SkillController(SkillService skills, SkillAssessmentService assessmentService,
                           CurrentUserService currentUser) {
        this.skills = skills;
        this.assessmentService = assessmentService;
        this.currentUser = currentUser;
    }

    @GetMapping
    public ApiResponse<List<SkillSummary>> list() {
        return ApiResponse.of(skills.listForUser(currentUser.currentUserId()).stream()
                .map(SkillSummary::from).toList());
    }

    @GetMapping("/{id}")
    public ApiResponse<SkillDetail> get(@PathVariable UUID id) {
        return ApiResponse.of(SkillDetail.from(skills.getForUser(id, currentUser.currentUserId())));
    }

    @GetMapping("/{id}/dependencies")
    public ApiResponse<SkillDependenciesResponse> dependencies(@PathVariable UUID id) {
        return ApiResponse.of(SkillDependenciesResponse.from(
                skills.neighboursForUser(id, currentUser.currentUserId())));
    }

    @PostMapping("/{id}/assessments")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AssessmentResponse> assess(@PathVariable UUID id,
                                                  @Valid @RequestBody AssessSkillRequest request) {
        AssessmentResult result = assessmentService.assess(id, new AssessmentInput(
                request.knowledge(), request.practical(), request.evidence(),
                request.interview(), request.production()));
        return ApiResponse.of(AssessmentResponse.from(
                result.assessment(), result.score(), result.confidence()));
    }
}
