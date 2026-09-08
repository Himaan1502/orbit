# ADR-026: Evidence-Backed Confidence, Readiness with Evidence/Project Coverage, and the Final Priority-Engine Factor Swap

## Status
Proposed — for product-owner scope sign-off (M5). **Readiness blend weights and
the confidence thresholds need sign-off** (see §5).

## Date
2026-09-08

## Context
[ADR-024](ADR-024-projects-domain.md) and [ADR-025](ADR-025-evidence-domain.md)
add the Projects and Evidence *data*. This ADR is how that data flows into the
three scores that were left with placeholders for it:

1. **Skill assessment `confidence`** — [ADR-017](ADR-017-skill-scoring-v1.md) §4
   promised: *"When `evidence`/`production` verified sources land, `confidence`
   becomes `partially-verified` / `verified`."* Today every score is
   `self-reported`.
2. **Career Readiness `evidenceCoverage` / `projectCoverage`** —
   [ADR-018](ADR-018-career-readiness.md) §1 shipped these as explicit
   `{value: null, weight: 0, status: "not-tracked-until-…"}` and said they
   *"take a share once their engines land (M5)."*
3. **Priority Engine `projectImpact`** — [ADR-014](ADR-014-priority-engine-v1.md)
   ships it as a neutral `0.5` ("Projects domain not yet available"). It is the
   **last** of the eight factors still on its placeholder.

**Frozen and not reopened:** the Skill Scoring formula and weights (ADR-017),
the Priority Engine formula and weights (ADR-014), the ownership-404 pattern,
OIDC/BFF.

## Decision

### 1. Evidence raises `confidence`, never a score

Blueprint §13: *"Evidence must never automatically imply mastery."* So the
skill-scoring **dimension values stay exactly as the user self-assessed them**,
and the **formula and weights are untouched**. What changes is the `confidence`
label on the *latest* assessment for a skill, derived at read time from the
evidence linked to that skill:

```
evidenceStrength(skill, user) = Σ over linked, non-deleted evidence of
                                   contribution_weight            (capped at 1.0)

confidence(latest assessment) =
   VERIFIED            if evidenceStrength ≥ orbit.evidence.verified-threshold  (default 1.0)
                          AND ≥ 2 distinct evidence types
   PARTIALLY_VERIFIED  if evidenceStrength ≥ orbit.evidence.partial-threshold   (default 0.4)
   SELF_REPORTED       otherwise    (and always, if there is no assessment at all)
```

- Computed by a **pure `EvidenceConfidenceCalculator`** (the M2/M3/M4 pattern),
  from the evidence weights + types + the assessment. No I/O, unit-tested.
- `skill_assessments` rows are **still append-only and still `source = self`** —
  ADR-025 §5. A future `source = system` re-assessment from verified evidence is
  a separate, later decision.
- The Skill detail response's `confidence` field and the readiness `confidence`
  string now vary per skill instead of being the constant `"self-reported"`.
- **Un-assessed skill + rich evidence ⇒ still "not assessed."** Evidence with no
  self-assessment has nothing to raise the confidence *of*.

### 2. Career Readiness — `evidenceCoverage` and `projectCoverage` become real

Amends [ADR-018](ADR-018-career-readiness.md) §1. The four components and their
proposed blend weights (**renormalised among those with data**, exactly as
ADR-018 already does):

| component | M5 computation | proposed weight |
|---|---|---|
| `skillCapability` | ADR-018 — importance-weighted mean capability | **0.45** |
| `interviewReadiness` | ADR-018 — interview-weight-weighted interview dimension | **0.20** |
| `evidenceCoverage` | importance-weighted mean of `min(1, evidenceStrength(skill))` over the target's skills | **0.20** |
| `projectCoverage` | importance-weighted mean of `Σ demonstrated_contribution` (capped at 1) over the target's skills, from `project_skills` | **0.15** |

`CareerReadinessCalculator.compute(...)` gains the evidence + project inputs;
the pure signature and reproducibility (Blueprint §43) are preserved. A target
skill with no evidence contributes `0` to `evidenceCoverage` — a real gap, the
ADR-018 convention.

Career **gaps** (`GET /api/v1/career/gaps`) gains **`evidenceGaps`** (target
skills with capability ≥ some bar but `evidenceStrength` ≈ 0 — "you can do it,
you just haven't shown it") and **`projectGaps`** (target skills no project
targets), per Blueprint §24 / UX §31.

### 3. `project_skills.demonstrated_contribution` — derived

Per [ADR-024](ADR-024-projects-domain.md) §2, set by a pure calculator:

```
demonstrated_contribution(project, skill) =
   0                                             if project.status ≠ 'completed'
   min( expected_contribution,
        Σ contribution_weight of evidence linked to BOTH this skill and this project )
                                                 if project.status = 'completed'
```

Recomputed inline when a project's status changes or its evidence links change
(the M2 "recompute progress inline" pattern). Stored on the row (it is a
join-table attribute, not a per-request score) but **only ever written by the
calculator**, never by the API from a request body.

### 4. Priority Engine — the final factor swap (`projectImpact`)

Same mechanism as the M3 swap ([ADR-014](ADR-014-priority-engine-v1.md), formula
+ weights + `fingerprint` **byte-identical**):

- `PriorityInput` gains a nullable `Double projectImpactSignal` (joining the
  four M3 signals).
- `PriorityEngine.score()` replaces the hardcoded neutral `projectImpact` with
  `signalOrNeutral(factors, "projectImpact", in.projectImpactSignal(), …)` —
  identical helper to `skillGap` / `dependencyImpact` / `interviewValue`.
- The signal is computed by a new `TaskProjectSignals` port
  (`projects` domain, consumed by `execution` — the M3 `TaskCareerSignals`
  pattern):
  `projectImpact = expected_contribution of this task's skill for this task's
  project`, weighted up if the project is `active` and its objective is
  career-linked; `null` (→ neutral 0.5, unchanged) when the task has no project.
- **`projectImpact` rewards *working on* an active project** via
  `expected_contribution` — it does not wait for `demonstrated_contribution`
  (that would mean a task only gets priority *after* the project is done, which
  is backwards).

After M5 **no Priority Engine factor is on a placeholder.**

### 5. What needs product-owner sign-off

1. **Readiness blend** — `skillCapability 0.45 / interviewReadiness 0.20 /
   evidenceCoverage 0.20 / projectCoverage 0.15`. Configurable
   (`orbit.career.readiness.*`), startup-validated, renormalised among available
   components.
2. **Confidence thresholds** — `partial ≥ 0.4`, `verified ≥ 1.0` + 2 distinct
   evidence types. Configurable (`orbit.evidence.*`).
3. **`verification_status = self_attested` is the M5 ceiling** (ADR-025 §3) — no
   external verification integrations this milestone.
4. **Evidence still never writes a `skill_assessments` row** (ADR-025 §5) — it
   raises `confidence` and feeds readiness only.

## Alternatives Considered
1. **Evidence bumps the `evidence` *dimension* of a new auto-assessment** —
   rejected; violates Blueprint §13, and it would silently change scores behind
   the user's back. Confidence-only is the honest model.
2. **`projectCoverage` counts *expected* contribution** — rejected; that is a
   claim, not coverage. `demonstrated` (evidence-gated) is the point.
3. **`projectImpact` uses `demonstrated_contribution`** — rejected (§4): a task
   would only gain priority after its project is finished.
4. **A fifth readiness component for "recency of evidence"** — deferred; needs
   the evidence-trend work that ADR-018's deferred "readiness trend" also needs.
5. **Fixed readiness weights (not configurable)** — rejected; every ORBIT
   scoring weight is configurable + startup-validated (ADR-014/017/018 pattern).

## Consequences
- **Positive:** the PRD §7 differentiator ("distinguish learning from
  demonstrated capability") is now structural — confidence and readiness move
  only when the user logs real artifacts.
- **Positive:** every Priority Engine factor and every Career Readiness
  component computes from real data after M5.
- **Positive:** no frozen decision reopened — dimension scores, both formulas,
  and both weight sets are untouched; only *confidence* and the
  *placeholder* factor/components change.
- **Negative:** four more tunables (one blend + two thresholds) shipped as
  defaults. Flagged; all configurable.
- **Negative:** `confidence` is now a read-time computation over evidence for
  every skill shown — a few extra rows per skill-list request. Trivial at this
  scale; revisit with a projection if the evidence table grows large.
