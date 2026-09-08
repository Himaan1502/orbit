package com.orbit.platform.evidence.repository;

import com.orbit.platform.evidence.model.ProjectEvidence;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ProjectEvidenceRepository extends JpaRepository<ProjectEvidence, ProjectEvidence.Key> {

    List<ProjectEvidence> findByEvidenceId(UUID evidenceId);

    List<ProjectEvidence> findByProjectId(UUID projectId);

    void deleteByEvidenceId(UUID evidenceId);

    long countByProjectId(UUID projectId);
}
