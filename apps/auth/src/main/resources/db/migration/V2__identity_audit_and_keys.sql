-- V2: ORBIT identity, auth audit log, and signing-key store. See ADR-011, ADR-012.

-- ---------------------------------------------------------------------------
-- auth.users — the credential + account store. Owned by apps/auth. apps/api
-- keeps its own orbit.users profile row keyed by the SAME id (the OIDC `sub`).
-- ---------------------------------------------------------------------------
CREATE TABLE users (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    email         VARCHAR(254) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,             -- BCrypt, strength 12
    display_name  VARCHAR(100),
    status        VARCHAR(20)  NOT NULL DEFAULT 'active',  -- active | disabled
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_auth_users_email UNIQUE (email),
    CONSTRAINT ck_auth_users_email_lower CHECK (email = lower(email)),
    CONSTRAINT ck_auth_users_status CHECK (status IN ('active', 'disabled'))
);

COMMENT ON TABLE users IS
    'ORBIT identity / credential store (apps/auth). id == OIDC sub == orbit.users.id.';

-- ---------------------------------------------------------------------------
-- auth.auth_events — append-only audit trail for authentication-sensitive
-- actions (Blueprint §35 "login audit events", PRD §11 "audit logs").
-- ---------------------------------------------------------------------------
CREATE TABLE auth_events (
    id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID        NULL REFERENCES users (id) ON DELETE SET NULL,
    email      VARCHAR(254),
    event_type VARCHAR(40) NOT NULL,   -- REGISTERED | LOGIN_SUCCESS | LOGIN_FAILURE | LOGOUT | RATE_LIMITED
    ip         VARCHAR(45),
    user_agent VARCHAR(400),
    detail     VARCHAR(200),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_auth_events_email      ON auth_events (email, created_at DESC);
CREATE INDEX idx_auth_events_user_id    ON auth_events (user_id, created_at DESC);
CREATE INDEX idx_auth_events_type_time  ON auth_events (event_type, created_at DESC);

-- ---------------------------------------------------------------------------
-- auth.signing_keys — persistent RSA signing keys. The private key is stored
-- AES-256-GCM encrypted with a key derived from $AUTH_KEYSTORE_SECRET. Multiple
-- rows support rotation: JWKS publishes every non-retired public key; exactly
-- one row is `active` and signs. See ADR-011 §3.
-- ---------------------------------------------------------------------------
CREATE TABLE signing_keys (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    key_id      VARCHAR(64) NOT NULL,                 -- JWK "kid"
    algorithm   VARCHAR(10) NOT NULL DEFAULT 'RS256',
    public_key  TEXT        NOT NULL,                 -- X.509 SPKI, base64
    private_key TEXT        NOT NULL,                 -- PKCS#8, AES-GCM sealed, base64
    active      BOOLEAN     NOT NULL DEFAULT true,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    rotated_at  TIMESTAMPTZ NULL,
    CONSTRAINT uq_signing_keys_kid UNIQUE (key_id)
);

-- At most one active signing key.
CREATE UNIQUE INDEX uq_signing_keys_single_active ON signing_keys (active) WHERE active;
