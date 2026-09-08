package com.orbit.platform.evidence.service;

import com.orbit.platform.common.exception.ApiException;
import com.orbit.platform.evidence.model.Evidence;
import com.orbit.platform.evidence.model.EvidenceType;
import com.orbit.platform.evidence.model.ProjectEvidence;
import com.orbit.platform.evidence.model.SkillEvidence;
import com.orbit.platform.evidence.repository.EvidenceRepository;
import com.orbit.platform.evidence.repository.ProjectEvidenceRepository;
import com.orbit.platform.evidence.repository.SkillEvidenceRepository;
import com.orbit.platform.evidence.web.dto.EvidenceDtos.CreateEvidenceRequest;
import com.orbit.platform.evidence.web.dto.EvidenceDtos.EvidenceProjectInput;
import com.orbit.platform.evidence.web.dto.EvidenceDtos.EvidenceSkillInput;
import com.orbit.platform.evidence.web.dto.EvidenceDtos.UpdateEvidenceRequest;
import com.orbit.platform.identity.service.CurrentUserService;
import com.orbit.platform.projects.service.ProjectLookup;
import com.orbit.platform.skills.service.SkillCatalog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * CRUD for the caller's evidence and its skill / project links (Blueprint §13,
 * ADR-025). Ownership enforced on every op ({@code 404}). Creating or updating
 * evidence <strong>never</strong> touches {@code skill_assessments} (ADR-025 §5)
 * — evidence informs an assessment's {@code confidence}, not its score.
 */
@Service
public class EvidenceService {

    private static final Logger log = LoggerFactory.getLogger(EvidenceService.class);

    private final EvidenceRepository evidence;
    private final SkillEvidenceRepository skillLinks;
    private final ProjectEvidenceRepository projectLinks;
    private final SkillCatalog skillCatalog;
    private final ProjectLookup projects;
    private final CurrentUserService currentUser;

    public EvidenceService(EvidenceRepository evidence, SkillEvidenceRepository skillLinks,
                           ProjectEvidenceRepository projectLinks, SkillCatalog skillCatalog,
                           ProjectLookup projects, CurrentUserService currentUser) {
        this.evidence = evidence;
        this.skillLinks = skillLinks;
        this.projectLinks = projectLinks;
        this.skillCatalog = skillCatalog;
        this.projects = projects;
        this.currentUser = currentUser;
    }

    public record EvidenceWithLinks(Evidence evidence, List<SkillEvidence> skills, List<ProjectEvidence> projects) {
    }

    @Transactional(readOnly = true)
    public List<EvidenceWithLinks> listMine(EvidenceType type, UUID skillId, UUID projectId) {
        UUID userId = currentUser.currentUserId();
        List<Evidence> rows = type == null
                ? evidence.findByUserIdAndDeletedAtIsNullOrderByOccurredAtDescCreatedAtDesc(userId)
                : evidence.findByUserIdAndTypeAndDeletedAtIsNullOrderByOccurredAtDescCreatedAtDesc(userId, type);

        if (skillId != null) {
            Set<UUID> keep = skillLinks.findBySkillId(skillId).stream()
                    .map(SkillEvidence::getEvidenceId).collect(Collectors.toSet());
            rows = rows.stream().filter(e -> keep.contains(e.getId())).toList();
        }
        if (projectId != null) {
            Set<UUID> keep = projectLinks.findByProjectId(projectId).stream()
                    .map(ProjectEvidence::getEvidenceId).collect(Collectors.toSet());
            rows = rows.stream().filter(e -> keep.contains(e.getId())).toList();
        }
        return rows.stream()
                .map(e -> new EvidenceWithLinks(e, skillLinks.findByEvidenceId(e.getId()),
                        projectLinks.findByEvidenceId(e.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public EvidenceWithLinks getMine(UUID id) {
        Evidence e = evidence.findByIdAndUserIdAndDeletedAtIsNull(id, currentUser.currentUserId())
                .orElseThrow(() -> ApiException.notFound("Evidence not found."));
        return new EvidenceWithLinks(e, skillLinks.findByEvidenceId(id), projectLinks.findByEvidenceId(id));
    }

    /** Evidence for one skill, scoped to the caller — drives {@code GET /api/v1/skills/{id}/evidence}. */
    @Transactional(readOnly = true)
    public List<EvidenceWithLinks> forSkill(UUID skillId) {
        UUID userId = currentUser.currentUserId();
        Set<UUID> evidenceIds = skillLinks.findBySkillId(skillId).stream()
                .map(SkillEvidence::getEvidenceId).collect(Collectors.toSet());
        if (evidenceIds.isEmpty()) {
            return List.of();
        }
        return evidence.findByUserIdAndDeletedAtIsNullOrderByOccurredAtDescCreatedAtDesc(userId).stream()
                .filter(e -> evidenceIds.contains(e.getId()))
                .map(e -> new EvidenceWithLinks(e, skillLinks.findByEvidenceId(e.getId()),
                        projectLinks.findByEvidenceId(e.getId())))
                .toList();
    }

    @Transactional
    public EvidenceWithLinks create(CreateEvidenceRequest req) {
        UUID userId = currentUser.getOrProvision().getId();

        Evidence saved = evidence.save(Evidence.create(
                userId, EvidenceType.fromValue(req.type()), req.title().strip(), strip(req.description()),
                strip(req.sourceUrl()), strip(req.externalIdentifier()), req.occurredAt(), req.metadata()));

        replaceSkillLinks(saved.getId(), req.skills());
        replaceProjectLinks(userId, saved.getId(), req.projects());

        log.info("event=evidence.recorded user_id={} evidence_id={} type={} skills={} projects={}",
                userId, saved.getId(), req.type(),
                req.skills() == null ? 0 : req.skills().size(),
                req.projects() == null ? 0 : req.projects().size());
        return getMine(saved.getId());
    }

    @Transactional
    public EvidenceWithLinks update(UUID id, UpdateEvidenceRequest req) {
        Evidence e = getMine(id).evidence();
        if (req.type() != null) {
            e.setType(EvidenceType.fromValue(req.type()));
        }
        if (req.title() != null && !req.title().isBlank()) {
            e.setTitle(req.title().strip());
        }
        if (req.description() != null) {
            e.setDescription(strip(req.description()));
        }
        if (req.sourceUrl() != null) {
            e.setSourceUrl(strip(req.sourceUrl()));
        }
        if (req.externalIdentifier() != null) {
            e.setExternalIdentifier(strip(req.externalIdentifier()));
        }
        if (req.occurredAt() != null) {
            e.setOccurredAt(req.occurredAt());
        }
        if (req.metadata() != null) {
            e.setMetadata(req.metadata());
        }
        evidence.save(e);

        if (req.skills() != null) {
            replaceSkillLinks(id, req.skills());
        }
        if (req.projects() != null) {
            replaceProjectLinks(e.getUserId(), id, req.projects());
        }
        return getMine(id);
    }

    @Transactional
    public void delete(UUID id) {
        Evidence e = getMine(id).evidence();
        e.softDelete();
        evidence.save(e);
    }

    private void replaceSkillLinks(UUID evidenceId, List<EvidenceSkillInput> inputs) {
        skillLinks.deleteByEvidenceId(evidenceId);
        skillLinks.flush();
        if (inputs == null) {
            return;
        }
        for (EvidenceSkillInput in : inputs) {
            if (!skillCatalog.exists(in.skillId())) {
                throw ApiException.notFound("Skill not found: " + in.skillId());
            }
            skillLinks.save(SkillEvidence.of(in.skillId(), evidenceId,
                    in.contributionWeight() == null ? null : BigDecimal.valueOf(in.contributionWeight())));
        }
    }

    private void replaceProjectLinks(UUID userId, UUID evidenceId, List<EvidenceProjectInput> inputs) {
        projectLinks.deleteByEvidenceId(evidenceId);
        projectLinks.flush();
        if (inputs == null) {
            return;
        }
        for (EvidenceProjectInput in : inputs) {
            if (!projects.existsForUser(in.projectId(), userId)) {
                throw ApiException.notFound("Project not found: " + in.projectId());
            }
            projectLinks.save(ProjectEvidence.of(in.projectId(), evidenceId));
        }
    }

    private static String strip(String s) {
        return s == null ? null : s.strip();
    }
}
