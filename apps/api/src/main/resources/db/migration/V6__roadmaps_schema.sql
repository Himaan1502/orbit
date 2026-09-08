-- V6: Milestone 4 — Roadmaps. Blueprint §11 (roadmaps, roadmap_topics,
-- roadmap_topic_skills). Schema, priority tier, no topic-dependency table:
-- ADR-021. Ingestion (seeded in V7): ADR-022. Derived progress + intersection:
-- ADR-023.
--
-- The roadmap catalog is GLOBAL (like orbit.skills) — no user_id, not
-- soft-deletable, read-only to end users. Only orbit.user_roadmaps is per-user.

-- ---------------------------------------------------------------------------
-- orbit.roadmaps — the global roadmap catalog (Blueprint §11 + priority_tier).
-- ---------------------------------------------------------------------------
CREATE TABLE orbit.roadmaps (
    id                  UUID         PRIMARY KEY,
    name                VARCHAR(120) NOT NULL,
    external_identifier VARCHAR(80)  NOT NULL,          -- roadmap.sh slug, e.g. 'mlops'
    source_name         VARCHAR(80)  NOT NULL,          -- 'roadmap.sh'
    source_url          TEXT         NOT NULL,          -- 'https://roadmap.sh/mlops'
    description         TEXT,                            -- one-line summary ORBIT authors (ADR-022 §3)
    priority_tier       INTEGER      NOT NULL,          -- 1 | 2 | 3  (P1/P2/P3) — surfacing only (ADR-021 §6)
    status              VARCHAR(20)  NOT NULL DEFAULT 'active',
    last_verified_at    TIMESTAMPTZ,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_roadmaps_external_identifier UNIQUE (external_identifier),
    CONSTRAINT ck_roadmaps_priority_tier CHECK (priority_tier IN (1, 2, 3)),
    CONSTRAINT ck_roadmaps_status CHECK (status IN ('active', 'archived'))
);

COMMENT ON COLUMN orbit.roadmaps.priority_tier IS
    'P1/P2/P3 — informs UI surfacing/ordering only. NOT a Priority Engine input (ADR-021 §6, ADR-014 frozen).';
COMMENT ON COLUMN orbit.roadmaps.description IS
    'One-line summary authored by ORBIT. No third-party roadmap prose (PRD Non-Goal, Blueprint §25).';

-- ---------------------------------------------------------------------------
-- orbit.roadmap_topics — normalized topic tree (Blueprint §11). Self-referential.
-- ---------------------------------------------------------------------------
CREATE TABLE orbit.roadmap_topics (
    id                  UUID         PRIMARY KEY,
    roadmap_id          UUID         NOT NULL REFERENCES orbit.roadmaps (id) ON DELETE CASCADE,
    parent_topic_id     UUID         REFERENCES orbit.roadmap_topics (id) ON DELETE CASCADE,
    name                VARCHAR(120) NOT NULL,          -- short, factual, ORBIT-authored label
    external_identifier VARCHAR(120),                    -- roadmap.sh node id when known
    source_url          TEXT,
    sort_order          INTEGER      NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_roadmap_topic_not_own_parent CHECK (parent_topic_id IS NULL OR parent_topic_id <> id),
    CONSTRAINT uq_roadmap_topic_ext UNIQUE (roadmap_id, external_identifier)
);

CREATE INDEX idx_roadmap_topics_roadmap_order ON orbit.roadmap_topics (roadmap_id, sort_order);
CREATE INDEX idx_roadmap_topics_parent ON orbit.roadmap_topics (parent_topic_id);

-- ---------------------------------------------------------------------------
-- orbit.roadmap_topic_skills — topic → canonical skill (Blueprint §11).
-- One skill may map to many topics across many roadmaps.
-- ---------------------------------------------------------------------------
CREATE TABLE orbit.roadmap_topic_skills (
    roadmap_topic_id UUID         NOT NULL REFERENCES orbit.roadmap_topics (id) ON DELETE CASCADE,
    skill_id         UUID         NOT NULL REFERENCES orbit.skills (id) ON DELETE CASCADE,
    relevance        NUMERIC(3,2)  NOT NULL DEFAULT 0.5,
    PRIMARY KEY (roadmap_topic_id, skill_id),
    CONSTRAINT ck_roadmap_topic_skill_relevance CHECK (relevance >= 0 AND relevance <= 1)
);

-- The intersection query and the Skill -> roadmaps reverse lookup both start from a skill.
CREATE INDEX idx_roadmap_topic_skills_skill ON orbit.roadmap_topic_skills (skill_id);

-- ---------------------------------------------------------------------------
-- orbit.user_roadmaps — minimal per-user adoption (ADR-021 §5). Progress is
-- DERIVED (ADR-023), never stored here. Ownership is structural (pair PK).
-- ---------------------------------------------------------------------------
CREATE TABLE orbit.user_roadmaps (
    user_id     UUID         NOT NULL REFERENCES orbit.users (id) ON DELETE CASCADE,
    roadmap_id  UUID         NOT NULL REFERENCES orbit.roadmaps (id) ON DELETE CASCADE,
    status      VARCHAR(20)  NOT NULL DEFAULT 'active',
    started_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    target_date DATE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, roadmap_id),
    CONSTRAINT ck_user_roadmaps_status CHECK (status IN ('active', 'paused'))
);

CREATE INDEX idx_user_roadmaps_roadmap ON orbit.user_roadmaps (roadmap_id);
