package com.orbit.platform.objectives.web;

import com.orbit.platform.common.dto.ApiResponse;
import com.orbit.platform.objectives.service.ObjectiveService;
import com.orbit.platform.objectives.web.dto.ObjectiveDtos.CreateObjectiveRequest;
import com.orbit.platform.objectives.web.dto.ObjectiveDtos.ObjectiveResponse;
import com.orbit.platform.objectives.web.dto.ObjectiveDtos.UpdateObjectiveRequest;
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

/**
 * {@code /api/v1/objectives} (Blueprint §20). Every handler operates only on the
 * authenticated caller's own objectives — see {@link ObjectiveService}.
 */
@RestController
@RequestMapping("/api/v1/objectives")
public class ObjectiveController {

    private final ObjectiveService objectives;

    public ObjectiveController(ObjectiveService objectives) {
        this.objectives = objectives;
    }

    @GetMapping
    public ApiResponse<List<ObjectiveResponse>> list() {
        List<ObjectiveResponse> body = objectives.listMine().stream()
                .map(ObjectiveResponse::from)
                .toList();
        return ApiResponse.of(body);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ObjectiveResponse> create(@Valid @RequestBody CreateObjectiveRequest request) {
        return ApiResponse.of(ObjectiveResponse.from(objectives.create(request)));
    }

    @GetMapping("/{id}")
    public ApiResponse<ObjectiveResponse> get(@PathVariable UUID id) {
        return ApiResponse.of(ObjectiveResponse.from(objectives.getMine(id)));
    }

    @PatchMapping("/{id}")
    public ApiResponse<ObjectiveResponse> update(@PathVariable UUID id,
                                                 @Valid @RequestBody UpdateObjectiveRequest request) {
        return ApiResponse.of(ObjectiveResponse.from(objectives.update(id, request)));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        objectives.delete(id);
    }
}
