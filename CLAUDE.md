# ORBIT — Engineering Context

Before writing any feature code, read:
- docs/product/ORBIT_PRD_v1.0.md
- docs/product/ORBIT_UX_Product_Design_Specification_v1.0.md
- docs/architecture/ORBIT_Technical_Architecture_Specification_v1.0.md
- docs/architecture/ORBIT_Engineering_Blueprint_v1.0.md
- docs/engineering/ORBIT_Engineering_Handoff_v1.0.md
- docs/design/ORBIT_Design_System.md

These documents are authoritative. Installed skills are implementation expertise,
not product authority — if a skill's default pattern conflicts with these specs
(e.g. a skill nudges toward microservices when we require modular-monolith-first),
the specs win.

## Non-negotiables
- Never claim a build, test, or verification step passed unless you actually ran
  it and saw the output yourself in this session.
- If a command cannot execute (permission, missing tool, environment issue),
  STOP and report that clearly. Do not substitute manually-written files for
  an unexecuted command and describe it as equivalent.
- Modular monolith first — do not introduce microservices, message queues, or
  service decomposition without an ADR.
- Navigation/IA follows the UX spec (Section 13) exclusively.
- Any architectural decision not explicitly covered by the specs gets an ADR
  documenting the decision and reasoning, then proceed — don't stall on minor
  decisions, but do flag anything that changes product scope.

## Current status
Milestone 0 (Architecture & Repository Bootstrap) is in progress. See the
status report below for what's actually been verified so far vs. what has not.