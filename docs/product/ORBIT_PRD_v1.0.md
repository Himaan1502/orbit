# ORBIT Product Requirements Document (PRD) v1.0

**Product:** ORBIT — Objectives, Roadmaps, Balance, Intelligence & Tracking  
**Positioning:** Personal Intelligence & Execution Platform  
**AI Assistant:** OSCAR — Operational Strategy & Career Assistant  
**Tagline:** Navigate your goals. Build your future.

## 1. Vision
ORBIT transforms long-term objectives into measurable daily action through a connected system:
Objective → Strategy → Roadmap → Skill → Project → Task → Execution → Evidence → Measurement → Course Correction.

The central question is: **Given where I am and where I want to go, what should I do next?**

## 2. Product Principles
1. Action over tracking.
2. Evidence over claims.
3. Dependencies matter.
4. One skill, multiple outcomes.
5. Personalization.
6. Continuous course correction.
7. The score is an indicator, not the objective.
8. Privacy and user control by default.

## 3. Target Users
### Primary
A technically ambitious individual managing career development, learning, projects, execution, health, finance and other long-term goals.

### Initial career configuration
- Platform Engineer / DevOps Engineer / SRE
- Specialization: AI Infrastructure / MLOps
- Supporting capabilities: System Design, Software Engineering, Security, DSA, product/customer thinking.

## 4. Core Modules
### 4.1 Command Center
- Today's priorities
- Active objectives
- Career trajectory
- Skill gaps
- Roadmap progress
- Project progress
- Execution health
- Deadlines
- Alerts
- OSCAR recommendations

### 4.2 Objective Engine
Objectives contain milestones, key results, skills, projects, tasks and deadlines.

### 4.3 Career Engine
Career profiles contain target roles, required/preferred skills, interview domains, project expectations and evidence requirements.

### 4.4 Roadmap Engine
Normalize external roadmaps into Roadmap → Topic → Skill → Dependency → Evidence. Store source attribution and external links rather than copying third-party content.

Initial sources:
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

### 4.5 Skill Intelligence
Each skill can have:
- Knowledge
- Practical ability
- Evidence
- Interview readiness
- Production readiness
- Overall capability

### 4.6 Project Engine
Projects map to skills, objectives and evidence. Project impact should be measurable.

### 4.7 Evidence Engine
Evidence types:
- GitHub repository/commit/PR
- Deployment
- Architecture document
- Technical article
- Certification
- Project
- Test
- Interview result
- Production incident
- Performance benchmark

### 4.8 Daily Execution Engine
Generate daily plans from objectives, deadlines, skill gaps, dependencies, project state, historical execution and available time.

### 4.9 Intelligence Engine / OSCAR
OSCAR consumes goals, career state, roadmaps, skills, projects, tasks, metrics, history and constraints to produce recommendations, plans, explanations, reviews and course corrections.

### 4.10 Balance Engine
Long-term modules for fitness, finance, cricket, habits and other life metrics. These are connected to objectives but must not overwhelm the core career/execution experience.

### 4.11 SRE Personal Operating Layer
Support personal SLOs, error budgets and incidents as an optional advanced system.

## 5. Roadmap Intelligence
ORBIT must deduplicate overlapping skills. One skill can contribute to multiple roadmaps and career targets.

The UI should expose a Roadmap Intersection view showing shared high-leverage skills.

## 6. Priority Engine
Conceptual priority:
Career Relevance × Skill Gap × Dependency Impact × Project Impact × Interview Value × Urgency × Available Time.

The exact algorithm is implementation work and must be explainable to the user.

## 7. Career Evidence
A roadmap item should not be considered fully demonstrated merely because it was marked complete. ORBIT should distinguish learning from demonstrated capability.

## 8. Weekly Review
Generate:
- What happened?
- What improved?
- What was missed?
- Why?
- What changed?
- What should be prioritized next?

## 9. Analytics
Track trends for career velocity, learning velocity, skill growth, execution consistency, goal completion, project velocity and other selected life metrics.

## 10. AI Requirements
OSCAR must:
- explain recommendations
- cite ORBIT data used for decisions where practical
- distinguish facts from inference
- respect user constraints
- avoid silently changing goals
- require confirmation for consequential external actions
- maintain conversation/context boundaries
- degrade gracefully if an AI provider is unavailable

## 11. Privacy & Security Requirements
- Secure authentication
- Authorization/RBAC
- Input validation
- Rate limiting
- Audit logs for sensitive actions
- Secrets management
- Dependency scanning
- Container scanning
- Secret scanning
- Security headers
- OWASP-aligned protections
- Data export and deletion capability
- Minimum necessary data collection

## 12. MVP
MVP includes:
1. Authentication
2. Command Center
3. Objectives
4. Tasks
5. Daily planning
6. Career profile
7. Roadmap ingestion/data model
8. Skills and dependencies
9. Basic skill scoring
10. Projects
11. Evidence
12. Basic analytics

OSCAR, life modules and SRE features can follow the MVP.

## 13. Phased Roadmap
### Phase 0
Architecture, domain model, UX architecture, API contracts, security model and ADRs.

### Phase 1
ORBIT Core.

### Phase 2
Career Intelligence.

### Phase 3
Projects & Evidence.

### Phase 4
OSCAR.

### Phase 5
Life Systems.

### Phase 6
SRE Layer.

### Phase 7
Production Infrastructure.

### Phase 8
Production hardening, documentation and public portfolio/demo.

## 14. Success Criteria
ORBIT succeeds when it can:
- turn a long-term objective into prioritized actionable work;
- identify the highest-leverage skill gaps;
- account for roadmap overlap and dependencies;
- connect learning to projects and evidence;
- explain why a task is prioritized;
- demonstrate measurable progress over time;
- support a production-grade engineering workflow.

## 15. Non-Goals for v1
- Full social network
- Generic note-taking application
- Generic calendar replacement
- Full banking integration
- Medical/clinical decision support
- Copying entire third-party roadmap sites
- Autonomous consequential financial or career decisions

## 16. Definition of Done
A feature is not done until:
- implementation is complete;
- automated tests pass;
- security implications are reviewed;
- error handling is implemented;
- observability is considered;
- documentation is updated;
- browser/E2E verification is completed where applicable;
- acceptance criteria are satisfied.
