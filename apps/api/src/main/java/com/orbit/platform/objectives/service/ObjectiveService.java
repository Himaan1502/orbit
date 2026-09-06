package com.orbit.platform.objectives.service;

import com.orbit.platform.common.exception.ApiException;
import com.orbit.platform.identity.service.CurrentUserService;
import com.orbit.platform.objectives.model.Objective;
import com.orbit.platform.objectives.repository.ObjectiveRepository;
import com.orbit.platform.objectives.web.dto.ObjectiveDtos.CreateObjectiveRequest;
import com.orbit.platform.objectives.web.dto.ObjectiveDtos.UpdateObjectiveRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Objective use-cases with resource-ownership enforced <em>server-side, on every
 * operation</em> (Tech Arch §9: "resource ownership is enforced independently of
 * UI"; Blueprint §36 threat model: IDOR / cross-user data access).
 *
 * <p>Every lookup is scoped to {@code currentUserId()}. A request for an
 * objective the caller does not own is indistinguishable from one that does not
 * exist — {@code 404}, never {@code 403} — so the API does not confirm the
 * existence of other users' resources (existence-disclosure hardening).
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
    public List<Objective> listMine() {
        return objectives.findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(currentUser.currentUserId());
    }

    @Transactional(readOnly = true)
    public Objective getMine(UUID id) {
        return objectives.findByIdAndUserIdAndDeletedAtIsNull(id, currentUser.currentUserId())
                .orElseThrow(() -> ApiException.notFound("Objective not found."));
    }

    @Transactional
    public Objective create(CreateObjectiveRequest req) {
        // Ensures the user's profile row exists before we reference it as owner.
        UUID userId = currentUser.getOrProvision().getId();
        return objectives.save(Objective.create(
                userId,
                req.title().strip(),
                req.description() == null ? null : req.description().strip(),
                req.targetDate()));
    }

    @Transactional
    public Objective update(UUID id, UpdateObjectiveRequest req) {
        Objective objective = getMine(id);
        if (req.title() != null && !req.title().isBlank()) {
            objective.setTitle(req.title().strip());
        }
        if (req.description() != null) {
            objective.setDescription(req.description().strip());
        }
        if (req.status() != null && !req.status().isBlank()) {
            objective.setStatus(req.status());
        }
        if (req.targetDate() != null) {
            objective.setTargetDate(req.targetDate());
        }
        return objectives.save(objective);
    }

    @Transactional
    public void delete(UUID id) {
        Objective objective = getMine(id);
        objective.softDelete();
        objectives.save(objective);
    }
}
