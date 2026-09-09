-- V10: Milestone 7 — OSCAR.
--   ai domain schema ......... ADR-032 §1  (ai_conversations, ai_messages)
--   provider abstraction ..... ADR-031     (no schema — env keys + per-message metadata columns)
--
-- Blueprint §18 verbatim. Both tables are PER-USER. No seed data.
--
-- What OSCAR persists (Blueprint §18 "never persist unnecessary sensitive
-- context", ADR-032 §1): the user's message text and OSCAR's FINAL assistant
-- text only. NOT the assembled context bundle, NOT intermediate tool-call
-- results, NOT raw provider payloads. ai_messages is append-only — no UPDATE,
-- no soft-delete column; a conversation delete cascades.

-- ---------------------------------------------------------------------------
-- orbit.ai_conversations — one chat thread. title is the first user message,
-- trimmed; the user can rename it (PATCH). updated_at moves on each new turn so
-- the list can order by recency.
-- ---------------------------------------------------------------------------
CREATE TABLE orbit.ai_conversations (
    id         UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID         NOT NULL REFERENCES orbit.users (id) ON DELETE CASCADE,
    title      VARCHAR(160),
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_ai_conversations_user ON orbit.ai_conversations (user_id, updated_at DESC);

COMMENT ON TABLE orbit.ai_conversations IS
    'Blueprint §18 — OSCAR chat threads. Ownership is (user_id); cross-user access is structurally impossible (ADR-032).';

-- ---------------------------------------------------------------------------
-- orbit.ai_messages — append-only turn log. role is 'user' or 'assistant'.
-- model_provider / model_name / prompt_version / token_usage are NULL on user
-- rows and populated from AiResponse on assistant rows (ADR-031 §5). token_usage
-- is {promptTokens, completionTokens} — stored for future cost controls
-- (Blueprint §28); nothing acts on it in M7.
-- ---------------------------------------------------------------------------
CREATE TABLE orbit.ai_messages (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id UUID        NOT NULL REFERENCES orbit.ai_conversations (id) ON DELETE CASCADE,
    role            VARCHAR(16) NOT NULL,
    content         TEXT        NOT NULL,
    model_provider  VARCHAR(24),
    model_name      VARCHAR(60),
    prompt_version  VARCHAR(24),
    token_usage     JSONB,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_ai_messages_role CHECK (role IN ('user', 'assistant'))
);

CREATE INDEX idx_ai_messages_conversation ON orbit.ai_messages (conversation_id, created_at);

COMMENT ON TABLE orbit.ai_messages IS
    'Blueprint §18 — append-only. Persists the user message + OSCAR final text only; never the context bundle or tool results (ADR-032 §1).';
