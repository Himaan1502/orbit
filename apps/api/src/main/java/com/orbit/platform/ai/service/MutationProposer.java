package com.orbit.platform.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orbit.platform.ai.provider.ChatItem;
import com.orbit.platform.common.exception.ApiException;
import com.orbit.platform.objectives.model.Objective;
import com.orbit.platform.objectives.service.ObjectiveService;
import com.orbit.platform.projects.service.ProjectService;
import com.orbit.platform.execution.service.TaskService;
import com.orbit.platform.skills.repository.SkillRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.orbit.platform.ai.service.ToolArgs.optionalUuid;
import static com.orbit.platform.ai.service.ToolArgs.putIfInt;
import static com.orbit.platform.ai.service.ToolArgs.putIfText;
import static com.orbit.platform.ai.service.ToolArgs.requireText;
import static com.orbit.platform.ai.service.ToolArgs.requireUuid;
import static com.orbit.platform.ai.service.ToolArgs.text;

/**
 * Turns a mutation-tool call into a {@link ProposedAction} — after validating
 * the args and ownership-checking every referenced id (ADR-032 §4). <b>Nothing
 * here writes.</b> The returned {@code ProposedAction.call} is the exact existing
 * {@code /api/v1} request the frontend's "Start" button makes; that endpoint
 * re-enforces ownership and validation.
 */
@Component
public class MutationProposer {

    private static final Logger log = LoggerFactory.getLogger(MutationProposer.class);

    private final ObjectMapper json;
    private final ObjectiveService objectives;
    private final TaskService tasks;
    private final ProjectService projects;
    private final SkillRepository skillRepo;

    public MutationProposer(ObjectMapper json, ObjectiveService objectives, TaskService tasks,
                            ProjectService projects, SkillRepository skillRepo) {
        this.json = json;
        this.objectives = objectives;
        this.tasks = tasks;
        this.projects = projects;
        this.skillRepo = skillRepo;
    }

    /** Not {@code @Transactional} — ownership checks run in the domain services' own txs. */
    public MutationOutcome propose(ChatItem.ToolCall call) {
        String name = call.name();
        JsonNode a = ToolArgs.parse(json, call.argumentsJson());
        try {
            return switch (name) {
                case "create_task" -> createTask(a);
                case "complete_task" -> completeTask(a);
                case "update_objective" -> updateObjective(a);
                case "create_project" -> createProject(a);
                case "record_evidence" -> recordEvidence(a);
                default -> new MutationOutcome.Rejected(name, "not a known mutation tool");
            };
        } catch (ApiException e) {
            return new MutationOutcome.Rejected(name, e.getMessage());
        } catch (RuntimeException e) {
            log.warn("event=oscar.mutation.invalid tool={} error={}", name, e.toString());
            return new MutationOutcome.Rejected(name, "invalid arguments");
        }
    }

    private MutationOutcome createTask(JsonNode a) {
        UUID objectiveId = requireUuid(a, "objectiveId");
        String title = requireText(a, "title");
        Objective obj = objectives.requireOwned(objectiveId);   // 404 if not the caller's
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("objectiveId", objectiveId.toString());
        body.put("title", title);
        putIfText(body, "description", a, "description");
        putIfInt(body, "estimatedMinutes", a, "estimatedMinutes");
        putIfText(body, "dueAt", a, "dueAt");
        UUID skillId = optionalUuid(a, "skillId");
        if (skillId != null && skillRepo.findById(skillId).isPresent()) {
            body.put("skillId", skillId.toString());
        }
        UUID projectId = optionalUuid(a, "projectId");
        if (projectId != null) {
            projects.getMine(projectId);
            body.put("projectId", projectId.toString());
        }
        return proposed("create_task",
                "Create task \"" + title + "\" under objective \"" + obj.getTitle() + "\"",
                "POST", "/api/v1/tasks", body,
                "Proposed by OSCAR from your current objectives and priorities.");
    }

    private MutationOutcome completeTask(JsonNode a) {
        UUID taskId = requireUuid(a, "taskId");
        var task = tasks.getMine(taskId);   // 404 if not the caller's
        return proposed("complete_task",
                "Mark task \"" + task.getTitle() + "\" complete",
                "POST", "/api/v1/tasks/" + taskId + "/complete", Map.of(),
                "Proposed by OSCAR based on your recent execution.");
    }

    private MutationOutcome updateObjective(JsonNode a) {
        UUID objectiveId = requireUuid(a, "objectiveId");
        Objective obj = objectives.requireOwned(objectiveId);
        Map<String, Object> body = new LinkedHashMap<>();
        putIfText(body, "title", a, "title");
        putIfText(body, "description", a, "description");
        putIfInt(body, "priority", a, "priority");
        String status = text(a, "status");
        if (status != null && List.of("active", "completed", "archived").contains(status)) {
            body.put("status", status);
        }
        putIfText(body, "targetDate", a, "targetDate");
        if (body.isEmpty()) {
            return new MutationOutcome.Rejected("update_objective", "no changed fields supplied");
        }
        return proposed("update_objective",
                "Update objective \"" + obj.getTitle() + "\": " + String.join(", ", body.keySet()),
                "PATCH", "/api/v1/objectives/" + objectiveId, body,
                "Proposed by OSCAR — objective changes are never applied without your confirmation (PRD §10).");
    }

    private MutationOutcome createProject(JsonNode a) {
        String name = requireText(a, "name");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", name);
        putIfText(body, "description", a, "description");
        UUID objectiveId = optionalUuid(a, "objectiveId");
        if (objectiveId != null) {
            objectives.requireOwned(objectiveId);
            body.put("objectiveId", objectiveId.toString());
        }
        return proposed("create_project", "Create project \"" + name + "\"",
                "POST", "/api/v1/projects", body,
                "Proposed by OSCAR from your objectives and skill gaps.");
    }

    private MutationOutcome recordEvidence(JsonNode a) {
        String type = requireText(a, "type");
        String title = requireText(a, "title");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", type);
        body.put("title", title);
        putIfText(body, "description", a, "description");
        putIfText(body, "sourceUrl", a, "sourceUrl");
        UUID skillId = optionalUuid(a, "skillId");
        if (skillId != null && skillRepo.findById(skillId).isPresent()) {
            body.put("skills", List.of(Map.of("skillId", skillId.toString())));
        }
        return proposed("record_evidence", "Log " + type + " evidence \"" + title + "\"",
                "POST", "/api/v1/evidence", body,
                "Proposed by OSCAR — evidence is what ORBIT keeps distinct from self-assessment (PRD §7).");
    }

    private static MutationOutcome proposed(String tool, String description, String method, String path,
                                            Map<String, Object> body, String reasoning) {
        return new MutationOutcome.Proposed(new ProposedAction(tool, description,
                new ProposedAction.ApiCall(method, path, body), reasoning));
    }
}
