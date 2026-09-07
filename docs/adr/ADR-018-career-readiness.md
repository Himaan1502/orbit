# ADR-018: Career Readiness Aggregation (with M3 components only)

## Status
Accepted — **blend weights (0.70 / 0.30) pending product-owner sign-off** (see §"Decision needed")

## Date
2026-09-07

## Context
Blueprint §24 defines career readiness as an aggregate of **Skill Capability +
Evidence + Project Coverage + Interview Readiness + Target-Specific
Requirements**, and says a target role should expose *overall readiness, critical
gaps, high-leverage skills, evidence gaps, interview gaps, project gaps*. UX §19
requires *overall readiness, component breakdown, evidence confidence, trend,
explanation* — and "never imply false precision". Blueprint §43 Milestone-4
acceptance: **"skill gaps can be identified", "readiness is reproducible"**.

Milestone 3 has Skills + Career Targets but **not** the Evidence Engine or the
Project Engine (M5). So three of §24's five components have no data.

## Decision

### 1. Readiness = the components that have data in M3, honestly labelled
`CareerReadinessService.readinessFor(careerTargetId, userId)` →
`CareerReadiness` composes:

| §24 component | M3 status | M3 computation |
|--------------|-----------|----------------|
| **Skill Capability** | ✅ available | importance-weighted mean of the user's `skill_assessments.overall` across the target's `career_target_skills`; a target skill with no assessment scores `0` (an unknown skill is a gap, not a neutral) |
| **Interview Readiness** | ✅ available | `interview_weight`-weighted mean of the users' skill `interview` dimension across the target's skills |
| **Target-Specific Requirements** | ✅ available | *is* the weighting — `career_target_skills` (importance / interview_weight / production_weight) defines what counts |
| **Evidence Coverage** | ⏳ M5 | reported as `null` with `status: "not-tracked-until-evidence-engine"` |
| **Project Coverage** | ⏳ M5 | reported as `null` with `status: "not-tracked-until-project-engine"` |

`overall = wСap·skillCapability + wInt·interviewReadiness` with
`wCap = 0.70`, `wInt = 0.30` **among the available components** (they renormalise
to sum to 1). When Evidence + Project land, they take a share and these shrink —
the response's `componentBreakdown` always shows each component's weight and
value, so the change is visible, not silent. These two blend weights are
**configurable** (`orbit.career.readiness.*`) and flagged for sign-off alongside
the skill weights.

### 2. Response shape (UX §19)
```json
{
  "careerTargetId": "…", "targetName": "Platform Engineer / AI Infrastructure",
  "overall": 0.54,
  "confidence": "self-reported",
  "componentBreakdown": [
    { "component": "skillCapability",   "value": 0.58, "weight": 0.70, "status": "computed" },
    { "component": "interviewReadiness", "value": 0.45, "weight": 0.30, "status": "computed" },
    { "component": "evidenceCoverage",   "value": null, "weight": 0.0,  "status": "not-tracked-until-evidence-engine" },
    { "component": "projectCoverage",    "value": null, "weight": 0.0,  "status": "not-tracked-until-project-engine" }
  ],
  "explanation": "Based on 9 self-assessed skills weighted by this target. Evidence and project coverage are not yet tracked.",
  "criticalGaps": [ { "skillId": "…", "skill": "Kubernetes networking", "importance": 0.9, "capability": 0.2, "gap": 0.72 } ],
  "highLeverageSkills": [ { "skillId": "…", "skill": "Observability", "unlocks": 4, "leverage": 0.81 } ],
  "assessedSkillCount": 9, "targetSkillCount": 13
}
```

### 3. Critical gaps & high-leverage skills (Blueprint §24, §31)
- **Critical gap:** a target skill ranked by `importance × (1 − capability)`,
  descending. Top 5 returned.
- **High-leverage skill:** a target skill ranked by how much *other target
  skills* transitively depend on it in the skill DAG
  ([ADR-019](ADR-019-skill-dependency-graph.md)), weighted by their importance
  and their current gap — "closing this unblocks the most valuable work".
  Top 5 returned.

### 4. Reproducibility & trend
`readinessFor(...)` is a pure function of the stored assessment rows +
`career_target_skills` + the skill DAG + the config weights. Given the same
stored data it always returns the same numbers (Blueprint §43). **Trend**
(UX §19 "+8 vs last month") is **deferred** — it needs a `career_readiness_history`
snapshot table (Blueprint mentions it at §46); recorded in `deferred-items.md`.
M3 returns `trend: null`.

### 5. `GET /api/v1/career/readiness` and `GET /api/v1/career/gaps`
`/readiness` for the user's **primary** career target (highest `priority`, then
newest); `?careerTargetId=` selects another. `/gaps` returns just
`criticalGaps` + `highLeverageSkills` (a lighter payload for the dashboard).

## Alternatives Considered
1. **Report `overall` over all five components with the missing ones as `0`** —
   rejected; it would make every M3 readiness score look terrible for reasons
   the user cannot act on, violating UX §19's "explanation" intent.
2. **Only ship Skill Capability in M3, add Interview Readiness later** — rejected;
   interview readiness needs only skill data (`interview` dimension +
   `interview_weight`), which M3 has. Shipping it now is nearly free.
3. **Un-assessed target skill scores `0.5`** — rejected; "I haven't assessed
   this yet" for a high-importance target skill *is* a gap and should pull
   readiness down and surface in `criticalGaps`.

## Consequences
- **Positive:** an honest, reproducible readiness number in M3; the
  component breakdown makes the "what's missing" explicit; adding Evidence /
  Project later is additive and visible.
- **Negative:** M3 readiness is dominated by self-assessment (see
  [ADR-017](ADR-017-skill-scoring-v1.md) — same caveat).
- **Negative:** no trend line in M3. Acceptable; the number is still useful and
  the history table is a clean later addition.

## Decision needed (product owner)
1. The available-component blend `skillCapability 0.70 / interviewReadiness 0.30`
   (§1) — not from any spec; this ADR's proposal.
2. Deferring **trend** and **per-target scoring weights** to a later milestone.
