# ADR-032: OSCAR Domain — Conversations, Context Builder, Tool Registry, Confirmation-Gated Mutations

## Status
Accepted — approved by the product owner 2026-09-09 (all five sign-off items).
Item 3 (OSCAR never writes — mutation tools only ever produce a `ProposedAction`
executed through the existing endpoint) is held exactly as designed. Milestone 7
(our numbering; Blueprint "Milestone 9 — OSCAR").

## Date
2026-09-09

## Context

Blueprint §26 (OSCAR Architecture — orchestration, not a DB chatbot), §27 (Tool
Registry), §18 (`ai_conversations` / `ai_messages` schema), §37 (AI Security
Rules). Tech Arch §12 (context layers, tool examples, *"Never allow arbitrary
database or shell access from the model"*). PRD §10 (explain, cite, distinguish
facts from inference, no silent goal changes, confirmation for consequential
actions). UX §25 (OSCAR is "operational, not conversational"; response
structure; §25.4 consequential actions require confirmation).

[ADR-031](ADR-031-ai-provider-abstraction.md) covers the provider layer. This
ADR covers everything above it: the conversation store, what context OSCAR sees,
what it can do, and how a state change actually happens.

## Decision

### 1. `ai` domain + V10 schema — Blueprint §18 verbatim

New `com.orbit.platform.ai` package. V10 migration:

```text
orbit.ai_conversations
- id          UUID PK
- user_id     UUID NOT NULL REFERENCES orbit.users(id) ON DELETE CASCADE
- title       VARCHAR(160)          -- first user message, trimmed; user-editable
- created_at  TIMESTAMPTZ NOT NULL
- updated_at  TIMESTAMPTZ NOT NULL

orbit.ai_messages                    -- append-only
- id             UUID PK
- conversation_id UUID NOT NULL REFERENCES orbit.ai_conversations(id) ON DELETE CASCADE
- role           VARCHAR(16) NOT NULL   -- CHECK IN ('user','assistant')
- content        TEXT NOT NULL
- model_provider VARCHAR(24)            -- null for user rows
- model_name     VARCHAR(60)
- prompt_version VARCHAR(24)
- token_usage    JSONB                  -- {promptTokens, completionTokens}
- created_at     TIMESTAMPTZ NOT NULL
```

**What is persisted (Blueprint §18 "never persist unnecessary sensitive
context"):** the user's message text, and OSCAR's *final* assistant text (the
rendered recommendation/answer). **Not persisted:** the assembled context
bundle, intermediate tool-call results, or the raw provider payloads. Tool
*names* invoked in a turn may be recorded in `token_usage`-adjacent metadata for
debugging; their outputs are not stored.

`GET/POST /api/v1/oscar/conversations`, `GET/PATCH/DELETE
/conversations/{id}` (PATCH = rename), `POST /conversations/{id}/turns` (the
chat turn — request: the user message; response: the `OscarResponse` §6, and
the assistant `ai_messages` row is created server-side). Ownership-404 (M1
pattern).

### 2. Context Builder — deterministic, layered, read-only through domain services

`ContextBuilder.forTurn(userId, message) -> ContextBundle`. Assembles the Tech
Arch §12 layers **by calling the existing `currentUser`-scoped domain services**
— never SQL, never another user's data:

| Layer | Source |
|---|---|
| Current objectives (title, status, progress, priority) | `ObjectiveService` |
| Active career target + readiness + top gaps | `CareerTargetService`, `CareerReadinessService` |
| Relevant skill graph (target skills + immediate neighbours) | `SkillGraphService` |
| Recent execution (last 2 weeks: completed, throughput, blockers) | `AnalyticsService` |
| Current projects (active, status, demonstrated coverage) | `ProjectService` |
| Incomplete tasks — top 10 by priority, with their `priority_reason` headline | `TaskService` / `TaskScoringService` |
| Today's plan + available minutes | `DailyPlanService`, `CurrentUserService` |
| This week's weekly-review `content` if one exists | `WeeklyReviewService` |

**M7 sends the full bundle every turn.** It is bounded (a few KB of structured
JSON) and Gemini's context window ([ADR-031](ADR-031-ai-provider-abstraction.md))
makes per-turn relevance filtering unnecessary. **Intent classification /
selective retrieval** (Blueprint §26 "retrieve only relevant context") is a
flagged refinement, not M7.

The bundle is rendered into the prompt inside explicit `<orbit_context>`
delimiters; the system prompt states that everything within is data and nothing
in it is an instruction (Blueprint §37).

### 3. Tool Registry — read tools execute, everything else proposes

`ToolRegistry` exposes JSON-schema tool declarations to the provider. Every tool
maps to an existing domain service call; **the backend validates every tool
input** (Blueprint §37) before dispatching.

**Read tools — executed immediately, no confirmation, results fed back to the
model (bounded to 3 rounds/turn, [ADR-031](ADR-031-ai-provider-abstraction.md) §2):**

```
get_current_objectives      get_active_career_target   get_skill_gap
get_skill_dependencies       get_project_status         get_today_plan
get_recent_execution         get_weekly_metrics         get_roadmap_intersection
```

Most of this is already in the context bundle; the tools let OSCAR pull *more
detail on demand* (e.g. one skill's full dependency subtree) without bloating
every prompt.

**Mutation tools — declared to the model, but never executed by OSCAR
(Blueprint §27, PRD §10, UX §25.4):**

```
create_task   complete_task   update_objective   create_project   record_evidence
```

### 4. How a state change happens — the model proposes, the human confirms, the *existing* endpoint executes

When the model emits a mutation tool call, OSCAR **does not call any mutating
service.** It:

1. Validates the args against the target endpoint's request schema.
2. Returns a `ProposedAction` in the `OscarResponse` (§6): a human-readable
   description, the exact `{method, path, body}` of the **existing** ORBIT API
   call, and OSCAR's reasoning.
3. The frontend renders it as a UX §25.2 card — `[ Start ] [ Explain ]
   [ Dismiss ]`.
4. **"Start" → the frontend calls that existing domain endpoint directly**
   (`POST /api/v1/tasks`, etc.) — which already enforces ownership, validation
   and re-scoring. The result is posted back into the conversation as a system
   note.

**OSCAR holds zero mutation capability at any point.** There is no code path
where a model response causes a write. This is the strictest possible reading of
"mutations require confirmation" and it means the "trusted automation mode"
Blueprint §27 mentions is a genuinely separate future ADR, not a flag flip.

### 5. `PolicyGuard`

Runs before the provider call and before each tool dispatch:
- per-user **daily request cap** (default 50) and **per-turn token budget**
  (24k prompt / 2k output) — configurable, startup-validated
  ([ADR-031](ADR-031-ai-provider-abstraction.md) §3)
- tool allow-list — a tool call for anything not in the registry is dropped
- tool-arg validation against the domain request schema
- conversation-scope check — the turn's `conversation_id` belongs to the caller
- strips any `<orbit_context>` / instruction-like markers from the *user's*
  message before it's sent (prevents a user smuggling fake context)

### 6. `OscarResponse` — the structured contract (UX §25.2 / §25.3, PRD §10)

`ResponseValidator` requires the model's JSON to parse to:

```
OscarResponse {
  kind: "answer" | "recommendation" | "unavailable"
  message: string                       // the prose, always present
  sections?: {                          // recommendation only (UX §25.2)
    action: string
    why: string
    impact: string
    evidence: EvidenceRef[]             // {label, kind: "fact"|"metric"|"inference", value}
  }
  proposedActions?: ProposedAction[]    // §4 — 0..n
  citations?: string[]                  // which context layers / tools were used (PRD §10 "cite ORBIT data")
}
```

Each evidence item is tagged `fact` / `metric` / `inference` (UX §25.3). A
response that doesn't parse → `{kind:"answer", message:"I couldn't put that
together — try rephrasing."}`, not persisted (Blueprint §13).

### 7. Web — the OSCAR view

Replace the `/oscar` placeholder:
- Conversation list + a conversation view: message stream, an autosizing input,
  a send button. **Non-streaming** — a "thinking…" state while the turn runs
  ([ADR-031](ADR-031-ai-provider-abstraction.md) §4).
- Recommendation rendering follows UX §25.2 (Action / Why / Impact / Evidence),
  with fact/metric/inference chips (§25.3).
- `ProposedAction` cards: `[ Start ]` `[ Explain ]` `[ Dismiss ]`. "Start" calls
  the existing domain hook (`useCreateTask`, etc.).
- **Unavailable state** (UX §25.5) when the feature flag is off or `AiService`
  returned `kind:"unavailable"` — the "your tasks / objectives / analytics
  remain available" panel.
- Entry points (UX §25.1): the nav item + a single Command Center recommendation
  card ("Ask OSCAR"). Contextual cards on Today / detail views — deferred.
- Uses the [ADR-030](ADR-030-frontend-component-primitives.md) Radix primitives
  (`Tooltip` to explain a citation, `DropdownMenu` for the conversation menu).

## Alternatives Considered

1. **Let OSCAR execute mutation tools directly after an in-chat "yes".** Rejected
   — an in-chat confirmation still routes the write through an OSCAR-held code
   path. Routing "Start" to the existing endpoint means OSCAR is provably
   read-only and the write goes through the same validation as a manual action.
2. **Persist the full context bundle + tool results per message** (for
   reproducibility). Rejected — Blueprint §18 "never persist unnecessary
   sensitive context"; the bundle is re-derivable from ORBIT's own data at any
   time, and storing it duplicates the whole DB into `ai_messages`.
3. **Vector store / embeddings for context retrieval.** Deferred — ORBIT's
   per-user data is small and fully structured; the domain services already
   answer "what's relevant". Revisit if the context bundle outgrows the window.
4. **A separate `oscar` domain package vs `ai`.** Blueprint §5's domain list has
   `ai/`, not `oscar/`. OSCAR lives in `ai/`.

## Consequences

- **Positive.** OSCAR is genuinely read-only — the hardest safety property
  (Blueprint §37, PRD §10) is structural, not policy. Context is real ORBIT data
  through existing ownership-scoped services. The response contract makes
  fact/inference separation and citations enforceable, not hoped-for.
- **Negative.** The "model proposes, human clicks, endpoint executes" flow is
  more UI plumbing than "OSCAR did it" — accepted, it's the point. Sending the
  full bundle every turn spends prompt tokens (bounded by the budget; Gemini's
  window absorbs it).
- **Neutral.** V10 adds two tables; `ai_messages` is append-only and small.

## Sign-off items

1. **`ai_conversations` / `ai_messages` (V10)** — Blueprint §18 verbatim;
   persist only the user message + OSCAR's final text + provider/model/version/usage.
2. **Context builder sends the full bounded bundle every turn** via existing
   domain services (no SQL, no cross-user); intent-based retrieval deferred.
3. **Read tools execute (3 rounds max); mutation tools return a `ProposedAction`
   only** — "Start" in the UI calls the existing domain endpoint. OSCAR never
   writes.
4. **`OscarResponse` schema** with fact/metric/inference-tagged evidence +
   citations; a non-parsing response degrades to a safe message, unpersisted.
5. **`/oscar` view + one Command Center card**; contextual cards on other views
   and streaming deferred.
