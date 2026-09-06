# ADR-010: Observability Stack

## Status
Accepted

## Date
2026-09-05

## Context
ORBIT needs observability from day one — structured logging, metrics, health checks, and request correlation.

## Decision
### Logging
- SLF4J + Logback
- Structured JSON format in production
- Console format in development
- Request correlation via `X-Request-ID` header and MDC

### Metrics
- Micrometer with Prometheus endpoint (`/actuator/prometheus`)
- Spring Boot Actuator for health checks
- Key metrics: `http_requests_total`, `http_request_duration`, `http_errors_total`, `db_query_duration`

### Health Checks
- Liveness: `/actuator/health/liveness`
- Readiness: `/actuator/health/readiness`
- Custom indicators for database and Redis connectivity

### Tracing
- Deferred to later milestones. OpenTelemetry will be added when distributed tracing is needed.

## Alternatives Considered
1. **Full OpenTelemetry from day one**: Deferred. Adds complexity without immediate benefit for a monolith. Infrastructure is ready for when it's needed.

## Consequences
- **Positive**: Observable from day one, standard tooling, minimal overhead.
- **Negative**: No distributed tracing initially. Acceptable for a monolith.
