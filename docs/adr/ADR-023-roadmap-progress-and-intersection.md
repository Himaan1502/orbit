# ADR-023: Roadmap Progress & Intersection — Formulas and Heuristics

## Status
Accepted — progress formula, intersection ranking, and recommended-action bands ratified by the product owner 2026-09-08

## Date
2026-09-08

## Context
M4 must render, per UX §20 / Blueprint §32 / PRD §5:
- Roadmap library: coverage / progress per roadmap.
- Roadmap detail: current proficiency per topic, recommended next actions.
- **Roadmap Intersection** ("a signature ORBIT experience"): the skills shared
  across a set of selected roadmaps, ranked so the highest-leverage shared skill
  is obvious.

All of this is **derived** from data ORBIT already has — the user's
`skill_assessments` (M3), `roadmap_topic_skills.relevance` (ADR-021), and the
`SkillGraph` (ADR-019). Nothing here is stored; given the same inputs it returns
the same numbers (Blueprint §43 "reproducible").

The Priority Engine formula and the Skill Scoring formula are **frozen**
(ADR-014, ADR-017) and are not touched.

## Decision

### 1. `RoadmapProgressCalculator` — pure, no I/O

The caller loads the roadmap's topics, each topic's `(skill_id, relevance)`
mappings, the user's latest capability per skill (`overall` from the newest
assessment, **0 if never assessed** — an honest gap, the ADR-018 convention),
and the `SkillGraph`. The calculator returns:

**Roadmap coverage** — relevance-weighted mean capability, exactly the
ADR-018 readiness pattern:
```
coverage = Σ (relevance_i · capability_i) / Σ relevance_i      over the roadmap's distinct skills
```
`0` when the roadmap has no mapped skills. Rendered as a 0–100 %.

**Per-topic status** — from the topic's own relevance-weighted capability
`t = Σ(rel·cap)/Σrel` over that topic's skills:
| `t` | status |
|---|---|
| `≥ 0.70` | `covered` |
| `0.01 – 0.70` | `in_progress` |
| `= 0` (nothing assessed) | `not_started` |

**Recommended next topic** — among `not_started` / `in_progress` topics, rank by:
1. **readiness** first — the fraction of the topic's skills whose *prerequisites*
   (in `SkillGraph`) are already `covered` (≥ 0.70). A topic you can actually
   start now beats one gated behind unlearned prerequisites.
2. then **weighted gap** — `Σ relevance_i · (1 − capability_i)` (bigger = more
   roadmap value unlocked).
3. then `sort_order` (the roadmap's own ordering) as a stable tiebreak.
The top 1–3 are returned with a one-line factual reason.

### 2. Roadmap Intersection

`GET /api/v1/roadmaps/intersections?roadmapIds=<id,id,…>` — when `roadmapIds` is
omitted, the user's **adopted** roadmaps (`user_roadmaps`, ADR-021 §5) are used;
`< 2` roadmaps in the set → `400` (an intersection needs two).

Query (one indexed `GROUP BY`, M5 "queried efficiently"):
```sql
SELECT rts.skill_id, count(DISTINCT rt.roadmap_id) AS in_count
FROM orbit.roadmap_topic_skills rts
JOIN orbit.roadmap_topics rt ON rt.id = rts.roadmap_topic_id
WHERE rt.roadmap_id = ANY(:ids)
GROUP BY rts.skill_id
HAVING count(DISTINCT rt.roadmap_id) >= 2
```

Each shared skill is then enriched in-app:
- `contributesTo` — the roadmaps in the set that contain it (names + tiers).
- `capability` — the user's latest `overall`, or `null` if unassessed.
- `careerRelevance` — the skill's `importance` in the user's **primary** career
  target (highest-priority active one), via a new read port
  `PrimaryCareerSkillWeights` in the `career` domain; `null` if there is no
  target or the skill is not in it. Rendered `HIGH ≥ 0.66 / MEDIUM ≥ 0.4 / LOW`,
  matching the Blueprint §32 example.
- `leverage` — `SkillGraph.leverage(skillId, importanceByTargetSkill,
  gapByTargetSkill)` reused from ADR-018/019, normalised `[0,1]` across the
  shared set.
- `recommendedAction` — see §3.

**Ranking** (most-worth-doing first):
1. `in_count` — appears in more of the selected roadmaps → more leverage.
2. `careerRelevance` (nulls last).
3. `leverage` (graph — unblocks the most other shared work).
4. `gap` = `1 − capability` (nulls treated as `1`).

### 3. `recommendedAction` — capability-band heuristic

A short, factual, ORBIT-authored string (never lifted from a source),
keyed off the user's current capability for that skill:

| capability | action |
|---|---|
| `null` or `< 0.30` | "Start with the fundamentals and a first hands-on exercise." |
| `0.30 – 0.60` | "Build something non-trivial with it end to end." |
| `0.60 – 0.85` | "Deploy and operate a production-style workload." |
| `≥ 0.85` | "Maintain — you're strong here; keep it warm with real use." |

The Blueprint §32 example ("Deploy and observe a production-style workload" at
63/100) sits in the `0.60–0.85` band, which this table matches.

### 4. What needs product-owner sign-off

1. **Topic-status thresholds** — `covered ≥ 0.70`, `not_started = 0`.
2. **Coverage formula** — relevance-weighted mean capability (same shape as
   ADR-018 readiness; reasonable, but it's a choice).
3. **Intersection ranking order** — `in_count → careerRelevance → leverage →
   gap`.
4. **`recommendedAction` bands and wording.**

All are configurable where it's cheap (thresholds via
`@ConfigurationProperties orbit.roadmaps.*`, startup-validated); the ranking
order and the action strings are code. Starting values are proposed here; tune
after the roadmaps have been lived with, as with the M2 daily-plan curves.

### 5. Non-goals (M4)
- No stored progress, no progress history / trend.
- No roadmap-derived Priority Engine signal (ADR-021 §6).
- No "related projects" / "evidence" on roadmap detail (those domains are M5/M6).
- No cross-roadmap *path* generation ("do MLOps then DevSecOps") — the
  intersection shows overlap, it does not sequence roadmaps.

## Alternatives Considered
1. **Unweighted topic completion (count done / total)** — rejected; ignores that
   some topics matter far more to a roadmap than others, and it rewards ticking
   trivial topics. `relevance` weighting is the whole point of §11's column.
2. **Store `progress_percentage` on `user_roadmaps`** — rejected (ADR-021 §5):
   derived value, would drift, needs invalidation on every assessment.
3. **Intersection ranked purely by `in_count`** — rejected; two roadmaps both
   containing "HTML basics" shouldn't outrank a career-critical skill in two
   roadmaps. Career relevance and graph leverage break the tie meaningfully.
4. **LLM-generated `recommendedAction`** — rejected for M4; a deterministic band
   table is reproducible, explainable, and free. OSCAR can enrich later.
5. **`recommendedAction` from the source roadmap's prose** — rejected; that is
   exactly the copied third-party content the PRD forbids.

## Consequences
- **Positive:** every roadmap number is reproducible from stored data and reuses
  the M3 readiness / graph machinery — one mental model across Career and
  Roadmaps.
- **Positive:** the intersection answers "what's the highest-leverage thing that
  counts for several of my directions" — the PRD §5 / UX §20 intent.
- **Negative:** coverage treats an unassessed skill as 0, so a brand-new user
  sees 0 % everywhere until they self-assess. Correct (it *is* a gap) but the
  web copy must frame it as "assess your skills to light this up".
- **Negative:** four tunables and a ranking order are judgement calls shipped as
  defaults. Flagged; cheap to change.
