package com.orbit.platform.evidence.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/** Links an evidence item to a project it came out of (Blueprint §13). */
@Entity
@Table(name = "project_evidence", schema = "orbit")
@IdClass(ProjectEvidence.Key.class)
@Getter
@NoArgsConstructor
public class ProjectEvidence {

    @Id
    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @Id
    @Column(name = "evidence_id", nullable = false, updatable = false)
    private UUID evidenceId;

    public static ProjectEvidence of(UUID projectId, UUID evidenceId) {
        ProjectEvidence p = new ProjectEvidence();
        p.projectId = projectId;
        p.evidenceId = evidenceId;
        return p;
    }

    public static final class Key implements Serializable {
        private UUID projectId;
        private UUID evidenceId;

        public Key() {
        }

        public Key(UUID projectId, UUID evidenceId) {
            this.projectId = projectId;
            this.evidenceId = evidenceId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Key k)) {
                return false;
            }
            return Objects.equals(projectId, k.projectId) && Objects.equals(evidenceId, k.evidenceId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(projectId, evidenceId);
        }
    }
}
