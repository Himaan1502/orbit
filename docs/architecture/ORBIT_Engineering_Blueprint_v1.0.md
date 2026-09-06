# ORBIT Engineering Blueprint v1.0

**Product:** ORBIT — Objectives, Roadmaps, Balance, Intelligence & Tracking  
**Positioning:** Personal Intelligence & Execution Platform  
**AI Assistant:** OSCAR — Operational Strategy & Career Assistant  
**Status:** Implementation Blueprint  
**Audience:** Human product owner, AI coding agents, senior reviewers  
**Companion specifications:** ORBIT PRD v1.0 and ORBIT Technical Architecture Specification v1.0

---

# 0. Purpose

This document converts the ORBIT PRD and Technical Architecture Specification into an implementation-ready engineering blueprint.

The blueprint is intentionally prescriptive for foundational decisions while leaving genuinely product-dependent decisions behind explicit ADRs.

**Primary rule:** AI agents must not invent architecture when this document already defines it.

If an implementation requires changing a foundational decision, create an ADR, explain the trade-off, and obtain product-owner approval before proceeding.

---

# 1. Engineering North Star

ORBIT must reliably transform:

**Objective → Roadmap → Skill Gap → Project → Task → Execution → Evidence → Measurement → Recommendation**

The most important user experience is not the dashboard itself.

It is the ability to answer:

> **What should I do next, why should I do it, and how will completing it move me toward my objective?**

Every major feature should reinforce that loop.

---

# 2. Non-Negotiable Engineering Principles

1. **Modular monolith first.**
2. **PostgreSQL is the source of truth.**
3. **REST API with `/api/v1`.**
4. **Strong domain boundaries.**
5. **No premature microservices.**
6. **No arbitrary AI writes.**
7. **Deterministic priority calculations before AI explanations.**
8. **Evidence must remain distinguishable from self-reported completion.**
9. **Security is implemented from the beginning, not added at the end.**
10. **Every meaningful feature has tests and acceptance criteria.**
11. **Infrastructure must be reproducible.**
12. **Observability is part of the feature definition of done.**
13. **External roadmap content must not be copied wholesale.**
14. **AI agents must work in small, reviewable increments.**
15. **No production-readiness claim without verification.**

---

# 3. Technology Baseline

## Web

- Next.js
- TypeScript
- App Router
- Tailwind CSS
- shadcn/ui
- TanStack Query
- Recharts
- Zod where runtime validation is useful

## API

- Java
- Spring Boot
- Spring Security
- Spring Data JPA
- Bean Validation
- PostgreSQL
- Flyway or Liquibase

## Runtime

- Docker
- Docker Compose for local development

## Cache / asynchronous work

- Redis where justified
- Spring-compatible durable job mechanism

## Infrastructure

- Terraform
- Kubernetes
- Helm
- GitHub Actions
- Argo CD/GitOps

## Observability

- Micrometer
- Prometheus
- Grafana
- OpenTelemetry
- Structured JSON logging

## Security

- Gitleaks
- Trivy
- Dependency vulnerability scanning
- SAST where practical

---

# 4. Repository Blueprint

Use a monorepo.

```text
orbit/
├── apps/
│   ├── web/
│   │   ├── app/
│   │   ├── components/
│   │   ├── features/
│   │   ├── lib/
│   │   ├── hooks/
│   │   └── tests/
│   │
│   └── api/
│       └── src/
│           ├── main/
│           └── test/
│
├── packages/
│   ├── ui/
│   ├── types/
│   └── config/
│
├── infrastructure/
│   ├── terraform/
│   ├── kubernetes/
│   └── helm/
│
├── docs/
│   ├── architecture/
│   ├── adr/
│   ├── api/
│   ├── product/
│   └── runbooks/
│
├── scripts/
│
├── .github/
│   └── workflows/
│
├── docker-compose.yml
├── README.md
└── LICENSE
```

---

# 5. Backend Domain Boundaries

The backend is one deployable application but is internally divided into domains.

```text
identity/
objectives/
career/
roadmaps/
skills/
projects/
evidence/
execution/
analytics/
ai/
balance/
sre/
shared/
```

## Domain rule

A domain owns:

- entities
- application services
- domain rules
- repositories/interfaces
- DTO mappings
- validation specific to the domain

Cross-domain communication should use explicit application interfaces rather than reaching into another domain's internals.

---

# 6. Core Data Model

The initial relational model is:

```text
User
 ├── UserProfile
 ├── CareerTarget
 ├── Objective
 ├── Project
 ├── Task
 ├── DailyPlan
 ├── SkillAssessment
 ├── MetricEntry
 ├── WeeklyReview
 ├── SLO
 ├── Incident
 └── Integration

CareerTarget
 ├── CareerTargetSkill
 └── Objective

Roadmap
 └── RoadmapTopic
       └── RoadmapTopicSkill
              └── Skill

Skill
 ├── SkillDependency
 ├── SkillRoadmap
 ├── SkillAssessment
 └── SkillEvidence

Project
 ├── ProjectSkill
 └── Evidence

Objective
 ├── Milestone
 └── Task

AIConversation
 └── AIMessage
```

---

# 7. PostgreSQL Schema Blueprint

The exact column types can be refined during implementation, but the following logical schema is authoritative for v1.

## 7.1 users

```text
users
- id UUID PK
- email VARCHAR UNIQUE NOT NULL
- display_name VARCHAR
- status VARCHAR NOT NULL
- created_at TIMESTAMPTZ NOT NULL
- updated_at TIMESTAMPTZ NOT NULL
```

## 7.2 user_profiles

```text
user_profiles
- user_id UUID PK/FK users.id
- timezone VARCHAR NOT NULL
- locale VARCHAR
- available_daily_minutes INTEGER
- preferences JSONB
- created_at TIMESTAMPTZ
- updated_at TIMESTAMPTZ
```

Keep flexible preferences in JSONB only for genuinely user-configurable, low-query fields.

---

# 8. Career Schema

## career_targets

```text
career_targets
- id UUID PK
- user_id UUID FK
- name VARCHAR NOT NULL
- role_family VARCHAR
- specialization VARCHAR
- target_readiness NUMERIC
- target_date DATE
- priority INTEGER
- status VARCHAR
- created_at TIMESTAMPTZ
- updated_at TIMESTAMPTZ
```

## career_target_skills

```text
career_target_skills
- career_target_id UUID FK
- skill_id UUID FK
- importance NUMERIC
- interview_weight NUMERIC
- production_weight NUMERIC
PRIMARY KEY (career_target_id, skill_id)
```

---

# 9. Objective Schema

## objectives

```text
objectives
- id UUID PK
- user_id UUID FK
- career_target_id UUID NULL FK
- title VARCHAR NOT NULL
- description TEXT
- priority INTEGER
- status VARCHAR
- target_date DATE
- progress NUMERIC
- created_at TIMESTAMPTZ
- updated_at TIMESTAMPTZ
```

## milestones

```text
milestones
- id UUID PK
- objective_id UUID FK
- title VARCHAR NOT NULL
- description TEXT
- target_date DATE
- status VARCHAR
- sort_order INTEGER
- created_at TIMESTAMPTZ
- updated_at TIMESTAMPTZ
```

---

# 10. Task / Execution Schema

## tasks

```text
tasks
- id UUID PK
- user_id UUID FK
- objective_id UUID NULL FK
- milestone_id UUID NULL FK
- project_id UUID NULL FK
- skill_id UUID NULL FK
- title VARCHAR NOT NULL
- description TEXT
- estimated_minutes INTEGER
- priority_score NUMERIC
- priority_reason JSONB
- status VARCHAR
- due_at TIMESTAMPTZ
- completed_at TIMESTAMPTZ
- created_at TIMESTAMPTZ
- updated_at TIMESTAMPTZ
```

`priority_reason` should contain explainable factors, not an opaque model response.

Example:

```json
{
  "career_relevance": 0.9,
  "skill_gap": 0.8,
  "dependency_impact": 0.7,
  "project_impact": 0.9,
  "urgency": 0.4
}
```

## daily_plans

```text
daily_plans
- id UUID PK
- user_id UUID FK
- plan_date DATE
- available_minutes INTEGER
- status VARCHAR
- generated_by VARCHAR
- created_at TIMESTAMPTZ
- updated_at TIMESTAMPTZ
```

## daily_plan_tasks

```text
daily_plan_tasks
- daily_plan_id UUID FK
- task_id UUID FK
- position INTEGER
- allocated_minutes INTEGER
PRIMARY KEY (daily_plan_id, task_id)
```

---

# 11. Roadmap Schema

## roadmaps

```text
roadmaps
- id UUID PK
- name VARCHAR NOT NULL
- source_name VARCHAR NOT NULL
- source_url TEXT NOT NULL
- external_identifier VARCHAR
- description TEXT
- status VARCHAR
- last_verified_at TIMESTAMPTZ
- created_at TIMESTAMPTZ
- updated_at TIMESTAMPTZ
```

## roadmap_topics

```text
roadmap_topics
- id UUID PK
- roadmap_id UUID FK
- parent_topic_id UUID NULL FK roadmap_topics.id
- name VARCHAR NOT NULL
- external_identifier VARCHAR
- source_url TEXT
- sort_order INTEGER
- created_at TIMESTAMPTZ
- updated_at TIMESTAMPTZ
```

## skills

```text
skills
- id UUID PK
- canonical_name VARCHAR UNIQUE NOT NULL
- description TEXT
- category VARCHAR
- status VARCHAR
- created_at TIMESTAMPTZ
- updated_at TIMESTAMPTZ
```

## roadmap_topic_skills

```text
roadmap_topic_skills
- roadmap_topic_id UUID FK
- skill_id UUID FK
- relevance NUMERIC
PRIMARY KEY (roadmap_topic_id, skill_id)
```

This allows multiple roadmap topics to map to one canonical skill.

---

# 12. Skill Graph

## skill_dependencies

```text
skill_dependencies
- prerequisite_skill_id UUID FK skills.id
- dependent_skill_id UUID FK skills.id
- dependency_strength NUMERIC
PRIMARY KEY (prerequisite_skill_id, dependent_skill_id)
```

Do not create cycles unless explicitly justified.

The system should validate the graph during imports and administrative edits.

## skill_assessments

```text
skill_assessments
- id UUID PK
- user_id UUID FK
- skill_id UUID FK
- knowledge_score NUMERIC
- practical_score NUMERIC
- evidence_score NUMERIC
- interview_score NUMERIC
- production_score NUMERIC
- overall_score NUMERIC
- assessment_source VARCHAR
- assessed_at TIMESTAMPTZ
```

Scores are estimates, not objective facts.

---

# 13. Evidence Schema

## evidence

```text
evidence
- id UUID PK
- user_id UUID FK
- type VARCHAR NOT NULL
- title VARCHAR NOT NULL
- description TEXT
- source_url TEXT
- external_identifier VARCHAR
- verification_status VARCHAR
- metadata JSONB
- occurred_at TIMESTAMPTZ
- created_at TIMESTAMPTZ
- updated_at TIMESTAMPTZ
```

## skill_evidence

```text
skill_evidence
- skill_id UUID FK
- evidence_id UUID FK
- contribution_weight NUMERIC
PRIMARY KEY (skill_id, evidence_id)
```

## project_evidence

```text
project_evidence
- project_id UUID FK
- evidence_id UUID FK
PRIMARY KEY (project_id, evidence_id)
```

Evidence must never automatically imply mastery.

---

# 14. Project Schema

## projects

```text
projects
- id UUID PK
- user_id UUID FK
- objective_id UUID NULL FK
- name VARCHAR NOT NULL
- description TEXT
- status VARCHAR
- repository_url TEXT
- demo_url TEXT
- started_at TIMESTAMPTZ
- completed_at TIMESTAMPTZ
- created_at TIMESTAMPTZ
- updated_at TIMESTAMPTZ
```

## project_skills

```text
project_skills
- project_id UUID FK
- skill_id UUID FK
- expected_contribution NUMERIC
- demonstrated_contribution NUMERIC
PRIMARY KEY (project_id, skill_id)
```

---

# 15. Analytics Schema

## metrics

```text
metrics
- id UUID PK
- user_id UUID FK
- key VARCHAR NOT NULL
- name VARCHAR NOT NULL
- unit VARCHAR
- direction VARCHAR
- target_value NUMERIC
- created_at TIMESTAMPTZ
```

## metric_entries

```text
metric_entries
- id UUID PK
- metric_id UUID FK
- value NUMERIC NOT NULL
- recorded_at TIMESTAMPTZ
- source VARCHAR
```

Analytics should derive trends from raw entries where possible.

---

# 16. Weekly Review Schema

## weekly_reviews

```text
weekly_reviews
- id UUID PK
- user_id UUID FK
- week_start DATE
- week_end DATE
- summary TEXT
- generated_by VARCHAR
- status VARCHAR
- created_at TIMESTAMPTZ
```

Keep generated summaries separate from raw metrics.

---

# 17. SRE Personal Layer

## slos

```text
slos
- id UUID PK
- user_id UUID FK
- name VARCHAR NOT NULL
- metric_key VARCHAR NOT NULL
- target NUMERIC NOT NULL
- window_type VARCHAR
- window_value INTEGER
- status VARCHAR
- created_at TIMESTAMPTZ
- updated_at TIMESTAMPTZ
```

## incidents

```text
incidents
- id UUID PK
- user_id UUID FK
- title VARCHAR NOT NULL
- severity VARCHAR
- status VARCHAR
- detected_at TIMESTAMPTZ
- resolved_at TIMESTAMPTZ
- probable_cause TEXT
- remediation TEXT
- created_at TIMESTAMPTZ
- updated_at TIMESTAMPTZ
```

The SRE layer should initially be optional and must not dominate the core UX.

---

# 18. AI Schema

## ai_conversations

```text
ai_conversations
- id UUID PK
- user_id UUID FK
- title VARCHAR
- created_at TIMESTAMPTZ
- updated_at TIMESTAMPTZ
```

## ai_messages

```text
ai_messages
- id UUID PK
- conversation_id UUID FK
- role VARCHAR
- content TEXT
- model_provider VARCHAR
- model_name VARCHAR
- prompt_version VARCHAR
- token_usage JSONB
- created_at TIMESTAMPTZ
```

Never persist unnecessary sensitive context.

---

# 19. Integration Schema

## integrations

```text
integrations
- id UUID PK
- user_id UUID FK
- provider VARCHAR NOT NULL
- external_account_id VARCHAR
- status VARCHAR
- scopes JSONB
- encrypted_credentials_reference VARCHAR
- created_at TIMESTAMPTZ
- updated_at TIMESTAMPTZ
```

Credentials must never be stored as plaintext in PostgreSQL.

---

# 20. API Contract

Base URL:

```text
/api/v1
```

## Identity

```text
GET  /api/v1/me
GET  /api/v1/me/preferences
PUT  /api/v1/me/preferences
```

## Dashboard

```text
GET /api/v1/dashboard
GET /api/v1/dashboard/today
```

## Objectives

```text
GET    /api/v1/objectives
POST   /api/v1/objectives
GET    /api/v1/objectives/{id}
PATCH  /api/v1/objectives/{id}
DELETE /api/v1/objectives/{id}
```

## Tasks

```text
GET    /api/v1/tasks
POST   /api/v1/tasks
GET    /api/v1/tasks/{id}
PATCH  /api/v1/tasks/{id}
POST   /api/v1/tasks/{id}/complete
```

## Career

```text
GET  /api/v1/career/targets
POST /api/v1/career/targets
GET  /api/v1/career/readiness
GET  /api/v1/career/gaps
```

## Roadmaps

```text
GET /api/v1/roadmaps
GET /api/v1/roadmaps/{id}
GET /api/v1/roadmaps/{id}/topics
GET /api/v1/roadmaps/intersections
```

## Skills

```text
GET   /api/v1/skills
GET   /api/v1/skills/{id}
GET   /api/v1/skills/{id}/dependencies
GET   /api/v1/skills/{id}/evidence
POST  /api/v1/skills/{id}/assessments
```

## Projects

```text
GET   /api/v1/projects
POST  /api/v1/projects
GET   /api/v1/projects/{id}
PATCH /api/v1/projects/{id}
```

## Evidence

```text
GET  /api/v1/evidence
POST /api/v1/evidence
GET  /api/v1/evidence/{id}
```

## Daily planning

```text
GET  /api/v1/daily-plans/{date}
POST /api/v1/daily-plans/generate
POST /api/v1/daily-plans/{id}/regenerate
```

## OSCAR

```text
POST /api/v1/oscar/chat
GET  /api/v1/oscar/conversations
GET  /api/v1/oscar/conversations/{id}
```

AI write tools must be confirmation-gated.

---

# 21. API Response Standard

Success responses should be predictable.

For single resources:

```json
{
  "data": {}
}
```

For collections:

```json
{
  "data": [],
  "pagination": {
    "page": 0,
    "pageSize": 20,
    "total": 100
  }
}
```

Errors:

```json
{
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "The request is invalid.",
    "details": [
      {
        "field": "title",
        "message": "Title is required."
      }
    ],
    "requestId": "..."
  }
}
```

Never expose stack traces or internal implementation details.

---

# 22. Priority Engine v1

The priority engine must be deterministic.

Suggested normalized inputs:

```text
careerRelevance
skillGap
dependencyImpact
projectImpact
interviewValue
urgency
availableTimeFit
historicalCompletionProbability
```

Initial formula:

```text
priority =
  0.20 * careerRelevance +
  0.20 * skillGap +
  0.15 * dependencyImpact +
  0.15 * projectImpact +
  0.10 * interviewValue +
  0.10 * urgency +
  0.05 * availableTimeFit +
  0.05 * historicalCompletionProbability
```

All factors are normalized to 0–1.

This is a starting implementation, not a scientifically validated model.

The API should return the factors that produced the score.

---

# 23. Skill Scoring v1

Initial overall score:

```text
overall =
  0.20 * knowledge +
  0.25 * practical +
  0.20 * evidence +
  0.15 * interview +
  0.20 * production
```

The weights should eventually become configurable by career target.

For example, an SRE target may assign greater production weight than a general software-development target.

Never display:

> "You are 78% skilled."

Prefer:

> "ORBIT estimates your current capability at 78/100 based on these signals."

---

# 24. Career Readiness

Career readiness should aggregate:

```text
Skill Capability
+
Evidence
+
Project Coverage
+
Interview Readiness
+
Target-Specific Requirements
```

A target role can expose:

```text
Overall readiness
Critical gaps
High-leverage skills
Evidence gaps
Interview gaps
Project gaps
```

---

# 25. Roadmap Ingestion

Initial roadmap sources include:

- MLOps
- Forward Deployed Engineer
- LeetCode
- System Design
- DevOps
- DevSecOps
- AI Engineer
- Machine Learning
- Frontend
- Backend
- Full Stack
- Blockchain
- Cyber Security

## Important legal/engineering constraint

ORBIT should store:

- roadmap name
- source
- source URL
- external identifier
- normalized topic names
- normalized skills
- dependencies
- relevance

Do not reproduce large portions of third-party roadmap prose.

## Import process

```text
Source
  ↓
Fetcher
  ↓
Parser
  ↓
Normalizer
  ↓
Skill Matcher
  ↓
Dependency Validator
  ↓
Review Queue
  ↓
Database
```

Imports should be versioned and repeatable.

---

# 26. OSCAR Architecture

OSCAR is an orchestration layer, not a direct database chatbot.

```text
User
 ↓
OSCAR API
 ↓
Intent / Request Classification
 ↓
Context Builder
 ↓
Policy Guard
 ↓
Tool Selection
 ↓
AI Provider
 ↓
Structured Response
 ↓
Response Validator
 ↓
User
```

## Context builder

Retrieve only relevant context.

Example:

If the user asks:

> "What should I study tonight?"

OSCAR should retrieve:

- current objectives
- active career target
- high-priority skill gaps
- current projects
- incomplete tasks
- available time
- recent execution

It should not retrieve unrelated financial or personal data.

---

# 27. OSCAR Tool Registry

Initial tools:

```text
get_current_objectives
get_active_career_target
get_skill_gap
get_skill_dependencies
get_project_status
get_today_plan
get_recent_execution
get_weekly_metrics
get_roadmap_intersection
propose_daily_plan
propose_task
propose_objective_change
generate_weekly_review
```

Mutation tools:

```text
create_task
complete_task
update_objective
create_project
record_evidence
```

Mutation tools must require explicit confirmation unless the product later introduces a trusted automation mode with clear user controls.

---

# 28. AI Provider Abstraction

Do not couple the domain to one AI vendor.

```text
AIService
 ├── ProviderAdapter
 │    ├── Provider A
 │    ├── Provider B
 │    └── Provider C
 │
 ├── ContextBuilder
 ├── ToolRegistry
 ├── PolicyGuard
 └── ResponseValidator
```

Store:

- provider
- model
- prompt version
- usage
- latency
- status

This enables later model routing and cost controls.

---

# 29. Frontend Information Architecture

**Canonical source:** `docs/product/ORBIT_UX_Product_Design_Specification_v1.0.md`, Section 13 (Information Architecture) and Section 7 (Navigation Architecture).

This Blueprint does not define its own navigation tree. Frontend implementation must follow the UX specification's flat top-level structure (Command Center, Objectives, Career, Roadmaps, Skills, Projects, Evidence, Today, Analytics, OSCAR, Balance, SRE, Settings) rather than any nested variant. If a future engineering need appears to require a different navigation shape, raise an ADR against the UX spec rather than diverging here.

For MVP, only expose the modules that are actually implemented.

---

# 30. Command Center Layout

The home page should prioritize action.

Recommended order:

```text
1. Today's primary objective
2. Top 3 actions
3. Current trajectory
4. Critical skill gaps
5. Active projects
6. Execution health
7. Upcoming deadlines
8. Recent evidence
9. OSCAR insight
```

Avoid turning the dashboard into a wall of charts.

---

# 31. Career Trajectory UI

The central career page should show:

```text
TARGET
Platform Engineer / AI Infrastructure

Readiness
54 / 100

Critical gaps
- Observability
- Distributed Systems
- Kubernetes Production
- Security

Highest leverage
Observability

Next action
Implement OpenTelemetry tracing in Project X
```

The user should be able to drill from:

**Target → Skill → Gap → Project → Task → Evidence**

---

# 32. Roadmap Intersection UI

Display shared skills.

Example:

```text
Kubernetes

Contributes to:
✓ DevOps
✓ MLOps
✓ DevSecOps
✓ Platform Engineering

Career relevance:
HIGH

Current capability:
63/100

Recommended next action:
Deploy and observe a production-style workload.
```

---

# 33. Today UI

Today must be extremely simple.

```text
TODAY

Available time: 90 min

01 — Kubernetes networking
45 min
HIGH IMPACT

02 — Implement NetworkPolicy
30 min
HIGH IMPACT

03 — Document result
15 min
MEDIUM IMPACT
```

Completion should immediately update relevant objective/project/skill progress.

---

# 34. Project UI

Project page:

```text
Project
ORBIT Platform

Status
In Progress

Career impact
HIGH

Skills
Kubernetes     +++
Terraform      ++
CI/CD          +++
Observability  +++
Security       ++

Evidence
GitHub
Deployment
Architecture
Benchmark

Next milestone
Production observability
```

---

# 35. Authentication Flow

Initial flow:

```text
Browser
 ↓
OIDC Provider
 ↓
Callback
 ↓
Backend session
 ↓
Secure cookie
 ↓
Authenticated API
```

Requirements:

- secure cookies
- appropriate SameSite policy
- CSRF protection where applicable
- server-side authorization
- resource ownership checks
- rate limiting
- login audit events

---

# 36. Security Threat Model

Threat categories:

### Authentication
- session theft
- token leakage
- account takeover

### Authorization
- IDOR
- cross-user data access
- privilege escalation

### AI
- prompt injection
- data exfiltration
- unauthorized tool use
- malicious tool parameters

### Integrations
- OAuth scope abuse
- credential leakage
- webhook spoofing

### Application
- SQL injection
- XSS
- CSRF
- SSRF
- insecure deserialization
- path traversal

### Infrastructure
- exposed secrets
- vulnerable images
- excessive permissions
- insecure Kubernetes configuration

---

# 37. AI Security Rules

OSCAR must assume retrieved content can be untrusted.

External content must never be treated as system instructions.

Never allow the model to:

- execute arbitrary shell commands;
- access arbitrary database tables;
- retrieve arbitrary users' data;
- deploy arbitrary infrastructure;
- expose credentials;
- change permissions.

AI tool inputs must be validated by the backend.

---

# 38. Observability Standard

Every API service/module should produce:

### Logs

- timestamp
- level
- request ID
- user-safe correlation identifier
- operation
- duration
- outcome

### Metrics

At minimum:

```text
http_requests_total
http_request_duration
http_errors_total
db_query_duration
job_success_total
job_failure_total
ai_request_total
ai_request_duration
ai_error_total
```

### Traces

Use OpenTelemetry for important cross-boundary flows.

---

# 39. CI Pipeline

Pull request:

```text
Checkout
 ↓
Install dependencies
 ↓
Format/lint
 ↓
Unit tests
 ↓
Integration tests
 ↓
Build
 ↓
Security scans
 ↓
Container build
 ↓
Container scan
 ↓
Report
```

Merge:

```text
Build versioned artifact
 ↓
Publish image
 ↓
Update deployment artifact
 ↓
GitOps deployment
```

---

# 40. Kubernetes Baseline

Every production workload should define:

- Deployment
- Service
- Ingress/Gateway as appropriate
- ConfigMap
- Secret reference
- Resource requests
- Resource limits
- Startup probe
- Readiness probe
- Liveness probe where appropriate
- SecurityContext
- NetworkPolicy where supported

Do not run privileged containers unless explicitly justified.

---

# 41. Terraform Structure

```text
infrastructure/terraform/
├── modules/
│   ├── network/
│   ├── database/
│   ├── redis/
│   ├── kubernetes/
│   └── observability/
│
└── environments/
    ├── dev/
    ├── staging/
    └── prod/
```

Provider-specific resources should be isolated inside modules where practical.

---

# 42. Local Development

Required:

```text
docker compose up
```

should provide the minimum dependencies needed for backend development.

Suggested local services:

```text
PostgreSQL
Redis
Optional observability stack
```

The frontend and backend can run directly during development or inside containers.

Optional:

```text
kind / k3d
```

for Kubernetes testing.

---

# 43. Testing Strategy

## Unit

Focus on:

- priority engine
- skill scoring
- roadmap normalization
- dependency validation
- authorization rules
- domain logic

## Integration

Test:

- PostgreSQL
- migrations
- repository behavior
- authentication
- authorization
- API contracts

## E2E

Critical paths:

1. Login
2. Create objective
3. Create task
4. Generate daily plan
5. Create career target
6. Inspect skill gap
7. Add project
8. Add evidence
9. View career readiness
10. Ask OSCAR
11. Confirm an OSCAR mutation

---

# 44. Test Data

Create deterministic seed data for local development.

Example career target:

```text
Platform Engineer / AI Infrastructure
```

Example skills:

```text
Linux
Networking
Git
Python
Docker
Kubernetes
Terraform
CI/CD
Observability
Security
System Design
Distributed Systems
MLOps
```

Example project:

```text
ORBIT Platform
```

Seed data must never be used as production user data.

---

# 45. Feature Development Protocol

Every feature must follow:

```text
Requirement
 ↓
Acceptance Criteria
 ↓
Technical Design
 ↓
Implementation
 ↓
Unit Tests
 ↓
Integration Tests
 ↓
Security Review
 ↓
E2E Verification
 ↓
Documentation
 ↓
Code Review
 ↓
Merge
```

AI agents must not skip stages because a feature appears simple.

---

# 46. Antigravity Agent Operating Model

Antigravity is the primary implementation environment.

Recommended agents:

### ORBIT Architect
Owns architecture consistency and ADRs.

### ORBIT Backend Engineer
Owns Spring Boot domain/API implementation.

### ORBIT Frontend Engineer
Owns Next.js UI.

### ORBIT Database Engineer
Owns migrations/schema/indexes.

### ORBIT DevOps Engineer
Owns Docker/CI/CD/Terraform/Kubernetes.

### ORBIT SRE Engineer
Owns reliability/observability.

### ORBIT Security Engineer
Owns threat modeling/security controls.

### ORBIT QA Engineer
Owns automated/E2E/browser verification.

### ORBIT Documentation Engineer
Owns docs/runbooks.

Agents must read:
1. PRD
2. Technical Architecture
3. Engineering Blueprint
4. Relevant ADRs

before implementation.

---

# 47. Claude Code Operating Model

Claude Code is the independent senior reviewer.

After each major milestone:

```text
Review
 ↓
Findings
 ↓
Severity classification
 ↓
Issue/task creation
 ↓
Fix
 ↓
Re-review
```

Review categories:

```text
CRITICAL
HIGH
MEDIUM
LOW
```

Claude Code should initially review without modifying the repository unless explicitly asked to implement a fix.

---

# 48. Git Strategy

Use short-lived feature branches.

```text
main
  │
  ├── feat/objectives
  ├── feat/roadmaps
  ├── feat/skills
  ├── feat/oscar
  └── fix/...
```

Every meaningful change should have:

- clear commit message
- tests
- reviewable scope

Avoid giant AI-generated commits covering unrelated domains.

---

# 49. ADR Policy

Create an ADR whenever changing:

- architecture style
- database strategy
- authentication
- AI provider abstraction
- external integration strategy
- deployment strategy
- major dependency
- domain boundary
- data ownership

ADR template:

```text
# ADR-NNN: Title

## Context

## Decision

## Alternatives

## Consequences

## Status
```

---

# 50. MVP Implementation Sequence

## Milestone 0 — Bootstrap

Deliver:

- monorepo
- Next.js app
- Spring Boot app
- PostgreSQL
- Redis
- Docker Compose
- CI
- basic docs
- local development instructions

Acceptance:

- clean checkout can start locally;
- frontend reaches backend;
- backend reaches database;
- migrations run automatically;
- CI is green.

---

## Milestone 1 — Identity

Deliver:

- authentication
- user profile
- authorization
- secure session handling

Acceptance:

- user can sign in;
- user cannot access another user's resources;
- logout works;
- unauthorized requests fail safely.

---

## Milestone 2 — Objectives + Tasks

Deliver:

- objectives
- milestones
- tasks
- completion
- basic priority score

Acceptance:

- objective can be created;
- tasks can map to objective/milestone;
- completion updates progress;
- priority is explainable.

---

## Milestone 3 — Command Center

Deliver:

- today's priorities
- objectives
- execution summary
- upcoming deadlines

Acceptance:

- dashboard reflects real database state;
- no hard-coded progress;
- critical actions are usable on desktop and mobile widths.

---

## Milestone 4 — Career Engine

Deliver:

- career targets
- skills
- dependencies
- skill assessments
- readiness calculation

Acceptance:

- career target can be configured;
- skill gaps can be identified;
- dependency graph is navigable;
- readiness is reproducible.

---

## Milestone 5 — Roadmaps

Deliver:

- roadmap schema
- normalized topics
- canonical skills
- intersections
- source attribution

Acceptance:

- one skill can belong to multiple roadmaps;
- dependencies are validated;
- intersections can be queried efficiently.

---

## Milestone 6 — Projects + Evidence

Deliver:

- projects
- project skills
- evidence
- career evidence view

Acceptance:

- project contributes to mapped skills;
- evidence is linked to skills/projects;
- self-reported completion is distinct from evidence.

---

## Milestone 7 — Daily Planning

Deliver:

- available-time input
- daily plan generation
- task allocation
- completion tracking

Acceptance:

- plan respects available time;
- high-impact work is prioritized;
- recommendations are explainable.

---

## Milestone 8 — Analytics

Deliver:

- trends
- execution metrics
- skill progression
- career readiness history

Acceptance:

- analytics derive from persisted events/data;
- time ranges are correct;
- no fabricated metrics.

---

## Milestone 9 — OSCAR

Deliver:

- chat
- context builder
- tool registry
- provider abstraction
- confirmation-gated mutations

Acceptance:

- OSCAR can answer context-aware questions;
- recommendations use ORBIT data;
- mutations require confirmation;
- failed provider requests degrade gracefully;
- AI usage is observable.

---

## Milestone 10 — Production Platform

Deliver:

- Terraform
- Kubernetes
- GitOps
- observability
- security gates
- backups
- runbooks

Acceptance:

- deployment is reproducible;
- rollback is documented/tested;
- health checks work;
- alerts work;
- security scans are integrated.

---

# 51. Post-MVP Modules

Implement after the core system proves useful:

1. Fitness
2. Finance
3. Habits
4. Cricket
5. Personal SLOs
6. Error budgets
7. Personal incidents
8. Advanced AI planning
9. Additional integrations

Do not allow these modules to delay the core Objective → Skill → Project → Execution loop.

---

# 52. Production Readiness Checklist

## Application

- [ ] Authentication secure
- [ ] Authorization verified
- [ ] Validation complete
- [ ] Rate limiting
- [ ] Error handling
- [ ] Database migrations
- [ ] Backups
- [ ] Health checks
- [ ] Observability

## Security

- [ ] Secret scan
- [ ] Dependency scan
- [ ] Container scan
- [ ] Security headers
- [ ] Least privilege
- [ ] OAuth scope review
- [ ] AI tool authorization review

## Reliability

- [ ] Timeouts
- [ ] Safe retries
- [ ] Graceful degradation
- [ ] Resource limits
- [ ] Readiness/liveness strategy
- [ ] Rollback procedure
- [ ] Restore procedure

## Testing

- [ ] Unit
- [ ] Integration
- [ ] API
- [ ] E2E
- [ ] Browser verification
- [ ] Infrastructure validation

## Documentation

- [ ] README
- [ ] Architecture
- [ ] ADRs
- [ ] API docs
- [ ] Deployment guide
- [ ] Runbooks
- [ ] Incident procedures

---

# 53. Definition of Done

A feature is complete only if:

1. Acceptance criteria are satisfied.
2. Code is formatted and linted.
3. Unit tests exist for meaningful domain logic.
4. Integration tests exist where persistence/security/integration behavior matters.
5. E2E tests exist for critical user workflows.
6. Security implications were reviewed.
7. Observability was considered.
8. Documentation was updated.
9. No unrelated code was modified.
10. The change has been reviewed.
11. Browser verification was completed for user-facing workflows where applicable.

---

# 54. Definition of Production Grade

ORBIT is production-grade when the system can be:

- deployed reproducibly;
- monitored;
- debugged;
- rolled back;
- restored;
- secured;
- tested;
- upgraded;
- operated by someone other than the original developer.

A successful build is not sufficient evidence of production readiness.

---

# 55. Initial Open Decisions

These require explicit ADRs before the relevant milestone:

- exact hosting provider combination;
- exact OIDC provider;
- exact AI model/provider routing;
- durable job implementation;
- Redis necessity beyond MVP;
- object storage;
- initial career-weight configuration;
- roadmap import mechanism;
- backup retention;
- RPO/RTO targets.

---

# 56. First Antigravity Instruction

Use the following as the first high-level implementation instruction after adding the three ORBIT specification files to the repository:

```text
You are the lead engineering team for ORBIT.

Read these documents completely before changing code:
1. docs/product/ORBIT_PRD_v1.0.md
2. docs/product/ORBIT_UX_Product_Design_Specification_v1.0.md
3. docs/architecture/ORBIT_Technical_Architecture_Specification_v1.0.md
4. docs/architecture/ORBIT_Engineering_Blueprint_v1.0.md
5. docs/design/ORBIT_Design_System.md

Do not start feature implementation yet.

First perform Phase 0 — Architecture & Repository Bootstrap.

Tasks:
1. Inspect the repository.
2. Create the agreed monorepo structure.
3. Bootstrap the Next.js frontend.
4. Bootstrap the Spring Boot backend.
5. Configure PostgreSQL and Redis for local development.
6. Add Docker Compose.
7. Add database migration infrastructure.
8. Add initial CI pipeline.
9. Add formatting/linting/testing foundations.
10. Create the initial ADRs specified by the architecture.
11. Create development documentation.
12. Verify that a clean checkout can run locally.
13. Run all available tests and checks.

Do NOT implement business features yet.

Before changing foundational architecture, create an ADR and stop for product-owner approval.

At the end, produce:
- files created/changed;
- commands used;
- verification results;
- known issues;
- decisions requiring approval;
- recommended next milestone.

Never claim a task is complete without verification.
```

---

# 57. First Claude Code Review Instruction

After Phase 0 is complete:

```text
Act as an independent Staff+ engineer reviewing ORBIT.

Read:
1. ORBIT_PRD_v1.0.md
2. ORBIT_Technical_Architecture_Specification_v1.0.md
3. ORBIT_Engineering_Blueprint_v1.0.md
4. all current ADRs

Do not modify the repository.

Audit:
- architecture
- repository structure
- dependency choices
- security
- database strategy
- CI
- local development
- testing
- maintainability
- production readiness

Identify:
CRITICAL / HIGH / MEDIUM / LOW findings.

For every finding provide:
- location
- problem
- why it matters
- recommended fix
- whether it blocks Phase 1

Do not praise the implementation unless necessary. Focus on finding problems before they become expensive.
```

---

# 58. Engineering Philosophy

ORBIT itself should demonstrate the engineering capabilities it is intended to help develop.

The project should therefore visibly demonstrate:

```text
Software Engineering
        +
Cloud
        +
Infrastructure as Code
        +
Containers
        +
Kubernetes
        +
CI/CD
        +
GitOps
        +
Observability
        +
Security
        +
AI Engineering
        +
System Design
        +
SRE
```

The implementation should favor **real engineering evidence over technology-counting**.

Using Kubernetes just to say "Kubernetes" is not the goal.

Using Kubernetes to solve a deployment, reliability, scaling or operational problem is.

---

# 59. Long-Term Evolution

The intended evolution is:

```text
Phase 1
Modular Monolith
      ↓
Phase 2
Production-grade Monolith
      ↓
Phase 3
Observed + Automated Platform
      ↓
Phase 4
Selective Service Extraction
      ↓
Phase 5
AI-native Personal Intelligence Platform
```

Service extraction should happen only when justified by:

- scale;
- independent deployment;
- reliability isolation;
- team ownership;
- resource characteristics;
- security boundary.

Never split services merely because microservices look impressive on a resume.

---

# 60. Final Engineering Principle

ORBIT is simultaneously:

1. a useful personal product;
2. a serious software project;
3. a Platform/DevOps/SRE portfolio project;
4. an AI engineering project;
5. an experiment in personal operating systems.

The implementation should optimize for all five without allowing any one of them to destroy the others.

**Build the system you would be proud to operate in production.**
