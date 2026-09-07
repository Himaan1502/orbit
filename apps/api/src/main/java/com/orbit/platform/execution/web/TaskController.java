package com.orbit.platform.execution.web;

import com.orbit.platform.common.dto.ApiResponse;
import com.orbit.platform.execution.service.TaskService;
import com.orbit.platform.execution.web.dto.TaskDtos.CreateTaskRequest;
import com.orbit.platform.execution.web.dto.TaskDtos.TaskResponse;
import com.orbit.platform.execution.web.dto.TaskDtos.UpdateTaskRequest;
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

/** {@code /api/v1/tasks} (Blueprint §20). Scoped to the authenticated caller's tasks. */
@RestController
@RequestMapping("/api/v1/tasks")
public class TaskController {

    private final TaskService tasks;

    public TaskController(TaskService tasks) {
        this.tasks = tasks;
    }

    @GetMapping
    public ApiResponse<List<TaskResponse>> list() {
        return ApiResponse.of(tasks.listMine().stream().map(TaskResponse::from).toList());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<TaskResponse> create(@Valid @RequestBody CreateTaskRequest request) {
        return ApiResponse.of(TaskResponse.from(tasks.create(request)));
    }

    @GetMapping("/{id}")
    public ApiResponse<TaskResponse> get(@PathVariable UUID id) {
        return ApiResponse.of(TaskResponse.from(tasks.getMine(id)));
    }

    @PatchMapping("/{id}")
    public ApiResponse<TaskResponse> update(@PathVariable UUID id,
                                            @Valid @RequestBody UpdateTaskRequest request) {
        return ApiResponse.of(TaskResponse.from(tasks.update(id, request)));
    }

    @PostMapping("/{id}/complete")
    public ApiResponse<TaskResponse> complete(@PathVariable UUID id) {
        return ApiResponse.of(TaskResponse.from(tasks.complete(id)));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        tasks.delete(id);
    }
}
