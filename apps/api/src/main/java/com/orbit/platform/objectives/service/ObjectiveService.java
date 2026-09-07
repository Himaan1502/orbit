package com.orbit.platform.objectives.service;

import com.orbit.platform.common.exception.ApiException;
import com.orbit.platform.identity.service.CurrentUserService;
import com.orbit.platform.objectives.model.Objective;
import com.orbit.platform.objectives.model.ObjectiveStatus;
import com.orbit.platform.objectives.repository.ObjectiveRepository;
import com.orbit.platform.objectives.web.dto.ObjectiveDtos.CreateObjectiveRequest;
import com.orbit.platform.objectives.web.dto.ObjectiveDtos.UpdateObjectiveRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Objective use-cases with resource-ownership enforced <em>server-side, on every
 * operation</em> (Tech Arch §9; Blueprint §36 IDOR). Every lookup is scoped to
 * {@code currentUserId()}; a request for an objective the caller does not own is
 * answered {@code 404} — never {@code 403} — so existence is not disclosed
 * (ADR-016 §5, the Milestone 1 pattern).
 *
 * <p>{@code status} auto-transitions and {@code progress} are owned by
 * {@link ObjectiveProgressService}; they are never set through this service's
 * {@code update}.
 */
@Service
public class ObjectiveService {

    private final ObjectiveRepository objectives;
    private final CurrentUserService currentUser;

    public ObjectiveService(ObjectiveRepository objectives, CurrentUserService currentUser) {
        this.objectives = objectives;
        this.currentUser = currentUser;
    }

    @Transactional(readOnly = true)
    public List<Objective> listMine(ObjectiveStatus status) {
        UUID userId = currentUser.currentUserId();
        return status == null
                ? objectives.findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(userId)
                : objectives.findByUserIdAndStatusAndDeletedAtIsNullOrderByPriorityDescCreatedAtDesc(userId, status);
    }

    @Transactional(readOnly = true)
    public Objective getMine(UUID id) {
        return objectives.findByIdAndUserIdAndDeletedAtIsNull(id, currentUser.currentUserId())
                .orElseThrow(() -> ApiException.notFound("Objective not found."));
    }

    /** Ownership gate for nested resources (milestones, tasks). 404 if not the caller's. */
    @Transactional(readOnly = true)
    public Objective requireOwned(UUID objectiveId) {
        return getMine(objectiveId);
    }

    @Transactional
    public Objective create(CreateObjectiveRequest req) {
        UUID userId = currentUser.getOrProvision().getId();
        return objectives.save(Objective.create(
                userId,
                req.title().strip(),
                strip(req.description()),
                req.priority(),
                req.targetDate(),
                req.careerTargetId()));
    }

    @Transactional
    public Objective update(UUID id, UpdateObjectiveRequest req) {
        Objective o = getMine(id);
        if (req.title() != null && !req.title().isBlank()) {
            o.setTitle(req.title().strip());
        }
        if (req.description() != null) {
            o.setDescription(strip(req.description()));
        }
        if (req.priority() != null) {
            o.setPriority(req.priority());
        }
        if (req.targetDate() != null) {
            o.setTargetDate(req.targetDate());
        }
        if (req.careerTargetId() != null) {
            o.setCareerTargetId(req.careerTargetId());
        }
        if (req.status() != null && !req.status().isBlank()) {
            // Only ARCHIVED / un-archive is manual; ACTIVE<->COMPLETED is derived.
            ObjectiveStatus requested = ObjectiveStatus.fromValue(req.status());
            if (requested == ObjectiveStatus.ARCHIVED || o.getStatus() == ObjectiveStatus.ARCHIVED) {
                o.setStatus(requested);
            } else {
                throw ApiException.badRequest(
                        "Objective status is derived from progress; only 'archived' can be set manually.");
            }
        }
        return objectives.save(o);
    }

    @Transactional
    public void delete(UUID id) {
        Objective o = getMine(id);
        o.softDelete();
        objectives.save(o);
    }

    private static String strip(String s) {
        return s == null ? null : s.strip();
    }
}
