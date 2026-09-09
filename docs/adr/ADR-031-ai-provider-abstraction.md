# ADR-031: AI Provider Abstraction — Gemini (primary) / Groq (fallback), Free-Tier

## Status
Proposed — Milestone 7 (our numbering; Blueprint "Milestone 9 — OSCAR").
Resolves Blueprint §55 open decision *"exact AI model/provider routing"*.

## Date
2026-09-09

## Context

Blueprint §28 / Tech Arch §12: *"Do not couple the domain to one AI vendor"* —
an `AiService` with a `ProviderAdapter` layer, storing `provider / model /
prompt version / usage / latency / status` "to enable later model routing and
cost controls". Tech Arch §13 (AI Safety & Reliability): provider timeouts,
retry only safe operations, rate limits, token/cost budgets, prompt/version
tracking, structured output schemas, validation before persistence, graceful
degradation. PRD §10: OSCAR must "degrade gracefully if an AI provider is
unavailable". UX §25.5: *"ORBIT must remain functional if the AI provider is
unavailable."*

Nothing AI exists in the repo yet — no provider config, no HTTP client for
outbound calls, no `ai/` package.

**Provider constraint (product owner, 2026-09-09):** **Google Gemini via AI
Studio** is the primary provider — its large context window (Gemini 2.0/2.5
Flash: ~1M tokens) fits the Tech Arch §12 layered context-builder without
sophisticated retrieval. **Groq** is the documented fallback in the abstraction
layer. **Both are free-tier, no card required** — this must be a first-class
design input, not a paid provider with a free tier bolted on.

## Decision

### 1. `AiProvider` port + two adapters

```
com.orbit.platform.ai.provider
  AiProvider (interface)      complete(AiRequest) -> AiResponse
  GeminiProvider  @Component  — generativelanguage.googleapis.com, model gemini-2.0-flash
  GroqProvider    @Component  — api.groq.com/openai/v1 (OpenAI-compatible), model llama-3.3-70b-versatile
```

- `AiRequest` — system prompt + prior turns + the user message + the tool
  declarations (JSON schema) + generation config (max output tokens,
  temperature, timeout).
- `AiResponse` — either assistant text, or one/more tool calls (name + validated
  args), plus `Usage(promptTokens, completionTokens)`, `modelName`, `finishReason`.
- Both adapters translate to/from the provider's native shape. Outbound HTTP via
  Spring's `RestClient` (in `spring-web`, no new dependency); a dedicated
  `RestClient` bean per provider with the configured base URL, connect/read
  timeouts, and the API key applied per that provider's convention (Gemini:
  `x-goog-api-key` header; Groq: `Authorization: Bearer`).

### 2. `AiService` — orchestration, routing, degradation

```
AiService.respond(AiTurn) -> OscarResponse
```

1. Build the request (system prompt `oscar-v1`, context bundle, tool decls).
2. Call the **primary** provider (`orbit.ai.provider.primary=gemini`).
3. On a *retriable* failure — connect/read timeout, HTTP 429, HTTP 5xx, malformed
   body — fall through to the **fallback** (`orbit.ai.provider.fallback=groq`).
   A single retry of the primary on a transient 5xx before failing over.
4. If both fail, or the feature is disabled (§4), return the **degraded
   response** — a structured `OscarResponse` with `status: "unavailable"` that
   the UI renders as UX §25.5. **No exception propagates to the caller;** OSCAR
   being down never breaks an ORBIT request.
5. Non-mutating tool calls are executed and fed back for up to **3 tool rounds**
   per turn (bounded — Blueprint §13 "retry only safe operations"); a mutation
   tool call ends the round and is returned as a *proposed action*
   ([ADR-032](ADR-032-oscar-domain.md) §4).

### 3. Free-tier as a design input (not an afterthought)

- **Rate limits are the binding constraint**, not cost. Gemini AI Studio free
  tier (Flash): ~15 requests/min, ~1M tokens/min, ~1500 requests/day. Groq free
  tier: generous RPM, a daily token ceiling. `PolicyGuard`
  ([ADR-032](ADR-032-oscar-domain.md) §5) enforces a **per-user daily request
  cap** (default 50) and a **per-turn token budget** (default: 24k prompt / 2k
  output) so a single conversation can't exhaust the day's quota. Both
  configurable, startup-validated.
- **A 429 from the primary is a normal event**, not an error — it triggers
  fail-over to Groq, then the degraded response, and increments a metric. No
  alerting on a single 429.
- **No card, no billing integration, no cost dashboard** in M7. `token_usage` is
  stored per message for future cost controls (Blueprint §28) but nothing acts
  on it yet.

### 4. Feature flag — OSCAR is optional, ORBIT is not

- OSCAR is **enabled only when at least one provider key is present**
  (`GEMINI_API_KEY` and/or `GROQ_API_KEY`). No key → the `/oscar` page shows the
  UX §25.5 unavailable state, the nav item still routes there, and **every other
  ORBIT feature is unaffected**. `docker compose` runs fine with no key.
- Keys come from the environment only (`GEMINI_API_KEY`, `GROQ_API_KEY` on
  `orbit-api`, `${VAR:-}` passthrough in compose). Never logged, never returned
  in any response, never stored in the DB.

### 5. Observability (Blueprint §28 / §38)

- Per call: `orbit_ai_requests_total{provider, model, outcome}` (outcome ∈
  `ok / rate_limited / failed_over / degraded`), `orbit_ai_request_duration_seconds{provider}`,
  `orbit_ai_tokens_total{provider, kind}`.
- Structured log per turn: `event=oscar.turn user_id=… provider=… model=…
  prompt_version=… prompt_tokens=… completion_tokens=… tool_rounds=…
  duration_ms=… outcome=…` — no message content in logs.
- `ai_messages` row per persisted message carries `model_provider`,
  `model_name`, `prompt_version`, `token_usage` (Blueprint §18).

### 6. Safety (Blueprint §13 / §37, PRD §10)

- **The model never executes anything.** Read tools run in the backend against
  validated inputs; mutation tools are proposals only ([ADR-032](ADR-032-oscar-domain.md)).
- Retrieved ORBIT data that is user- or third-party-authored (roadmap
  `source_url`, evidence `description`, objective text) is passed as **data,
  clearly delimited**, with a system-prompt instruction that content inside those
  delimiters is never an instruction (Blueprint §37 "external content must never
  be treated as system instructions").
- `ResponseValidator` parses every model response against the
  [ADR-032](ADR-032-oscar-domain.md) §6 schema; a response that doesn't parse
  becomes a safe "I couldn't format that" fallback — never persisted, never
  acted on (Blueprint §13 "never treat model output as trusted executable code").
- Prompt text is a versioned resource (`oscar-v1`); a change bumps the version
  and every new `ai_messages` row records which version produced it.

## Alternatives Considered

1. **OpenAI / Anthropic as primary.** Rejected per the product owner — both
   require a card for meaningful use. The abstraction makes adding them later a
   one-adapter change; nothing here precludes it.
2. **Spring AI or LangChain4j.** Rejected for M7 — both add a heavy dependency
   and their own abstractions over what is, for two providers with simple REST
   APIs, a ~150-line adapter each. Revisit if the provider set grows or if
   advanced RAG/agent features are needed. The `AiProvider` port keeps the door
   open.
3. **Gemini's OpenAI-compatibility endpoint for both providers.** Tempting (one
   adapter), but Gemini's compat layer lags its native API on tool calling and
   system-instruction handling. Use each provider's native API; the port
   normalises them.
4. **Streaming responses (SSE) in M7.** Deferred. Both providers support it, but
   it adds SSE plumbing on the API + client for a first cut. M7 is
   request/response; streaming is a flagged refinement.
5. **A queue/job for AI calls** ([ADR-029](ADR-029-durable-job-mechanism.md)).
   Not needed — a chat turn is synchronous within the request (a few seconds,
   bounded by the provider timeout). The job runner is for scheduled work.

## Consequences

- **Positive.** Blueprint §55's provider-routing decision is closed with a
  zero-cost, no-card stack. Vendor-neutral by construction. OSCAR down ≠ ORBIT
  down. Free-tier rate limits are handled as first-class flow (fail-over +
  degrade), not surfaced as errors.
- **Negative.** Two adapters to maintain; free-tier rate limits mean a heavy day
  can leave OSCAR degraded for a user (mitigated by the per-user cap being well
  under the daily quota). Gemini AI Studio free tier has no SLA.
- **Neutral.** Two new env vars; a `RestClient` bean per provider; `ai_messages`
  grows one row per message.

## Sign-off items

1. **Gemini (AI Studio) primary, Groq fallback**, both free-tier / no-card;
   `AiProvider` port + `AiService` with retry → fail-over → degrade.
2. **OSCAR is feature-flagged on the presence of a provider key**; absent a key,
   `/oscar` shows the unavailable state and nothing else in ORBIT changes.
3. **`PolicyGuard` caps**: per-user daily request cap + per-turn token budget;
   read tools retry, mutations never do; bounded tool-round depth (3).
4. **Non-streaming** request/response in M7; streaming deferred.
5. **Keys from env only**, never logged / returned / persisted; `token_usage`
   stored per message but not acted on (no cost controls in M7).
