-- V1: Create users table
-- This is the initial migration for ORBIT, creating only the users table.
-- Additional tables will be added in subsequent milestones.

CREATE TABLE IF NOT EXISTS orbit.users (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email           VARCHAR(255) UNIQUE NOT NULL,
    password_hash   VARCHAR(255) NOT NULL,
    display_name    VARCHAR(200),
    first_name      VARCHAR(100),
    last_name       VARCHAR(100),
    status          VARCHAR(50) NOT NULL DEFAULT 'active',
    avatar_url      VARCHAR(500),
    preferences     JSONB DEFAULT '{}',
    onboarding_completed BOOLEAN DEFAULT FALSE,
    last_login_at   TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMPTZ
);

-- Index for soft delete queries
CREATE INDEX idx_users_email ON orbit.users(email) WHERE deleted_at IS NULL;
CREATE INDEX idx_users_status ON orbit.users(status) WHERE deleted_at IS NULL;

-- Comment
COMMENT ON TABLE orbit.users IS 'Core user identity table for ORBIT platform';
