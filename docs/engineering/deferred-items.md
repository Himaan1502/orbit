# Deferred Engineering Items

Cross-cutting work consciously postponed, with the milestone it is expected to
land in. Add an entry here instead of a bare `TODO` in code, and link back to
this file from the relevant comment.

---

## ✅ Closed in Milestone 1 (incl. follow-ups)

- **Hermetic test suite (Testcontainers).** `apps/api` and `apps/auth` now start
  PostgreSQL (and Redis, for the API) via Testcontainers using the
  singleton-container pattern. `application-test.yml` no longer assumes a local
  instance or a particular Redis password. CI service containers removed.
- **`apps/api/application-test.yml` relocated** from `src/main/resources/` to
  `src/test/resources/` — it no longer ships in the production jar.
- **Signing-key rotation.** Overlapping rotation implemented in `SigningKeyManager`
  ([ADR-011](../adr/ADR-011-authorization-server-persistence.md) §3): the JWKS
  publishes the active key plus retired keys still inside the overlap window
  (`AUTH_SIGNING_KEY_OVERLAP`, default 7d); the signer is pinned to the active
  `kid` via the JWS header. Operator-triggered by `AUTH_ROTATE_SIGNING_KEY=true`
  + restart; the in-memory JWK set refreshes live. A zero-restart admin HTTP
  trigger remains a possible future addition but is not needed.

---

## ✅ Closed in Milestone 2

- **Objectives / Milestones / Tasks / Daily Planning** — Blueprint §9, §10, §20.
  See CLAUDE.md's M2 status table and ADR-014/015/016.
- **Priority Engine v1** — Blueprint §22, configurable weights
  ([ADR-014](../adr/ADR-014-priority-engine-v1.md)).

---

## ✅ Closed in Milestone 4

- **Roadmap domain + ingestion** — Blueprint §11/§20/§25. 13 curated roadmap.sh
  roadmaps normalized to topics + canonical skills, seeded (V6 schema, V7 data),
  augmenting the M3 catalog. [ADR-021](../adr/ADR-021-roadmap-domain-and-schema.md),
  [ADR-022](../adr/ADR-022-roadmap-ingestion.md).
- **Derived roadmap progress + Roadmap Intersection** — Blueprint §32, PRD §5.
  [ADR-023](../adr/ADR-023-roadmap-progress-and-intersection.md).

---

## ✅ Closed in Milestone 5

- **Projects domain** — Blueprint §14. `orbit.projects` + `project_skills`
  (expected vs derived demonstrated contribution) + `tasks.project_id`, a
  project reuses its linked objective's milestones.
  [ADR-024](../adr/ADR-024-projects-domain.md).
- **Evidence domain** — Blueprint §13. `orbit.evidence` + `skill_evidence` +
  `project_evidence`; `self_attested` verification ceiling; evidence never
  writes `skill_assessments`. [ADR-025](../adr/ADR-025-evidence-domain.md).
- **Evidence-backed confidence + 4-way career readiness + `projectImpact`
  factor swap** — a skill's assessment `confidence` now varies per skill from
  its evidence (the score never moves); readiness blends
  capability/interview/evidence/project coverage; the last Priority Engine
  factor comes off its placeholder.
  [ADR-026](../adr/ADR-026-evidence-backed-confidence-and-readiness.md).
- Supersedes **"M5+ — Evidence & project coverage in career readiness"** below.

---

## ✅ Closed in Milestone 6

- **Durable job mechanism** (Blueprint §55 open decision, deferred since M0) —
  `@Scheduled` + an `orbit.job_runs` ledger + `JobRunner` (idempotent /
  retryable / observable / bounded / dup-safe). Not Quartz / Batch / Redis-queue;
  ShedLock named as the M10 multi-instance upgrade. Supersedes
  **"M4+ — Durable / async job runner"** below.
  [ADR-029](../adr/ADR-029-durable-job-mechanism.md).
- **Analytics domain** — Blueprint §15 / M8, UX §24. Execution / career / skills
  trends, all derived at read time (no rollup storage). The readiness trend
  resolves the `ReadinessResponse.trend: null` stub. `objective_progress_events`
  (event-appended) + `tasks.blocked_since`. `metrics` / `metric_entries` schema
  only. [ADR-027](../adr/ADR-027-analytics-domain.md).
- **Weekly Review** — Blueprint §16, PRD §8. `orbit.weekly_reviews` + a pure
  rule-based `WeeklyReviewComposer` (no LLM; `generated_by='oscar'` reserved for
  M7+) + `WeeklyReviewGenerationJob` (the one M6 scheduled job).
  [ADR-028](../adr/ADR-028-weekly-review.md).

---

## ✅ Closed in Milestone 7 (OSCAR)

- **AI provider abstraction** (Blueprint §55 open decision — *"exact AI
  model/provider routing"*) — `AiProvider` port + Gemini (primary,
  `gemini-3.6-flash`) / Groq (fallback, `openai/gpt-oss-120b`) adapters, both
  free-tier / no-card; `AiService` with retry → fail-over → graceful degrade;
  feature-flagged on a provider key. Model IDs are `GEMINI_MODEL` / `GROQ_MODEL`
  env overrides. [ADR-031](../adr/ADR-031-ai-provider-abstraction.md).
- **OSCAR domain** — `ai/` package, V10 `ai_conversations` / `ai_messages`
  (Blueprint §18 verbatim, append-only). Layered `ContextBuilder` via the
  existing ownership-scoped domain services. `ToolRegistry` (9 read tools) +
  `MutationProposer` (5 mutation tools) — **OSCAR never writes**: a mutation tool
  call becomes a `ProposedAction` the user confirms, which calls the existing
  domain endpoint. `PolicyGuard` (per-user daily cap + prompt sanitisation),
  `ResponseValidator` (`OscarResponse` schema, non-parsing → safe fallback,
  unpersisted). Non-streaming. [ADR-032](../adr/ADR-032-oscar-domain.md).

### Not in scope for M7 — still open

- **Streaming responses (SSE).** M7 is request/response with a "thinking…"
  state. Both providers support streaming; deferred (ADR-031 §Alternatives 4).
- **OSCAR-narrated Weekly Review.** `weekly_reviews.generated_by` still only ever
  written as `'system'`; `'oscar'` stays reserved. The rule-based composer is
  unchanged.
- **Contextual recommendation cards** on Today / objective / skill detail views
  (UX §25.1). M7 ships the `/oscar` view + one Command Center card only.
- **Trusted automation mode** (Blueprint §27) — a genuinely separate future ADR,
  not a flag flip, precisely because OSCAR holds zero mutation capability today.
- **Intent classification / selective context retrieval** (Blueprint §26
  *"retrieve only relevant context"*). M7 sends the full bounded bundle every
  turn (ADR-032 §2); Gemini's window absorbs it.
- **Vector store / embeddings** for context (ADR-032 §Alternatives 3).
- **Cost controls / usage dashboard.** `ai_messages.token_usage` is stored but
  nothing acts on it (ADR-031 §3).
- **The post-confirmation "system note"** (ADR-032 §4 step 4) is shown in the UI
  only, not persisted — `ai_messages.role` is `('user','assistant')` and the
  table is append-only. A `role='system'` note or a dedicated events table is a
  small future addition if history needs to show confirmed actions.
- **SRE / Balance / Integration tools** in the registry.

---

## M5+ — Runtime roadmap importer

**Context.** M4 loads the 13 roadmaps as a **Flyway versioned seed** (V7,
[ADR-022](../adr/ADR-022-roadmap-ingestion.md) §1). `roadmaps.last_verified_at`
records when a human last checked the source.

**Deferred decision.** A re-runnable import command / job that fetches roadmap.sh
JSON, normalizes it, and upserts topics + skill mappings + edges (still through
`SkillGraphValidator`), for when the source changes and a curated re-seed is too
manual. Needs an outbound HTTP dependency and a normalization pipeline.

**Why not now.** One-time load of curated data; `ON CONFLICT` keeps a later
hand-refresh safe.

---

## M5+ — `user_roadmaps.last_activity_at`

**Context.** [ADR-021](../adr/ADR-021-roadmap-domain-and-schema.md) §5 —
`user_roadmaps` carries `started_at` and `target_date` but **not**
`last_activity_at` (it was in the product owner's suggested shape).

**Deferred decision.** Bump `last_activity_at` when a task tied to a skill in the
roadmap is completed, or a skill in the roadmap is re-assessed — needs a hook
from `execution` / `skills` into `roadmaps`. Add it with that hook.

---

## M4+ — Roadmap-tier as a Priority Engine signal

**Context.** [ADR-021](../adr/ADR-021-roadmap-domain-and-schema.md) §6 — roadmap
`priority_tier` (P1/P2/P3) drives **UI surfacing only**. The product owner asked
for the caution on record that tier "should not be the entire score"; an urgent
P2/P3 task must still be able to outscore a less-urgent P1 task.

**Deferred decision.** *If* usage shows tier should nudge task priority, add it
as a **new** small-weight `PriorityInput` signal (the M3 factor-swap pattern —
formula and existing weights unchanged, [ADR-014](../adr/ADR-014-priority-engine-v1.md)),
never as an override. Requires product-owner sign-off on the weight.

**Why not now.** No evidence it is needed; ADR-014's formula is frozen.

---

## M5+ — `roadmap_topic_dependencies` table

**Context.** [ADR-021](../adr/ADR-021-roadmap-domain-and-schema.md) §4 — M4 has
no per-roadmap topic-dependency edges; intra-roadmap order is `sort_order` + the
global `skill_dependencies` graph.

**Deferred decision.** Add the table (with its own cycle validation) only if a
roadmap needs a hard "topic A before topic B" gate that the skill graph does not
already express. An ADR when a real case appears.

---

## M6+ — Evidence & Project coverage on roadmap detail

**Context.** UX §20 roadmap detail lists "evidence" and "related projects";
[ADR-023](../adr/ADR-023-roadmap-progress-and-intersection.md) §5 excludes them.
As of **M5 the Evidence engine and Projects domain both exist** — but the
roadmap detail page still does not surface them.

**Deferred decision.** Surface a roadmap topic's evidence and the projects that
exercised its skills — a read-only join from `roadmap_topic_skills` to
`skill_evidence` / `project_skills`. No new domain work; a roadmaps-side query +
UI section.

---

## ✅ Closed in Milestone 3 — Priority Engine: real factor providers (partial)

Four of the five career/skill factors now compute from real data
([ADR-014](../adr/ADR-014-priority-engine-v1.md), swap only — formula, weights
and `fingerprint` unchanged):

- `careerRelevance` — the task skill's importance for the objective's career
  target; falls back to the target's mean skill importance when the task has no
  skill, and to the M2 objective-priority proxy when there is no career target.
- `skillGap` — `1 − latest overall` for the task's skill, or an assumed-gap
  constant (`0.7`) when the skill is attached but never self-assessed.
- `dependencyImpact` — fraction of the target's skills that the task's skill
  unblocks in the dependency graph.
- `interviewValue` — the task skill's `interview_weight` for the target.

Still neutral: **`projectImpact`** — waits on the Projects domain. And
`careerRelevance` / `skillGap` / `dependencyImpact` / `interviewValue` fall back
to neutral/proxy exactly as in M2 for any task without the relevant signal.

**Touch points if resumed.** `apps/api` `career/service/CareerTaskSignalsImpl.java`,
`execution/service/TaskScoringService.java`, `execution/priority/PriorityEngine.java`.

---

## M4+ — Per-career-target skill scoring weight overrides

**Context.** [ADR-017](../adr/ADR-017-skill-scoring-v1.md) §5 — the five Skill
Scoring dimension weights are one global set
(`orbit.skills.scoring.weights.*`). A career target already carries per-skill
`importance` / `interview_weight` / `production_weight`, but those weight the
skill within *readiness*, not the dimension blend within the *skill's own score*.

**Deferred decision.** Allow a career target (or role family) to override the
dimension weights — e.g. an SRE-leaning target weighting `production` higher —
resolved at scoring time with the global set as the default.

**Why not now.** No evidence yet that one global blend is wrong; adding a second
resolution path before it is needed is premature.

---

## ✅ Resolved (2026-09-09, Milestone 6) — Career readiness trend / history

`GET /api/v1/analytics/career` returns a `readinessTrend` — the existing
`CareerReadinessCalculator` re-run at each assessment/evidence/project event
with the as-of state (step function; formula/weights/`fingerprint` unchanged),
plus a sparkline on the Career page and the full chart on
`/analytics` ([ADR-027](../adr/ADR-027-analytics-domain.md) §2). The
`ReadinessResponse.trend: null` stub on `/career/readiness` itself is still
there — the history lives on the analytics endpoint, not the point-in-time one.

**Still open:** evidence/project components of a past readiness point are
reconstructed from `created_at` / `completed_at` (an approximation where the
derived `demonstrated_contribution` has no history of its own).

---

## ✅ Resolved (2026-09-08, Milestone 5) — Evidence & project coverage in career readiness

Readiness now blends four components —
`skillCapability 0.45 / interviewReadiness 0.20 / evidenceCoverage 0.20 /
projectCoverage 0.15` — all **computed**
([ADR-026](../adr/ADR-026-evidence-backed-confidence-and-readiness.md) §2). The
M3–M4 placeholder (`{value: null, weight: 0}`) is gone. **Note:** because the
capability weight dropped `0.70 → 0.45`, an evidence-free target's readiness
number is lower than under the old blend — intended.

---

## M4+ — User-editable skill catalog & assessment sources beyond `self`

**Context.** [ADR-020](../adr/ADR-020-skill-catalog-seed.md) — the catalog is
seeded and read-only to end users in M3; `skill_assessments.assessment_source`
allows `self | evidence | interview | system` but M3 only ever writes `self`, so
every score is `confidence: self-reported`.

**Deferred decision.** (a) A curator/admin path to add or deprecate catalog
skills and edit dependency edges (with the `SkillGraphValidator.wouldCreateCycle`
guard already in place); (b) `evidence`- and `interview`-sourced assessments that
raise `confidence` to `partially-verified` / `verified` as the Evidence and
interview-prep engines land.

---

## ✅ Resolved (2026-09-09, Milestone 6) — Durable / async job runner

`@Scheduled` + an `orbit.job_runs` ledger + `JobRunner` is ORBIT's durable job
mechanism for the single-instance phase
([ADR-029](../adr/ADR-029-durable-job-mechanism.md)) — idempotent, retryable,
observable, bounded, dup-safe (Tech Arch §11). ShedLock is the named M10
multi-instance upgrade. The one scheduled job so far is
`WeeklyReviewGenerationJob`.

**Still inline (by design, ADR-029 §Alternatives):** daily-plan generation, M2
progress rollup, M3 skill scoring, M5 `demonstrated_contribution` recompute, and
the M6 analytics series — all fast for a single user and correct against
source-of-truth. Move one onto the runner if a single edit ever fans out to
enough recompute to be worth deferring. `DailyPlanGenerator`,
`ProgressCalculator`, `SkillScoringEngine`, `DemonstratedContributionCalculator`
and `WeeklyReviewComposer` are pure functions a job can call directly.

---

## ✅ Resolved (2026-09-07) — Objective progress when milestones AND loose tasks coexist

An objective's loose tasks (objective set, milestone null) now count together as
**one synthetic unit** of progress alongside its milestones
([ADR-016](../adr/ADR-016-objective-status-and-progress.md) §3, amended).
Milestone-only and task-only objectives are unchanged.

---

## ✅ Resolved (2026-09-07) — Priority Engine weight-sum validation test

`PriorityWeightsTest` exercises the package-private `validate()` directly: the
context refuses to start if the eight weights do not sum to 1 (±0.001).

---

## M2+ — Daily-plan packing quality

**Context.** `DailyPlanGenerator` uses greedy selection by priority within the
minute budget ([ADR-015](../adr/ADR-015-daily-plan-generation.md), alternative 2).

**Deferred decision.** If users find the plan wastes time at the end of the
budget, replace greedy with a bounded knapsack / best-fit packing. Greedy is
kept for v1 because it is explainable and O(n log n).

---

## M2+ — Redis-backed rate limiting and durable lockout

**Context.** Login / registration rate limiting and failed-login lockout in
`apps/auth` are **in-memory** (Bucket4j + a `ConcurrentHashMap`), per
[ADR-012](../adr/ADR-012-identity-and-registration-model.md) §5. State resets on
restart and is not shared across replicas. This is a deviation from
[ADR-005](../adr/ADR-005-redis-usage-boundaries.md), which names Redis for
rate-limit state.

**Deferred decision.** When `apps/auth` runs more than one replica (or restart
resets become a real abuse gap), move rate-limit buckets and lockout counters to
Redis, and add a `auth.account_locks` table (or Redis TTL keys) for durable
lockout with an admin unlock path.

**Why not now.** `apps/auth` is single-instance in M1; adding Redis as a hard
dependency of that service is not yet justified.

**Touch points.** `apps/auth` `ratelimit/` package, `build.gradle.kts`,
`docker-compose.yml` (`orbit-auth` would gain a Redis dependency).

---

## M2+ — Password reset, email verification, MFA

**Context.** M1 scope is registration + login + logout only. There is no email
delivery in the platform yet.

**Deferred decision.** Email-verification-on-signup (with a `verified_at` column
on `auth.users`), self-service password reset (time-boxed single-use tokens),
and TOTP/WebAuthn MFA. Each needs an outbound email/notification channel first.

**Why not now.** Explicitly out of scope for Milestone 1. Single user today.

**Touch points.** `apps/auth` `identity/` package, a new `auth` migration, a
mail/notification service (does not exist yet).

---

## M2+ — HIBP (Have I Been Pwned) password check

**Context.** `RegistrationService` rejects short passwords, passwords containing
the email, all-digit / low-variety passwords, and a small embedded base-word
deny-list. It does **not** check breach corpora.

**Deferred decision.** Add a k-anonymity range query to the HIBP Pwned Passwords
API (send only the first 5 SHA-1 hex chars) during registration, fail-open on
API error.

**Why not now.** Adds an outbound dependency on a third-party API for a
single-user tool; the embedded checks cover the common cases.

**Touch points.** `apps/auth` `RegistrationService#validatePassword`.

---

## M2+ — ADMIN role and RBAC surface

**Context.** [ADR-004](../adr/ADR-004-oidc-authentication.md) allows `USER` /
`ADMIN`. M1 issues only `roles: ["USER"]`
([ADR-012](../adr/ADR-012-identity-and-registration-model.md) §2). The resource
server already maps a `roles` claim to `ROLE_*`.

**Deferred decision.** An `auth.users.role` column (or a roles table), admin
assignment, and `@PreAuthorize("hasRole('ADMIN')")` on the endpoints that need
it.

**Why not now.** No admin-only functionality exists yet.

---

## M2+ — BFF CSRF hardening (double-submit token)

**Context.** The web BFF relies on `SameSite=Lax` + an `Origin`/`Sec-Fetch-Site`
check for its state-changing routes
([ADR-013](../adr/ADR-013-frontend-session-and-bff.md) §4). No rotating
anti-CSRF token.

**Deferred decision.** Add a double-submit CSRF token for non-GET requests
through `/api/orbit/*` and `/logout` if the residual `SameSite=Lax` gap (a
same-site subdomain, or an old browser) becomes a concern.

**Why not now.** With `SameSite=Lax`, the origin check, and GET requests being
side-effect-free at the proxy, the M1 residual surface is low for a single-user
tool on one origin.

---

## M2+ — Auth-server session store & issuer for multi-instance / hosted

**Context.** `apps/auth` uses the default in-memory HTTP session for the login
UI, and the compose issuer is `http://localhost:9000`
([ADR-004](../adr/ADR-004-oidc-authentication.md) "Consequences").

**Deferred decision.** For a hosted / multi-replica deployment: a shared session
store (Spring Session + Redis or JDBC) for the auth server's login UI, and a
single externally-resolvable HTTPS issuer URL.

**Why not now.** Single instance, local only.
