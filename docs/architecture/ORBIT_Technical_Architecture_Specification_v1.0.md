# ORBIT Technical Architecture Specification v1.0

**Status:** Proposed  
**Product:** ORBIT — Personal Intelligence & Execution Platform  
**AI:** OSCAR — Operational Strategy & Career Assistant

## 1. Architecture Goals
- Production-grade foundations without premature microservice complexity.
- Clear separation of domains.
- Strong typing and API contracts.
- Testability and observability from day one.
- Provider-agnostic AI layer.
- Evolvable from modular monolith to services only when justified.
- Free/low-cost deployment path for initial production.

## 2. Architectural Decision: Modular Monolith First
Use a modular monolith for the initial backend rather than microservices.

Reason:
- ORBIT is a single product with strongly related domains.
- Lower operational complexity.
- Easier transactions and local development.
- Faster iteration.
- Clear module boundaries can later be extracted.

Backend modules:
1. Identity
2. Objectives
3. Career
4. Roadmaps
5. Skills
6. Projects
7. Evidence
8. Execution
9. Analytics
10. AI/OSCAR
11. Balance
12. SRE

## 3. High-Level Architecture

    Browser
       |
       v
    Next.js Web App
       |
       v
    Spring Boot API
       |
    +--+-------------------------------+
    |                                  |
    v                                  v
 PostgreSQL                          Redis
    |                                  |
    +----------------+-----------------+
                     |
             Background Jobs
                     |
                     v
              External Integrations
              GitHub / AI Providers

Infrastructure:
Docker → CI/CD → Terraform → Kubernetes → GitOps → Observability/Security.

## 4. Frontend
Technology:
- Next.js
- TypeScript
- App Router
- Tailwind CSS
- shadcn/ui
- TanStack Query
- Recharts

Principles:
- Server components by default where appropriate.
- Client state only when necessary.
- Accessible components.
- Responsive design.
- Feature/domain-based folder structure.

Suggested structure:

    apps/web/
      app/
      components/
      features/
        command-center/
        objectives/
        career/
        roadmaps/
        skills/
        projects/
        execution/
        analytics/
        oscar/
      lib/
      hooks/
      tests/

## 5. Backend
Technology:
- Java
- Spring Boot
- Spring Security
- Spring Data JPA
- Bean Validation
- PostgreSQL driver
- Flyway or Liquibase

Suggested structure:

    apps/api/
      src/main/java/.../orbit/
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

Each domain should own its application/domain logic and expose explicit interfaces.

## 6. Database
PostgreSQL is the source of truth.

Core entities:

    User
    UserProfile
    CareerTarget
    Objective
    Milestone
    Task
    Roadmap
    RoadmapTopic
    Skill
    SkillDependency
    SkillRoadmap
    SkillAssessment
    SkillEvidence
    Project
    ProjectSkill
    Evidence
    DailyPlan
    Metric
    MetricEntry
    WeeklyReview
    SLO
    ErrorBudget
    Incident
    AIConversation
    AIMessage
    Integration

Use UUIDs or equivalent non-sequential identifiers for externally exposed resources.

Important principles:
- UTC timestamps in storage.
- Explicit created_at/updated_at.
- Soft deletion only where justified.
- Database constraints for invariants.
- Migrations committed to source control.
- Index foreign keys and common query paths.
- Avoid JSON blobs for relational data that needs querying.

## 7. Key Relationships

    CareerTarget
       |
       +--> Objective
       +--> Skill
       +--> Project

    Roadmap
       +--> RoadmapTopic
                  +--> Skill

    Skill
       +--> SkillDependency --> Skill
       +--> SkillRoadmap --> Roadmap
       +--> SkillEvidence --> Evidence

    Project
       +--> ProjectSkill --> Skill
       +--> Evidence

    Objective
       +--> Milestone
       +--> Task
       +--> Project
       +--> Skill

## 8. API Design
REST initially.

Base:
    /api/v1

Examples:
    GET    /objectives
    POST   /objectives
    GET    /skills
    GET    /skills/{id}
    GET    /roadmaps
    GET    /projects
    POST   /projects
    GET    /dashboard
    GET    /career/readiness
    POST   /oscar/chat

Requirements:
- consistent error envelope;
- validation errors with field-level detail;
- pagination;
- filtering/sorting;
- idempotency for retry-sensitive commands;
- correlation/request IDs;
- OpenAPI documentation.

## 9. Authentication & Authorization
Initial approach:
- OIDC/OAuth login.
- Short-lived access/session mechanism.
- Secure, HttpOnly, SameSite cookies where appropriate.
- CSRF protection where cookie authentication requires it.
- Server-side authorization on every protected resource.

Roles can initially be:
- USER
- ADMIN

Design permissions so resource ownership is enforced independently of UI.

## 10. Redis
Use Redis only where it provides clear value:
- caching
- rate limiting
- job coordination
- short-lived AI/session state
- distributed locks where required

PostgreSQL remains authoritative.

## 11. Background Jobs
Use a durable job mechanism appropriate to the Spring ecosystem.

Initial jobs:
- roadmap processing
- analytics aggregation
- weekly review generation
- scheduled reminders
- GitHub synchronization
- AI insight generation

Jobs must be:
- idempotent
- retryable
- observable
- bounded by timeouts
- safe against duplicate execution

## 12. OSCAR Architecture

Create an AI provider abstraction:

    AIService
       |
       +--> ProviderAdapter
       +--> ContextBuilder
       +--> ToolRegistry
       +--> PolicyGuard
       +--> ResponseValidator

OSCAR should retrieve structured ORBIT context rather than dumping the entire database into prompts.

Context layers:
1. User request
2. Current objective
3. Career target
4. Relevant skill graph
5. Recent execution
6. Relevant project state
7. User constraints
8. Historical context only when useful

Tool examples:
- get_current_objectives
- get_skill_gap
- get_roadmap_dependencies
- get_project_status
- create_task (confirmation required)
- propose_daily_plan
- generate_weekly_review

Never allow arbitrary database or shell access from the model.

## 13. AI Safety & Reliability
- Provider timeouts.
- Retry only safe operations.
- Rate limits.
- Token/cost budgets.
- Prompt/version tracking.
- Structured output schemas.
- Validation before persistence.
- Human confirmation for consequential actions.
- Audit AI-generated changes.
- Never treat model output as trusted executable code.

## 14. Priority Engine
Start as deterministic domain logic, not an opaque LLM prompt.

Inputs:
- career relevance
- skill gap
- dependency impact
- project impact
- interview value
- urgency
- available time
- historical completion probability

Output:
- priority score
- rank
- explanation factors

OSCAR may explain or suggest adjustments, but the base score should remain reproducible.

## 15. Skill Scoring
Store component scores separately:
- knowledge
- practical ability
- evidence
- interview readiness
- production readiness

Do not claim objective "mastery." Label scores as ORBIT estimates and expose evidence/reasoning.

## 16. Roadmap Integration
Use normalized internal records with source attribution:
- source
- source URL
- external identifier
- topic
- skill
- dependency
- last verified timestamp

Do not copy full copyrighted roadmap content into ORBIT.

Create importers/adapters so roadmap sources can change independently.

## 17. GitHub Integration
Initial capabilities:
- link repositories
- inspect repository metadata
- map repositories to projects
- collect selected contribution evidence
- synchronize periodically

Use least-privilege OAuth scopes.

Never store GitHub credentials directly.

## 18. Observability
Use:
- structured application logs
- Micrometer metrics
- Prometheus
- Grafana
- OpenTelemetry
- distributed trace IDs
- health/readiness endpoints

Important metrics:
- API latency
- error rate
- DB latency
- job success/failure
- AI latency/errors/cost
- queue depth
- authentication failures
- integration failures

## 19. Security
Baseline:
- OWASP-aligned API security
- secure headers
- strict input validation
- output encoding where applicable
- parameterized queries
- dependency scanning
- container scanning
- secret scanning
- SAST where practical
- rate limiting
- audit logging
- least privilege
- encrypted transport
- encrypted secrets
- safe error messages

CI security gates:
- Gitleaks
- Trivy
- dependency vulnerability scanning
- test suite
- static analysis

## 20. CI/CD

Pull request pipeline:
1. formatting/lint
2. unit tests
3. integration tests
4. build
5. security scans
6. container build
7. artifact publication

Deployment:
- main branch → build/version → image registry → deployment manifest update → GitOps deployment.

Environments:
- local
- preview/staging
- production

Never deploy unreviewed production changes directly from a developer laptop.

## 21. Infrastructure
Infrastructure as Code with Terraform.

Kubernetes deployment should include:
- Deployments
- Services
- Ingress/Gateway
- ConfigMaps
- Secrets references
- resource requests/limits
- liveness/readiness/startup probes
- PodDisruptionBudget where appropriate
- HPA when justified
- NetworkPolicy where supported

For the first hosted version, keep infrastructure minimal. Use Kubernetes primarily where it adds portfolio/operational value; do not introduce a cluster merely to satisfy architecture aesthetics.

## 22. Environments
Local:
- Docker Compose for dependencies
- optional local Kubernetes via k3d/kind

Hosted:
- web frontend
- API
- managed PostgreSQL
- managed Redis if needed
- object storage if needed

Keep provider-specific infrastructure behind Terraform modules.

## 23. Testing Pyramid
Unit tests:
- domain logic
- scoring
- priority engine

Integration:
- database
- security
- API

E2E:
- authentication
- objective creation
- roadmap/skill workflows
- project/evidence workflows
- daily planning

Contract tests:
- external integrations

Infrastructure:
- Terraform validation
- Kubernetes/Helm validation

Security:
- dependency/container/secret scanning.

## 24. Reliability
Implement:
- timeouts
- bounded retries
- exponential backoff with jitter where appropriate
- circuit breaking for unstable external dependencies where justified
- graceful degradation
- database migration safety
- health checks
- backups
- restore testing before claiming disaster recovery.

## 25. Disaster Recovery
Initial target:
- automated database backups
- documented restore procedure
- periodic restore verification
- versioned infrastructure
- immutable application artifacts

Define RPO/RTO before scaling the system.

## 26. Performance
Initial goals should be measured rather than guessed.

Establish:
- API p50/p95 latency
- frontend performance metrics
- DB query latency
- background job duration
- AI latency

Use profiling and load testing before optimizing.

## 27. Repository
Recommended monorepo:

    orbit/
      apps/
        web/
        api/
      packages/
        ui/
        types/
        config/
      infrastructure/
        terraform/
        kubernetes/
        helm/
      docs/
        architecture/
        adr/
        api/
        product/
        runbooks/
      scripts/
      .github/workflows/

## 28. ADRs to Create First
ADR-001 Modular monolith
ADR-002 PostgreSQL as system of record
ADR-003 REST API
ADR-004 OIDC authentication
ADR-005 Redis usage boundaries
ADR-006 AI provider abstraction
ADR-007 Roadmap normalization strategy
ADR-008 GitHub integration permissions
ADR-009 Deployment strategy
ADR-010 Observability standard

## 29. Antigravity Execution Model
Antigravity is the primary development environment.

Agents:
- Architect
- Backend
- Frontend
- Database
- DevOps
- SRE
- Security
- QA
- UX
- Documentation

Rules:
1. Read PRD and architecture before coding.
2. Work in small, reviewable increments.
3. Never rewrite unrelated modules.
4. Run tests before declaring completion.
5. Produce an implementation artifact/summary.
6. Record architectural changes as ADRs.
7. Use browser verification for user-facing workflows.
8. Never weaken security or tests to make a task pass.

## 30. Claude Code Review Model
Use Claude Code as an independent senior reviewer after meaningful milestones.

Review prompts should ask for:
- correctness
- security vulnerabilities
- architectural flaws
- scalability issues
- missing tests
- bad abstractions
- technical debt
- production-readiness gaps

Claude Code should initially review before modifying code. Findings are converted into tracked issues/tasks and then fixed through the normal workflow.

## 31. Production Definition of Done
A release is production-ready only when:
- acceptance criteria pass;
- automated tests pass;
- security scans pass;
- migrations are verified;
- observability exists;
- deployment/rollback is documented;
- backup/restore is understood;
- browser E2E passes for critical paths;
- no known critical/high unresolved defects remain.

## 32. Implementation Order
1. Repository/bootstrap
2. Architecture/ADRs
3. Identity
4. Database/migrations
5. Objectives/tasks
6. Command Center
7. Career model
8. Roadmaps
9. Skills/dependencies
10. Projects/evidence
11. Analytics
12. OSCAR
13. Integrations
14. SRE/personal metrics
15. CI/CD
16. Production hardening

## 33. Open Decisions Before Implementation
- Exact hosting provider mix
- OIDC provider(s)
- AI provider/model routing
- Redis necessity for MVP
- background-job implementation
- object storage necessity
- initial scoring weights
- exact career profile schema
- exact roadmap ingestion mechanism

These decisions should be made through ADRs rather than hidden inside implementation.

