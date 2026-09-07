-- V4: Milestone 3 — Skills & Career. Blueprint §8 (career_targets,
-- career_target_skills), §11 (skills), §12 (skill_dependencies, skill_assessments).
-- Skill scoring: ADR-017. Readiness: ADR-018. Dependency graph: ADR-019.

-- ---------------------------------------------------------------------------
-- orbit.skills — the global canonical skill catalog (Blueprint §11). No user_id.
-- Populated by the seed migration (V5, ADR-020); read-only to end users in M3.
-- ---------------------------------------------------------------------------
CREATE TABLE orbit.skills (
    id             UUID         PRIMARY KEY,
    canonical_name VARCHAR(120) NOT NULL,
    description    TEXT,
    category       VARCHAR(60),
    status         VARCHAR(20)  NOT NULL DEFAULT 'active',
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_skills_canonical_name UNIQUE (canonical_name),
    CONSTRAINT ck_skills_status CHECK (status IN ('active', 'deprecated'))
);

-- ---------------------------------------------------------------------------
-- orbit.skill_dependencies — directed prerequisite edges (Blueprint §12).
-- Global (part of the catalog). Acyclic — enforced by SkillGraphValidator at
-- insert time (ADR-019); the seed is asserted acyclic by a test.
-- ---------------------------------------------------------------------------
CREATE TABLE orbit.skill_dependencies (
    prerequisite_skill_id UUID         NOT NULL REFERENCES orbit.skills (id) ON DELETE CASCADE,
    dependent_skill_id    UUID         NOT NULL REFERENCES orbit.skills (id) ON DELETE CASCADE,
    dependency_strength   NUMERIC(3,2) NOT NULL DEFAULT 0.5,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (prerequisite_skill_id, dependent_skill_id),
    CONSTRAINT ck_skill_dep_no_self_loop CHECK (prerequisite_skill_id <> dependent_skill_id),
    CONSTRAINT ck_skill_dep_strength CHECK (dependency_strength >= 0 AND dependency_strength <= 1)
);

CREATE INDEX idx_skill_dependencies_dependent ON orbit.skill_dependencies (dependent_skill_id);

-- ---------------------------------------------------------------------------
-- orbit.skill_assessments — per-user, append-only (Blueprint §12). Dimensions
-- and overall are 0..1 (ADR-017 §1); overall is computed on insert.
-- ---------------------------------------------------------------------------
CREATE TABLE orbit.skill_assessments (
    id                UUID         PRIMARY KEY,
    user_id           UUID         NOT NULL REFERENCES orbit.users (id) ON DELETE CASCADE,
    skill_id          UUID         NOT NULL REFERENCES orbit.skills (id) ON DELETE CASCADE,
    knowledge_score   NUMERIC(4,3) NOT NULL,
    practical_score   NUMERIC(4,3) NOT NULL,
    evidence_score    NUMERIC(4,3) NOT NULL,
    interview_score   NUMERIC(4,3) NOT NULL,
    production_score  NUMERIC(4,3) NOT NULL,
    overall_score     NUMERIC(4,3) NOT NULL,
    assessment_source VARCHAR(20)  NOT NULL DEFAULT 'self',
    assessed_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_skill_assess_source CHECK (assessment_source IN ('self', 'evidence', 'interview', 'system')),
    CONSTRAINT ck_skill_assess_range CHECK (
        knowledge_score  BETWEEN 0 AND 1 AND practical_score  BETWEEN 0 AND 1 AND
        evidence_score   BETWEEN 0 AND 1 AND interview_score  BETWEEN 0 AND 1 AND
        production_score BETWEEN 0 AND 1 AND overall_score    BETWEEN 0 AND 1)
);

CREATE INDEX idx_skill_assessments_latest ON orbit.skill_assessments (user_id, skill_id, assessed_at DESC);

-- ---------------------------------------------------------------------------
-- orbit.career_targets + orbit.career_target_skills — per-user (Blueprint §8).
-- ---------------------------------------------------------------------------
CREATE TABLE orbit.career_targets (
    id               UUID         PRIMARY KEY,
    user_id          UUID         NOT NULL REFERENCES orbit.users (id) ON DELETE CASCADE,
    name             VARCHAR(160) NOT NULL,
    role_family      VARCHAR(80),
    specialization   VARCHAR(80),
    target_readiness NUMERIC(4,3),
    target_date      DATE,
    priority         INTEGER      NOT NULL DEFAULT 3,
    status           VARCHAR(20)  NOT NULL DEFAULT 'active',
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    deleted_at       TIMESTAMPTZ,
    CONSTRAINT ck_career_targets_priority CHECK (priority BETWEEN 1 AND 5),
    CONSTRAINT ck_career_targets_status CHECK (status IN ('active', 'archived')),
    CONSTRAINT ck_career_targets_readiness CHECK (target_readiness IS NULL OR target_readiness BETWEEN 0 AND 1)
);

CREATE INDEX idx_career_targets_user ON orbit.career_targets (user_id) WHERE deleted_at IS NULL;

CREATE TABLE orbit.career_target_skills (
    career_target_id  UUID         NOT NULL REFERENCES orbit.career_targets (id) ON DELETE CASCADE,
    skill_id          UUID         NOT NULL REFERENCES orbit.skills (id) ON DELETE CASCADE,
    importance        NUMERIC(3,2) NOT NULL DEFAULT 0.5,
    interview_weight  NUMERIC(3,2) NOT NULL DEFAULT 0.5,
    production_weight NUMERIC(3,2) NOT NULL DEFAULT 0.5,
    PRIMARY KEY (career_target_id, skill_id),
    CONSTRAINT ck_cts_importance CHECK (importance BETWEEN 0 AND 1),
    CONSTRAINT ck_cts_interview_weight CHECK (interview_weight BETWEEN 0 AND 1),
    CONSTRAINT ck_cts_production_weight CHECK (production_weight BETWEEN 0 AND 1)
);

-- ---------------------------------------------------------------------------
-- Wire the new domains into the existing schema.
-- ---------------------------------------------------------------------------
ALTER TABLE orbit.objectives
    ADD CONSTRAINT fk_objectives_career_target
    FOREIGN KEY (career_target_id) REFERENCES orbit.career_targets (id) ON DELETE SET NULL;

ALTER TABLE orbit.tasks
    ADD COLUMN skill_id UUID REFERENCES orbit.skills (id) ON DELETE SET NULL;

CREATE INDEX idx_tasks_skill ON orbit.tasks (skill_id) WHERE deleted_at IS NULL;
