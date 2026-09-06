# ADR-008: Navigation Structure (UX Spec Authority)

## Status
Accepted

## Date
2026-09-05

## Context
The PRD and UX Spec define different navigation structures:
- **PRD**: Dashboard, Objectives, Roadmap, Skills, Projects, Balance, Analytics | Career Engine, Settings, Profile
- **UX Spec**: Command Center, Objectives, Career, Roadmaps, Skills, Projects, Evidence | Today, Analytics, OSCAR | Settings

Per the Engineering Handoff: UX Spec governs navigation structure and information architecture.

## Decision
We will follow the UX Spec navigation structure:

### Sidebar (Desktop)
**Primary Section:**
1. Command Center (primary landing)
2. Objectives
3. Career
4. Roadmaps
5. Skills
6. Projects
7. Evidence

**Secondary Section (below divider):**
8. Today
9. Analytics
10. OSCAR

**Bottom Section:**
11. Settings

### Mobile Bottom Navigation
`Today | Career | Projects | More`

### Notes for Milestone 0
- All navigation items render but link to placeholder pages.
- No business logic is implemented.
- Balance and SRE are deferred per UX Spec (post-MVP).

## Alternatives Considered
1. **PRD navigation**: Not chosen. UX Spec is authoritative for navigation per Handoff precedence.

## Consequences
- **Positive**: Aligns with the detailed UX specification which includes interaction behaviors, responsive breakpoints, and accessibility requirements.
- **Negative**: Some PRD features (Balance, Dashboard) appear under different names or positions. This is intentional per the UX Spec's refined information architecture.
