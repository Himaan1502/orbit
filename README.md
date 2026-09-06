# ORBIT — Personal Intelligence & Execution Platform

> Navigate your goals. Build your future.

ORBIT is a modular platform for career planning, skill development, project tracking, and personal execution. It connects objectives → career targets → roadmaps → skills → projects → evidence into a single unified system.

---

## Architecture

| Layer | Technology |
|-------|-----------|
| Frontend | Next.js 14 (App Router) · TypeScript · Tailwind CSS · shadcn/ui |
| Backend | Java 21 · Spring Boot 3.2 · Spring Security · Spring Data JPA |
| Database | PostgreSQL 16 · Flyway migrations |
| Cache | Redis 7 |
| Build | Gradle (Kotlin DSL) · npm |
| CI | GitHub Actions |

The backend is a **modular monolith** organized into bounded domain modules (`identity`, `objectives`, `career`, `roadmaps`, `skills`, `projects`, `evidence`, `execution`, `analytics`, `ai`, `balance`, `sre`, `shared`). See [ADR-001](docs/adr/ADR-001-modular-monolith-architecture.md) for rationale.

---

## Prerequisites

| Tool | Version |
|------|---------|
| Java | 21+ (Temurin recommended) |
| Node.js | 20+ |
| npm | 10+ |
| Docker | 24+ |
| Docker Compose | 2.20+ |

---

## Quick Start

### 1. Clone & configure

```bash
git clone <repo-url>
cd ORBIT
cp .env.example .env
```

### 2. Start infrastructure

```bash
docker compose up -d orbit-db orbit-redis
```

Wait for services to be healthy:
```bash
docker compose ps
```

### 3. Start the backend

```bash
cd apps/api
./gradlew bootRun
```

The API will be available at **http://localhost:8080**.

- Health check: `GET http://localhost:8080/api/v1/health`
- Actuator: `GET http://localhost:8080/actuator/health`
- API docs: `GET http://localhost:8080/api/swagger-ui`

### 4. Start the frontend

```bash
cd apps/web
npm install
npm run dev
```

The app will be available at **http://localhost:3000**.

### 5. Full Docker setup (alternative)

```bash
docker compose up
```

This starts PostgreSQL, Redis, the backend API, and the frontend.

---

## Project Structure

```
ORBIT/
├── apps/
│   ├── web/                    # Next.js frontend
│   │   ├── src/
│   │   │   ├── app/            # App Router pages
│   │   │   ├── components/     # React components
│   │   │   ├── config/         # App configuration
│   │   │   ├── lib/            # Utility functions
│   │   │   ├── stores/         # Zustand state stores
│   │   │   └── test/           # Test setup
│   │   └── package.json
│   └── api/                    # Spring Boot backend
│       ├── src/main/java/com/orbit/platform/
│       │   ├── common/         # Shared infrastructure
│       │   │   ├── config/     # Security, CORS, Redis, Jackson
│       │   │   ├── controller/ # Health endpoints
│       │   │   ├── dto/        # API response envelopes
│       │   │   ├── exception/  # Exception handling
│       │   │   ├── model/      # Base entity
│       │   │   ├── security/   # JWT infrastructure
│       │   │   └── util/       # Request ID filter
│       │   └── identity/       # User domain module
│       │       ├── model/
│       │       └── repository/
│       ├── src/main/resources/
│       │   ├── db/migration/   # Flyway SQL migrations
│       │   └── application*.yml
│       └── build.gradle.kts
├── packages/
│   ├── ui/                     # Shared UI components (future)
│   ├── types/                  # Shared TypeScript types (future)
│   └── config/                 # Shared configuration (future)
├── infrastructure/
│   ├── terraform/              # IaC (future)
│   ├── kubernetes/             # K8s manifests (future)
│   └── helm/                   # Helm charts (future)
├── docs/
│   ├── adr/                    # Architecture Decision Records
│   ├── api/                    # API documentation
│   ├── architecture/           # Architecture specs
│   ├── design/                 # Design system
│   ├── engineering/            # Engineering docs
│   ├── product/                # Product specs
│   └── runbooks/               # Operational runbooks
├── scripts/                    # Development scripts
├── docker-compose.yml          # Local development stack
├── .github/workflows/ci.yml    # CI pipeline
└── .env.example                # Environment template
```

---

## Development Commands

### Frontend (apps/web)

| Command | Description |
|---------|-------------|
| `npm run dev` | Start dev server |
| `npm run build` | Production build |
| `npm run lint` | ESLint check |
| `npm run format` | Prettier format |
| `npm run format:check` | Prettier check |
| `npm run type-check` | TypeScript check |
| `npm run test` | Run Vitest tests |
| `npm run test:coverage` | Tests with coverage |

### Backend (apps/api)

| Command | Description |
|---------|-------------|
| `./gradlew bootRun` | Start dev server |
| `./gradlew build` | Full build |
| `./gradlew test` | Run tests |
| `./gradlew compileJava` | Compile only |
| `./gradlew checkstyleMain` | Checkstyle analysis |
| `./gradlew spotbugsMain` | SpotBugs analysis |
| `./gradlew jacocoTestReport` | Coverage report |

### Docker

| Command | Description |
|---------|-------------|
| `docker compose up -d` | Start all services |
| `docker compose up -d orbit-db orbit-redis` | Infrastructure only |
| `docker compose down` | Stop all services |
| `docker compose logs -f orbit-api` | Follow API logs |

---

## Environment Variables

See [`.env.example`](.env.example) for all available variables.

| Variable | Default | Description |
|----------|---------|-------------|
| `POSTGRES_DB` | `orbit_db` | Database name |
| `POSTGRES_USER` | `orbit_user` | Database user |
| `POSTGRES_PASSWORD` | `orbit_pass` | Database password |
| `REDIS_PASSWORD` | `redis_pass` | Redis password |
| `JWT_SECRET` | (dev key) | JWT signing secret (≥256 bits) |
| `SPRING_PROFILES_ACTIVE` | `dev` | Spring profile |
| `CORS_ORIGINS` | `http://localhost:3000` | Allowed CORS origins |

---

## API Conventions

- Base path: `/api/v1`
- Response envelope: `{"data": ...}` or `{"error": {"code": "...", "message": "..."}}`
- Correlation: `X-Request-ID` header on all requests
- Pagination: `{"data": [...], "pagination": {"page": 0, "pageSize": 20, "total": N}}`

See [ADR-003](docs/adr/ADR-003-rest-api-conventions.md) for full conventions.

---

## Design System

ORBIT uses the **Deep Space** design language:
- Dark-first with light theme support
- Geist Sans + Geist Mono typography
- Cyan (`#4FD1E5`) and Violet (`#8B7CF6`) accent colors
- 4px spacing grid

See [Design System](docs/design/ORBIT_Design_System.md) and [ADR-007](docs/adr/ADR-007-deep-space-design-tokens.md).

---

## Architecture Decision Records

| ADR | Decision |
|-----|----------|
| [ADR-001](docs/adr/ADR-001-modular-monolith-architecture.md) | Modular monolith architecture |
| [ADR-002](docs/adr/ADR-002-postgresql-system-of-record.md) | PostgreSQL as system of record |
| [ADR-003](docs/adr/ADR-003-rest-api-conventions.md) | REST API conventions |
| [ADR-004](docs/adr/ADR-004-jwt-authentication.md) | JWT-based authentication |
| [ADR-005](docs/adr/ADR-005-redis-usage-boundaries.md) | Redis usage boundaries |
| [ADR-006](docs/adr/ADR-006-nextjs-frontend-framework.md) | Next.js frontend framework |
| [ADR-007](docs/adr/ADR-007-deep-space-design-tokens.md) | Deep Space design tokens |
| [ADR-008](docs/adr/ADR-008-navigation-structure.md) | Navigation structure |
| [ADR-009](docs/adr/ADR-009-flyway-database-migrations.md) | Flyway database migrations |
| [ADR-010](docs/adr/ADR-010-observability-stack.md) | Observability stack |

---

## Milestone Roadmap

- **M0** — Architecture & Repository Bootstrap ← *you are here*
- **M1** — User Identity & Authentication
- **M2** — Objectives Module
- **M3** — Command Center
- **M4** — Career & Skills
- **M5** — Roadmaps
- **M6** — Projects & Evidence
- **M7** — Today / Execution
- **M8** — Analytics
- **M9** — OSCAR AI
- **M10** — Production Hardening

---

## License

[MIT](LICENSE)
