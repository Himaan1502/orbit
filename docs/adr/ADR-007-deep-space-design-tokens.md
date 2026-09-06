# ADR-007: Deep Space Design Token Set

## Status
Accepted

## Date
2026-09-05

## Context
The specification documents present two different design token sets:
- **PRD**: Uses indigo-based accent colors (`#6366f1`), domain-specific colors (career=indigo, health=emerald, finance=amber, personal=pink), Plus Jakarta Sans + Inter fonts.
- **UX Spec + Design System**: Uses the "Deep Space" palette with cyan accent (`#4FD1E5`), violet secondary (`#8B7CF6`), Geist Sans + Geist Mono fonts.

Per the Engineering Handoff precedence: UX Spec governs visual behavior, Design System governs visual tokens.

## Decision
We will use the **Deep Space** design token set from the UX Spec and Design System document:

### Dark Theme (Primary)
- Background: `#0B0C10`
- Surface: `#14151B`
- Surface Raised: `#1B1D25`
- Border: `#2A2C36`
- Text Primary: `#E9E9EE`
- Text Secondary: `#8B8D98`
- Accent Cyan: `#4FD1E5` (primary CTAs, live/active)
- Accent Violet: `#8B7CF6` (categories, selected states)
- Success: `#3DD68C`
- Warning: `#F5A623`
- Danger: `#F2545B`

### Light Theme
- Background: `#F5F5F7`
- Surface: `#FFFFFF`
- Accent Cyan: `#0EA5B7` (darkened for WCAG AA)
- Accent Violet: `#6D5FE0` (darkened for WCAG AA)

### Typography
- Geist Sans (headings, body, UI)
- Geist Mono (technical metadata, counters)

## Alternatives Considered
1. **PRD indigo-based tokens**: Not chosen. The UX Spec and Design System are more detailed and authoritative for visual design per the Handoff precedence rules.

## Consequences
- **Positive**: Cohesive, WCAG AA-compliant design language with clear semantic roles.
- **Negative**: Domain-specific colors (career=indigo, health=emerald) from the PRD are not directly mapped. Domain colors can be added as needed in future milestones.
