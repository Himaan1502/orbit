# ADR-034: OSCAR Contextual Recommendation Cards

## Status
Proposed — pre-Milestone-8 pass, alongside
[ADR-033](ADR-033-oscar-streaming.md). Approved by the product owner 2026-09-10
as the next work after M7.

## Date
2026-09-10

## Context

UX §25.1 says OSCAR can appear "on Today, in relevant detail views, through
contextual recommendation cards". M7 shipped only the `/oscar` conversation view
and one "Ask OSCAR" card on the Command Center. The spec names contextual cards
but defines no mechanism — the *what* is spec'd, the *how* needs an ADR.

The risk with a card that fires on page load: it is **system-initiated, not
user-initiated**. It must not hammer the free-tier provider, must not eat the
user's conversational daily cap, and must be **silent** when OSCAR is off or has
nothing to say — a "OSCAR unavailable" banner on every Today visit is worse than
no card.

Redis is in the compose stack and configured in `apps/api` but currently unused
by the API.

## Decision

### 1. Surfaces — Today and objective detail (this pass)

- **`/today`** — one card: "what to focus on / what's at risk today", from the
  daily plan + top tasks + execution health.
- **`/objectives/[id]`** — one card: "the next concrete step for this objective",
  from that objective + its milestones/tasks + linked skill/target.
- Command Center keeps its M7 "Ask OSCAR" entry-point card (not a nudge).
- **Skill detail, roadmap detail, Career → deferred** to a later pass.

### 2. Endpoint — `GET /api/v1/oscar/nudge`

```
GET /api/v1/oscar/nudge?surface=today
GET /api/v1/oscar/nudge?surface=objective&objectiveId={id}
```

Response:

```
{ "status": "ready" | "none" | "unavailable",
  "card": {                       // only when status = "ready"
    "headline": "…",
    "body": "…",
    "why": "…",
    "proposedAction": ProposedAction | null,   // ADR-032 §4 shape, reused
    "generatedAt": "…"
  } }
```

- **Single-shot, no tool rounds.** A focused mini-context only — for
  `surface=objective`: that objective + its tasks + its linked skill/target
  readiness; for `surface=today`: the daily plan + top-5 tasks + execution
  health. Not the full ContextBuilder bundle.
- **Non-streaming** — the fetch is a background page-load request; its latency
  is hidden behind a skeleton and a cache (§3).
- Ownership-scoped: `objectiveId` must be the caller's (404 → `status:"none"`,
  never a leak).

### 3. Redis cache — keyed by data fingerprint, not a clock

The first real API use of Redis. Cache key:

```
oscar:nudge:{userId}:{surface}:{surfaceId}:{fingerprint}
```

`fingerprint` is a cheap hash of the inputs that would change the advice —
objective `updated_at` + its tasks' `(id,status)` + the active daily-plan id +
today's date bucket. So the card **refreshes when the underlying data moves**,
not on a timer, and an unchanged page served twice costs **zero provider calls**.
A backstop TTL of 12h bounds staleness.

### 4. Budget — separate from the conversational cap

Nudges do **not** consume the user's `orbit.ai.daily-request-cap` (that is for
questions the user asked). Instead a dedicated Redis counter caps *nudge
generation* at **`orbit.ai.nudge-hourly-cap`** (default 12) per user per rolling
hour. Over budget, or OSCAR disabled, or the provider fails → the endpoint
returns `status:"none"` / `status:"unavailable"` and **serves the last cached
card if one exists**, else nothing. A nudge never blocks a page and never
surfaces an error toast.

### 5. Silent by default; dismissible per surface per day

- `status:"none"` / `"unavailable"` → the `<OscarNudge>` component renders
  **nothing** (no placeholder, no banner).
- `status:"ready"` → a compact card (Design System, violet accent, matching the
  M7 Command Center card) with `[ Start ] [ Explain ] [ Dismiss ]` when it
  carries a `proposedAction`, else `[ Explain ] [ Dismiss ]`.
- Dismissal is remembered in `localStorage` keyed `oscar:nudge:dismissed:{surface}:{surfaceId}:{yyyy-mm-dd}`
  (the ADR-013 per-viewer-convenience pattern) — gone for the rest of the day,
  back tomorrow.

### 6. OSCAR still never writes

A nudge's `proposedAction` is the exact M7 `ProposedAction` shape and reuses
`ProposedActionCard` — "Start" calls the existing domain endpoint. The nudge
generator has **no mutation path** (it does not even declare mutation tools —
it produces at most one proposed action from a fixed template, args
ownership-checked like M7).

### 7. Web

`<OscarNudge surface="today" />` / `<OscarNudge surface="objective" objectiveId={id} />`
— a TanStack Query hook (`staleTime` 30 min, `retry: false`), renders `null`
unless `status:"ready"` and not dismissed. Dropped into `app/today/page.tsx` and
`app/objectives/[id]/page.tsx`.

## Alternatives Considered

1. **Enrich the existing deterministic `nextBestAction`** (Command Center /
   dashboard) with an OSCAR explanation instead of a new card. Rejected — that
   is a Priority-Engine artifact; a contextual card is OSCAR's own read of the
   surface, and UX §25.1 asks for it on Today / detail views, not just home.
2. **Generate nudges on a schedule** (a `@Scheduled` job pre-warms every active
   user's cards). Rejected for this pass — wasteful for surfaces the user never
   opens; the fingerprint cache already makes the on-load call cheap. A
   pre-warm job is a fine later optimisation.
3. **Count nudges against the conversational cap.** Rejected — a user who never
   opens `/oscar` should not have their question budget drained by page loads.
4. **Stream the nudge.** Rejected — it is short, cached, and behind a skeleton;
   streaming adds machinery for no felt benefit.

## Consequences

- **Positive.** OSCAR meets the user where the work is (UX §25.1). The
  fingerprint cache + separate budget keep free-tier cost near zero for normal
  use. Silent-by-default means it never degrades a page.
- **Negative.** First Redis dependency in the API surface (a cache miss with
  Redis down must degrade to `status:"none"`, not error — handled). A new small
  prompt template + endpoint to maintain. Two more call sites for `<OscarNudge>`.
- **Neutral.** `orbit.ai.nudge-hourly-cap` is a new tunable (startup-validated).
  Uses the ADR-033 `oscar-v2` prose format (headline = first line, body = rest).

## Sign-off items

1. **Two surfaces this pass** — Today + objective detail; skill / roadmap /
   Career deferred.
2. **`GET /oscar/nudge`** — single-shot, **no tool rounds**, focused
   mini-context, non-streaming, ownership-scoped.
3. **Redis-cached by `(user, surface, id, data-fingerprint)` + 12h TTL** — first
   API Redis use; a cache hit is zero provider calls; Redis down → `status:"none"`.
4. **Separate hourly nudge budget** (`orbit.ai.nudge-hourly-cap`, default 12),
   **not** the conversational daily cap; serve-stale-or-nothing, never block a
   page, never an error toast.
5. **Silent when OSCAR is off / failing / empty**; dismissible per surface per
   day (localStorage).
6. **Card `proposedAction` reuses the M7 confirm flow** — OSCAR still never
   writes; the nudge generator holds no mutation capability.
