# ADR-001: Modular Monolith Architecture

## Status
Accepted

## Date
2026-09-05

## Context
ORBIT is a personal intelligence and execution platform with tightly coupled domain models (objectives, career targets, skills, projects, evidence, daily execution). We need to choose between a microservices architecture and a monolithic approach.

ORBIT is a single-user personal tool. The domain models are deeply interrelated — objectives link to career targets, career targets require skills, skills connect to roadmaps and projects, projects produce evidence, and the priority engine queries across all domains.

## Decision
We will build ORBIT as a **modular monolith** — a single deployable Spring Boot application organized into strictly bounded domain modules:
- `identity`, `objectives`, `career`, `roadmaps`, `skills`, `projects`, `evidence`, `execution`, `analytics`, `ai`, `balance`, `sre`, `shared`

Each module owns its entities, services, repositories, and DTOs. Cross-module communication uses explicit public service interfaces, never direct repository or entity access.

## Alternatives Considered
1. **Microservices**: Rejected. Adds operational complexity (service mesh, distributed transactions, network latency) without corresponding benefit for a single-user tool.
2. **Unstructured Monolith**: Rejected. Would lead to tangled dependencies and make future extraction impossible.

## Consequences
- **Positive**: Simplified deployment, ACID transactions, fast local development, lower cognitive load, clear extraction path if needed later.
- **Negative**: All modules share a single deployment; a bug in one module could affect others. Mitigated by strict module boundaries and comprehensive testing.
