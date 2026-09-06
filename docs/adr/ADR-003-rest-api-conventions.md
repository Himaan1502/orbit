# ADR-003: REST API Conventions

## Status
Accepted

## Date
2026-09-05

## Context
ORBIT needs a well-defined API contract between the frontend and backend. We need to decide on API style, versioning, and response envelope format.

## Decision
All APIs follow RESTful JSON conventions under the `/api/v1` base path.

### Response Envelopes
- **Single resource**: `{"data": { ... }}`
- **Collection**: `{"data": [...], "pagination": {"page": 0, "pageSize": 20, "total": 100}}`
- **Error**: `{"error": {"code": "ERROR_CODE", "message": "...", "details": [...], "requestId": "..."}}`

### Conventions
- URL-based versioning (`/api/v1/`)
- Kebab-case for URL paths
- Standard HTTP status codes (200, 201, 204, 400, 401, 403, 404, 409, 422, 429, 500)
- Correlation IDs via `X-Request-ID` header
- No stack traces in error responses

## Alternatives Considered
1. **GraphQL**: Rejected for initial release. Adds complexity (schema stitching, N+1 queries) without clear benefit for a single-user app. Can be added later.
2. **gRPC**: Rejected. Browser support requires gRPC-Web proxy. REST is simpler for this use case.

## Consequences
- **Positive**: Simple, well-understood, excellent tooling, easy to document with OpenAPI.
- **Negative**: May require multiple round trips for complex views. Mitigated by aggregate endpoints (e.g., `/api/v1/dashboard`).
