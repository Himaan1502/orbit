-- V2: Align the identity tables with ADR-004 (OIDC) / ADR-012, and add the
-- first ownership-checked domain resource.
--
-- V1 is immutable (ADR-009) and shipped `password_hash NOT NULL` on orbit.users
-- under the superseded JWT ADR-004. Under OIDC the API performs no password
-- verification (ADR-004 §1) — credentials live in auth.users (apps/auth). This
-- migration removes the column and adds the profile + a minimal objectives
-- table (subset of Blueprint §9; NOT the Objective Engine).

-- ---------------------------------------------------------------------------
-- orbit.users — drop the credential column; the row is now a profile keyed by
-- the OIDC `sub` (== auth.users.id). Rows are provisioned just-in-time on the
-- user's first authenticated request (ADR-012 §3).
-- ---------------------------------------------------------------------------
ALTER TABLE orbit.users DROP COLUMN IF EXISTS password_hash;
ALTER TABLE orbit.users ALTER COLUMN id DROP DEFAULT;   -- id now supplied by the app (= sub)

COMMENT ON TABLE orbit.users IS
    'ORBIT domain profile. id == OIDC sub == auth.users.id. No credentials (see ADR-012).';

-- ---------------------------------------------------------------------------
-- orbit.user_profiles — Blueprint §7.2.
-- ---------------------------------------------------------------------------
CREATE TABLE orbit.user_profiles (
    user_id                UUID        PRIMARY KEY REFERENCES orbit.users (id) ON DELETE CASCADE,
    timezone               VARCHAR(64) NOT NULL DEFAULT 'UTC',
    locale                 VARCHAR(16),
    available_daily_minutes INTEGER,
    preferences            JSONB       NOT NULL DEFAULT '{}',
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT now()
);

COMMENT ON TABLE orbit.user_profiles IS 'Per-user preferences and planning context (Blueprint §7.2).';

-- ---------------------------------------------------------------------------
-- orbit.objectives — minimal subset of Blueprint §9. Exists in Milestone 1
-- purely to prove server-side resource-ownership enforcement (Tech Arch §9,
-- Blueprint §35). The Objective Engine is a later milestone.
-- ---------------------------------------------------------------------------
CREATE TABLE orbit.objectives (
    id          UUID         PRIMARY KEY,
    user_id     UUID         NOT NULL REFERENCES orbit.users (id) ON DELETE CASCADE,
    title       VARCHAR(200) NOT NULL,
    description TEXT,
    status      VARCHAR(20)  NOT NULL DEFAULT 'active',
    target_date DATE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    deleted_at  TIMESTAMPTZ,
    CONSTRAINT ck_objectives_status CHECK (status IN ('active', 'completed', 'archived'))
);

CREATE INDEX idx_objectives_user ON orbit.objectives (user_id) WHERE deleted_at IS NULL;
