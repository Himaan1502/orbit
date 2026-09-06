# ORBIT Engineering Handoff v1.0

This package contains the master product, design, and technical specifications for implementation by AI coding agents.

## Files
- `docs/product/ORBIT_PRD_v1.0.md` — product requirements and scope.
- `docs/product/ORBIT_UX_Product_Design_Specification_v1.0.md` — canonical UX behavior, information architecture, and navigation. This is the single source of truth for navigation structure; the Engineering Blueprint defers to it rather than defining its own.
- `docs/architecture/ORBIT_Technical_Architecture_Specification_v1.0.md` — engineering architecture and execution constraints.
- `docs/architecture/ORBIT_Engineering_Blueprint_v1.0.md` — implementation-ready schema, API contract, and milestone plan.
- `docs/design/ORBIT_Design_System.md` — palette/typography/component rationale; canonical token values are frozen in the UX spec, Section 3.

## Implementation rule
Read all five documents completely before implementing. Treat them as authoritative unless a documented ADR changes a decision. Work incrementally, test every change, preserve domain boundaries, and never declare production readiness without the Definition of Done.

## Precedence rule
If any two documents appear to conflict, resolve in this order: UX spec governs product-facing behavior and navigation; the Engineering Blueprint governs schema, API, and implementation sequencing; the Technical Architecture Specification governs infrastructure and platform decisions. Do not silently pick one — raise an ADR if a real conflict is found.
