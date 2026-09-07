-- V3: Milestone 2 domain — flesh out objectives, add milestones, tasks, and
-- daily plans. Blueprint §9 (objectives, milestones), §10 (tasks, daily_plans,
-- daily_plan_tasks). Status vocabularies + progress rollup: ADR-016. Priority
-- scoring columns: ADR-014. Daily-plan model: ADR-015.

-- ---------------------------------------------------------------------------
-- orbit.objectives — extend the M1 minimal table to Blueprint §9.
-- ---------------------------------------------------------------------------
ALTER TABLE orbit.objectives ADD COLUMN career_target_id UUID;          -- nullable; FK added when career_targets exists (M3)
ALTER TABLE orbit.objectives ADD COLUMN priority INTEGER      NOT NULL DEFAULT 3;   -- 1..5, 5 = highest (ADR-016 §2)
ALTER TABLE orbit.objectives ADD COLUMN progress NUMERIC(4,3)  NOT NULL DEFAULT 0;  -- 0..1, DERIVED (ADR-016 §3)

ALTER TABLE orbit.objectives ADD CONSTRAINT ck_objectives_priority CHECK (priority BETWEEN 1 AND 5);
ALTER TABLE orbit.objectives ADD CONSTRAINT ck_objectives_progress CHECK (progress >= 0 AND progress <= 1);

COMMENT ON COLUMN orbit.objectives.progress IS 'Derived from milestone/task completion — never set directly via the API (ADR-016).';

-- ---------------------------------------------------------------------------
-- orbit.milestones — Blueprint §9. Owner = the objective's owner (no user_id).
-- ---------------------------------------------------------------------------
CREATE TABLE orbit.milestones (
    id          UUID         PRIMARY KEY,
    objective_id UUID        NOT NULL REFERENCES orbit.objectives (id) ON DELETE CASCADE,
    title       VARCHAR(200) NOT NULL,
    description TEXT,
    target_date DATE,
    status      VARCHAR(20)  NOT NULL DEFAULT 'pending',
    sort_order  INTEGER      NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    deleted_at  TIMESTAMPTZ,
    CONSTRAINT ck_milestones_status CHECK (status IN ('pending', 'in_progress', 'completed'))
);

CREATE INDEX idx_milestones_objective ON orbit.milestones (objective_id) WHERE deleted_at IS NULL;

-- ---------------------------------------------------------------------------
-- orbit.tasks — Blueprint §10 (M2 subset: objective + milestone links;
-- project_id / skill_id land with their domains in M3+).
-- ---------------------------------------------------------------------------
CREATE TABLE orbit.tasks (
    id                UUID          PRIMARY KEY,
    user_id           UUID          NOT NULL REFERENCES orbit.users (id) ON DELETE CASCADE,
    objective_id      UUID          REFERENCES orbit.objectives (id) ON DELETE CASCADE,   -- nullable per §10; required by the M2 API
    milestone_id      UUID          REFERENCES orbit.milestones (id) ON DELETE SET NULL,
    title             VARCHAR(200)  NOT NULL,
    description       TEXT,
    estimated_minutes INTEGER,
    priority_score    NUMERIC(6,4),
    priority_reason   JSONB,
    status            VARCHAR(20)   NOT NULL DEFAULT 'todo',
    due_at            TIMESTAMPTZ,
    completed_at      TIMESTAMPTZ,
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    deleted_at        TIMESTAMPTZ,
    CONSTRAINT ck_tasks_status CHECK (status IN ('todo', 'in_progress', 'blocked', 'done', 'skipped')),
    CONSTRAINT ck_tasks_estimated_minutes CHECK (estimated_minutes IS NULL OR estimated_minutes > 0)
);

CREATE INDEX idx_tasks_user       ON orbit.tasks (user_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_tasks_objective  ON orbit.tasks (objective_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_tasks_milestone  ON orbit.tasks (milestone_id) WHERE deleted_at IS NULL;

-- ---------------------------------------------------------------------------
-- orbit.daily_plans + orbit.daily_plan_tasks — Blueprint §10, ADR-015.
-- ---------------------------------------------------------------------------
CREATE TABLE orbit.daily_plans (
    id                UUID         PRIMARY KEY,
    user_id           UUID         NOT NULL REFERENCES orbit.users (id) ON DELETE CASCADE,
    plan_date         DATE         NOT NULL,
    available_minutes INTEGER      NOT NULL,
    status            VARCHAR(20)  NOT NULL DEFAULT 'draft',
    generated_by      VARCHAR(20)  NOT NULL DEFAULT 'system',
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_daily_plans_user_date UNIQUE (user_id, plan_date),
    CONSTRAINT ck_daily_plans_status CHECK (status IN ('draft', 'active', 'completed')),
    CONSTRAINT ck_daily_plans_generated_by CHECK (generated_by IN ('system', 'user')),
    CONSTRAINT ck_daily_plans_minutes CHECK (available_minutes > 0)
);

CREATE TABLE orbit.daily_plan_tasks (
    daily_plan_id     UUID    NOT NULL REFERENCES orbit.daily_plans (id) ON DELETE CASCADE,
    task_id           UUID    NOT NULL REFERENCES orbit.tasks (id) ON DELETE CASCADE,
    position          INTEGER NOT NULL,
    allocated_minutes INTEGER NOT NULL,
    PRIMARY KEY (daily_plan_id, task_id),
    CONSTRAINT ck_daily_plan_tasks_alloc CHECK (allocated_minutes > 0)
);

CREATE INDEX idx_daily_plan_tasks_task ON orbit.daily_plan_tasks (task_id);
