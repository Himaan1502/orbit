# ADR-017: Skill Scoring v1 — Dimensions, Formula, Weights

## Status
Accepted — weight values ratified by the product owner 2026-09-08

## Date
2026-09-07

## Context
Blueprint §23 and UX §21 specify a **deterministic** skill score: a weighted
sum of five 0–1 dimensions. PRD §4.5 lists the same five. Blueprint §33 lists
scoring weights as an open decision to be made through an ADR. Blueprint §43's
Milestone-4 acceptance criterion: **"readiness is reproducible"** (skill scores
feed readiness). PRD §2.7 / UX §21 — "never present the aggregate as objective
truth", show the dimensions.

Milestone 3 introduces the Skills domain. Skill assessments in M3 are
**manual self-assessment** — the Evidence Engine (M5) and Project Engine (M5)
do not exist yet, so the `evidence` and `production` dimensions are
user-supplied claims, not verified signals (PRD §7 — "distinguish learning from
demonstrated capability").

## Decision

### 1. Formula — adopt Blueprint §23 exactly
```
overall = 0.20·knowledge + 0.25·practical + 0.20·evidence + 0.15·interview + 0.20·production
```
Dimensions and `overall` are stored as `NUMERIC` in `[0, 1]` (consistent with
`objectives.progress` and the Priority Engine's 0–1 factors); the API and UI
multiply by 100 for display ("78/100", UX §21). Weights sum to 1, so
`overall ∈ [0, 1]`.

### 2. Configurable, not hardcoded
Weights bind from `orbit.skills.scoring.weights.*` to a `@ConfigurationProperties`
record, startup-validated to sum to `1.0 ± 0.001` (same mechanism as
[ADR-014](ADR-014-priority-engine-v1.md)'s `PriorityWeights`). A short
`weightsFingerprint` is recorded on every persisted assessment.

**Per-career-target weight overrides** (Blueprint §23 — "an SRE target may assign
greater production weight") are **deferred**: the global weights ship in M3; a
`career_targets.scoring_weights` JSONB override is a small, additive later
change. Recorded in `deferred-items.md`.

### 3. `SkillScoringEngine` — pure, deterministic
`SkillScoringEngine.score(SkillDimensions)` → `SkillScore` is a pure function
(no I/O). `SkillDimensions` is a value object of the five 0–1 inputs.
`SkillScore` carries `overall`, the per-dimension contribution breakdown, the
`weightsFingerprint`, and a `confidence` flag (see §4). Unit-tested directly.

### 4. Learning vs demonstrated (PRD §7)
Each `skill_assessments` row records `assessment_source`:

| source | meaning |
|--------|---------|
| `self` | user self-assessment (the only source in M3) |
| `evidence` | derived from verified evidence (M5) |
| `interview` | from a recorded interview result (M5+) |
| `system` | computed by ORBIT from execution signals (later) |

The score's `confidence` is **`self-reported`** whenever the latest assessment
is `source = self` (i.e. always in M3). The UI shows this next to the score and
never renders it as fact. When `evidence`/`production` verified sources land,
`confidence` becomes `partially-verified` / `verified`.

### 5. Where scores are used
- Skill detail (`GET /api/v1/skills/{id}` for the current user) returns the
  latest `overall` + dimension breakdown + `confidence`.
- Career readiness ([ADR-018](ADR-018-career-readiness.md)) aggregates skill
  `overall` weighted by `career_target_skills.importance`.
- The Priority Engine's `skillGap` factor = `1 − overall` for the task's skill
  ([ADR-014](ADR-014-priority-engine-v1.md), factor provider swapped —
  formula/weights unchanged).

`overall_score` is recomputed and persisted on the assessment row whenever a new
assessment is posted; historical assessment rows are immutable (append-only),
so a score's provenance is always inspectable.

## Alternatives Considered
1. **0–100 integer dimensions** (matching the UX display) — rejected; `NUMERIC`
   0–1 keeps one scale across scoring, readiness, and the priority factors, and
   the ×100 is a display concern.
2. **Compute `overall` on read** instead of persisting it — rejected;
   persisting it on the (immutable) assessment row makes readiness reproducible
   from stored data and lets a score be traced to the weights that made it.
3. **Multiplicative model** — rejected for the same reason as ADR-014: one weak
   self-assessed dimension would collapse the score.
4. **Per-target weights in M3** — deferred; the global weights are the spec's
   starting point and per-target tuning needs real usage data first.

## Consequences
- **Positive:** one deterministic, explainable score; reproducible from stored
  assessment rows (Blueprint §43); weights are one config change from tuning;
  the Priority Engine's `skillGap` provider is a one-line swap.
- **Negative:** in M3 the score is entirely self-reported — `evidence` and
  `production` dimensions carry the user's optimism. Mitigated by the
  `confidence` flag and honest UI copy; corrected when M5 adds verified sources.
- **Negative:** global (not per-target) weights mean an SRE-focused user and a
  frontend-focused user get the same weighting in M3. Documented as deferred.

## Decision needed (product owner)
Confirm or adjust:
1. **The five weights** — shipping as Blueprint §23's values
   (`knowledge 0.20 · practical 0.25 · evidence 0.20 · interview 0.15 ·
   production 0.20`). These are the spec's own numbers; sign-off is
   ratification per Blueprint §33.
2. **Deferring per-career-target weight overrides** to a later milestone.
3. **`confidence` = `self-reported` for every M3 score** (since all M3
   assessments are `source = self`).
