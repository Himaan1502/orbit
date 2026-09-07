package com.orbit.platform.career.service;

import com.orbit.platform.career.model.CareerTarget;
import com.orbit.platform.career.model.CareerTargetSkill;
import com.orbit.platform.career.model.CareerTargetStatus;
import com.orbit.platform.career.repository.CareerTargetRepository;
import com.orbit.platform.career.repository.CareerTargetSkillRepository;
import com.orbit.platform.career.web.dto.CareerDtos.CareerTargetSkillInput;
import com.orbit.platform.career.web.dto.CareerDtos.CreateCareerTargetRequest;
import com.orbit.platform.career.web.dto.CareerDtos.UpdateCareerTargetRequest;
import com.orbit.platform.common.exception.ApiException;
import com.orbit.platform.identity.service.CurrentUserService;
import com.orbit.platform.skills.repository.SkillRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * CRUD for the caller's career targets and their weighted skill sets
 * (Blueprint §8). Ownership enforced on every op; a target the caller does not
 * own is answered {@code 404} (the M1 pattern).
 */
@Service
public class CareerTargetService {

    private final CareerTargetRepository targets;
    private final CareerTargetSkillRepository targetSkills;
    private final SkillRepository skills;
    private final CurrentUserService currentUser;

    public CareerTargetService(CareerTargetRepository targets, CareerTargetSkillRepository targetSkills,
                               SkillRepository skills, CurrentUserService currentUser) {
        this.targets = targets;
        this.targetSkills = targetSkills;
        this.skills = skills;
        this.currentUser = currentUser;
    }

    public record TargetWithSkills(CareerTarget target, List<CareerTargetSkill> skills) {
    }

    @Transactional(readOnly = true)
    public List<CareerTarget> listMine() {
        return targets.findByUserIdAndDeletedAtIsNullOrderByPriorityDescCreatedAtDesc(
                currentUser.currentUserId());
    }

    /** Same order as {@link #listMine()}, each target with its weighted skill set attached. */
    @Transactional(readOnly = true)
    public List<TargetWithSkills> listMineWithSkills() {
        return listMine().stream()
                .map(t -> new TargetWithSkills(t, targetSkills.findByCareerTargetId(t.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public TargetWithSkills getMine(UUID id) {
        CareerTarget t = targets.findByIdAndUserIdAndDeletedAtIsNull(id, currentUser.currentUserId())
                .orElseThrow(() -> ApiException.notFound("Career target not found."));
        return new TargetWithSkills(t, targetSkills.findByCareerTargetId(id));
    }

    @Transactional(readOnly = true)
    public CareerTarget primaryOf(UUID userId) {
        return targets.findByUserIdAndDeletedAtIsNullOrderByPriorityDescCreatedAtDesc(userId)
                .stream().filter(t -> t.getStatus() == CareerTargetStatus.ACTIVE)
                .findFirst().orElse(null);
    }

    @Transactional
    public TargetWithSkills create(CreateCareerTargetRequest req) {
        UUID userId = currentUser.getOrProvision().getId();
        CareerTarget saved = targets.save(CareerTarget.create(
                userId, req.name().strip(), strip(req.roleFamily()), strip(req.specialization()),
                req.priority(), req.targetDate(),
                req.targetReadiness() == null ? null : BigDecimal.valueOf(req.targetReadiness())));
        if (req.skills() != null && !req.skills().isEmpty()) {
            replaceSkills(saved.getId(), req.skills());
        }
        return getMine(saved.getId());
    }

    @Transactional
    public TargetWithSkills update(UUID id, UpdateCareerTargetRequest req) {
        CareerTarget t = getMine(id).target();
        if (req.name() != null && !req.name().isBlank()) {
            t.setName(req.name().strip());
        }
        if (req.roleFamily() != null) {
            t.setRoleFamily(strip(req.roleFamily()));
        }
        if (req.specialization() != null) {
            t.setSpecialization(strip(req.specialization()));
        }
        if (req.priority() != null) {
            t.setPriority(req.priority());
        }
        if (req.targetDate() != null) {
            t.setTargetDate(req.targetDate());
        }
        if (req.targetReadiness() != null) {
            t.setTargetReadiness(BigDecimal.valueOf(req.targetReadiness()));
        }
        if (req.status() != null && !req.status().isBlank()) {
            t.setStatus(CareerTargetStatus.fromValue(req.status()));
        }
        targets.save(t);
        if (req.skills() != null) {
            replaceSkills(id, req.skills());
        }
        return getMine(id);
    }

    @Transactional
    public void delete(UUID id) {
        CareerTarget t = getMine(id).target();
        t.softDelete();
        targets.save(t);
    }

    private void replaceSkills(UUID targetId, List<CareerTargetSkillInput> inputs) {
        for (CareerTargetSkillInput in : inputs) {
            if (skills.findById(in.skillId()).isEmpty()) {
                throw ApiException.notFound("Skill not found: " + in.skillId());
            }
        }
        targetSkills.deleteByCareerTargetId(targetId);
        targetSkills.flush();
        for (CareerTargetSkillInput in : inputs) {
            targetSkills.save(CareerTargetSkill.of(
                    targetId, in.skillId(),
                    bd(in.importance()), bd(in.interviewWeight()), bd(in.productionWeight())));
        }
    }

    private static BigDecimal bd(Double v) {
        return v == null ? null : BigDecimal.valueOf(v);
    }

    private static String strip(String s) {
        return s == null ? null : s.strip();
    }
}
