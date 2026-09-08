-- V8: Milestone 5 — Projects & Evidence. Blueprint §14 (projects, project_skills),
-- §13 (evidence, skill_evidence, project_evidence). Projects domain: ADR-024.
-- Evidence domain: ADR-025. Evidence-backed confidence + readiness + the final
-- Priority Engine factor swap: ADR-026.
--
-- Both domains are PER-USER. No seed data.

-- ---------------------------------------------------------------------------
-- orbit.projects — "execution vehicles for building evidence" (UX §22). Per-user,
-- soft-deletable (BaseEntity shape). Optionally linked to one objective, whose
-- milestones the project reuses (ADR-024 §3 — no project_milestones table).
-- ---------------------------------------------------------------------------
CREATE TABLE orbit.projects (
    id             UUID         PRIMARY KEY,
    user_id        UUID         NOT NULL REFERENCES orbit.users (id) ON DELETE CASCADE,
    objective_id   UUID         REFERENCES orbit.objectives (id) ON DELETE SET NULL,
    name           VARCHAR(160) NOT NULL,
    description    TEXT,
    status         VARCHAR(20)  NOT NULL DEFAULT 'planned',
    repository_url TEXT,
    demo_url       TEXT,
    started_at     TIMESTAMPTZ,
    completed_at   TIMESTAMPTZ,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    deleted_at     TIMESTAMPTZ,
    CONSTRAINT ck_projects_status CHECK (status IN ('planned', 'active', 'completed', 'archived'))
);

CREATE INDEX idx_projects_user ON orbit.projects (user_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_projects_objective ON orbit.projects (objective_id) WHERE deleted_at IS NULL;

COMMENT ON COLUMN orbit.projects.status IS
    '"completed" is user-settable — that is the self-reported completion the PRD keeps distinct from evidence.';

-- ---------------------------------------------------------------------------
-- orbit.project_skills — Blueprint §14. expected = the user''s claim;
-- demonstrated = DERIVED (0 until the project is completed AND evidenced) — ADR-026 §3.
-- ---------------------------------------------------------------------------
CREATE TABLE orbit.project_skills (
    project_id                 UUID         NOT NULL REFERENCES orbit.projects (id) ON DELETE CASCADE,
    skill_id                   UUID         NOT NULL REFERENCES orbit.skills (id)   ON DELETE CASCADE,
    expected_contribution      NUMERIC(3,2) NOT NULL DEFAULT 0.5,
    demonstrated_contribution  NUMERIC(3,2) NOT NULL DEFAULT 0,
    PRIMARY KEY (project_id, skill_id),
    CONSTRAINT ck_project_skills_expected CHECK (expected_contribution BETWEEN 0 AND 1),
    CONSTRAINT ck_project_skills_demonstrated CHECK (demonstrated_contribution BETWEEN 0 AND 1)
);

CREATE INDEX idx_project_skills_skill ON orbit.project_skills (skill_id);

COMMENT ON COLUMN orbit.project_skills.demonstrated_contribution IS
    'Derived (ADR-026 §3) — never set from a request body.';

-- ---------------------------------------------------------------------------
-- orbit.tasks.project_id — V3 reserved this ("project_id / skill_id land with
-- their domains in M3+"). skill_id landed in V4; project_id lands now.
-- ---------------------------------------------------------------------------
ALTER TABLE orbit.tasks ADD COLUMN project_id UUID REFERENCES orbit.projects (id) ON DELETE SET NULL;
CREATE INDEX idx_tasks_project ON orbit.tasks (project_id) WHERE deleted_at IS NULL;

-- ---------------------------------------------------------------------------
-- orbit.evidence — Blueprint §13. Per-user, soft-deletable. Creating evidence
-- NEVER writes to skill_assessments (ADR-025 §5). verification_status is
-- 'self_attested' for every M5 row — no external verification yet (ADR-025 §3).
-- ---------------------------------------------------------------------------
CREATE TABLE orbit.evidence (
    id                  UUID         PRIMARY KEY,
    user_id             UUID         NOT NULL REFERENCES orbit.users (id) ON DELETE CASCADE,
    type                VARCHAR(32)  NOT NULL,
    title               VARCHAR(200) NOT NULL,
    description         TEXT,
    source_url          TEXT,
    external_identifier VARCHAR(120),
    verification_status VARCHAR(20)  NOT NULL DEFAULT 'self_attested',
    metadata            JSONB        NOT NULL DEFAULT '{}'::jsonb,
    occurred_at         TIMESTAMPTZ,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    deleted_at          TIMESTAMPTZ,
    CONSTRAINT ck_evidence_type CHECK (type IN (
        'project', 'repository', 'deployment', 'benchmark', 'document',
        'certification', 'assessment', 'talk', 'other')),
    CONSTRAINT ck_evidence_verification CHECK (verification_status IN (
        'self_attested', 'pending', 'verified', 'disputed'))
);

CREATE INDEX idx_evidence_user ON orbit.evidence (user_id, occurred_at DESC) WHERE deleted_at IS NULL;
CREATE INDEX idx_evidence_type ON orbit.evidence (user_id, type) WHERE deleted_at IS NULL;

-- ---------------------------------------------------------------------------
-- orbit.skill_evidence / orbit.project_evidence — Blueprint §13 verbatim.
-- ---------------------------------------------------------------------------
CREATE TABLE orbit.skill_evidence (
    skill_id            UUID         NOT NULL REFERENCES orbit.skills (id)   ON DELETE CASCADE,
    evidence_id         UUID         NOT NULL REFERENCES orbit.evidence (id) ON DELETE CASCADE,
    contribution_weight NUMERIC(3,2) NOT NULL DEFAULT 0.5,
    PRIMARY KEY (skill_id, evidence_id),
    CONSTRAINT ck_skill_evidence_weight CHECK (contribution_weight BETWEEN 0 AND 1)
);

CREATE INDEX idx_skill_evidence_evidence ON orbit.skill_evidence (evidence_id);

CREATE TABLE orbit.project_evidence (
    project_id  UUID NOT NULL REFERENCES orbit.projects (id)  ON DELETE CASCADE,
    evidence_id UUID NOT NULL REFERENCES orbit.evidence (id)  ON DELETE CASCADE,
    PRIMARY KEY (project_id, evidence_id)
);

CREATE INDEX idx_project_evidence_evidence ON orbit.project_evidence (evidence_id);
