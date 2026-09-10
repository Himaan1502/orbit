# ADR-033: OSCAR Streaming — SSE, and a Prose-First Response Format

## Status
Proposed — pre-Milestone-8 pass (approved by the product owner 2026-09-10 as the
next work after M7, ahead of the Production Platform milestone). Supersedes
[ADR-031](ADR-031-ai-provider-abstraction.md) §Alternatives 4 ("Streaming
responses (SSE) in M7 — Deferred").

## Date
2026-09-10

## Context

M7 shipped OSCAR non-streaming: a turn is one request that returns after the
model (and any tool rounds) finish. With `gemini-3.6-flash` that is **30–60s of a
static "thinking…" indicator** — verified firsthand by the product owner, and
called out as a real UX cost worth fixing before the Production Platform
milestone.

Nothing in the PRD / UX / Blueprint / Tech Arch specs mentions streaming either
way — this is a design decision not covered by the specs, hence an ADR.

**The core tension.** M7's `OscarResponse` contract (ADR-032 §6) is a single JSON
object — `{kind, message, sections?, citations?}`. Streaming that object
token-by-token to the user is unreadable (they'd see `{"kind":"recomm…`), and
incremental JSON parsing to extract the growing `message` value is fragile. The
response format has to change for streaming to be clean.

**Runtime.** `apps/api` is Spring **MVC** (`spring-boot-starter-web`, Tomcat) —
not WebFlux. Redis is configured but currently unused by the API.

## Decision

### 1. Transport — SSE via MVC `SseEmitter`

New endpoint **`POST /api/v1/oscar/conversations/{id}/turns/stream`** →
`Content-Type: text/event-stream`, backed by `SseEmitter`. The turn runs on a
dedicated bounded executor (not a Tomcat request thread); the async request
timeout is set to **120s** (`WebMvcConfigurer.configureAsyncSupport`).

The M7 **`POST /api/v1/oscar/conversations/{id}/turns` stays** — it is used by
the integration tests, by the contextual-nudge path
([ADR-034](ADR-034-oscar-contextual-cards.md)), and is the client's fallback
when `fetch` streaming is unavailable or errors before the first token.

### 2. Response format — prose first, then a `===OSCAR-META===` block (`oscar-v2`)

A new prompt version **`oscar-v2`** (replacing `oscar-v1` for **both** the
streaming and non-streaming paths — one format, one parser) asks the model to
emit:

```
<the answer as plain prose — this is what streams to the user>
===OSCAR-META===
{"kind":"answer"|"recommendation",
 "sections":{"action":"…","why":"…","impact":"…",
   "evidence":[{"label":"…","kind":"fact"|"metric"|"inference","value":"…"}]},
 "citations":["careerTarget","get_skill_gap"]}
```

- Everything **before** the sentinel is the answer. The backend streams it as
  `token` events and persists it verbatim as the assistant message (ADR-032 §1 —
  "OSCAR's final assistant text").
- Everything **after** the sentinel is buffered, parsed once at stream end, and
  sent as a single `meta` event carrying `sections` + `citations`. There is no
  `message` field — the prose is the message.
- **The prose that arrived is always real.** If the model never emits the
  sentinel → `kind:"answer"`, no sections, prose stands. If the JSON after the
  sentinel doesn't parse → prose stands, no chips, logged. The M7 "unparsable →
  safe fallback, unpersisted" path now only triggers when **no prose at all**
  arrived.

`ResponseValidator` is rewritten to split on the sentinel; the balanced-brace
JSON scan is kept for the meta block.

### 3. Tool rounds during a stream

Read-tool rounds happen **before** any prose token. Each is surfaced as a
`status` event (`{"text":"Checking your skill gaps…"}`) so the UI shows
motion instead of a dead spinner. A mutation-tool call ends the round → the
backend emits `proposedAction` events, then a synthesis call whose prose is
streamed. Bounded by `orbit.ai.max-tool-rounds` exactly as M7.

### 4. SSE event vocabulary

| `event:` | data | meaning |
|---|---|---|
| `status` | `{text}` | pre-answer progress (tool round, "thinking") |
| `token` | `{text}` | append this chunk to the answer |
| `proposedAction` | a `ProposedAction` | 0..n, emitted before/around the prose |
| `meta` | `{kind, sections?, citations?, provider, model}` | final structured extras, once |
| `done` | `{messageId, persisted}` | stream finished, message written |
| `error` | `{recoverable, note}` | degraded — see §5 |

### 5. Fail-over with a live stream

- **Before the first `token`** (the client has only seen `status` events):
  primary failure → one retry on a transient 5xx → **fail over to the fallback
  provider silently**, exactly as M7.
- **After tokens have streamed**: the client is already reading an answer from
  one provider. A mid-stream drop → emit `error {recoverable:true, note:"the
  connection dropped — ask again to retry"}` then `done {persisted:false}`.
  **No mid-answer provider switch** — a spliced answer from two models is worse
  than a clean retry. What streamed is not persisted (partial).

### 6. HTTP client — migrate the provider adapters to the JDK `HttpClient`

Spring's `RestClient` buffers the whole response body — it cannot stream SSE.
Rather than add `spring-boot-starter-webflux` just for `WebClient`, migrate
`HttpAiProviderSupport` to **`java.net.http.HttpClient`** (JDK 21, already on the
classpath, no new dependency):

- non-streaming: `HttpClient.send(req, BodyHandlers.ofByteArray())` — same
  behaviour, same error mapping as M7's `RestClient` path.
- streaming: `BodyHandlers.ofLines()` — iterate SSE lines, parse `data:` frames,
  push text deltas to a `TokenSink` callback.

One HTTP client, one timeout/error-mapping code path for both providers.

Provider API additions:

```
AiProvider.completeStream(AiRequest, TokenSink) -> AiCompletion
  Gemini : POST /v1beta/models/{model}:streamGenerateContent?alt=sse
  Groq   : POST /chat/completions  with  "stream": true   (SSE, terminated by data: [DONE])
```

`completeStream` calls `sink.onToken(delta)` as text arrives and returns the
assembled `AiCompletion` (full text + tool calls + `usage`) when the stream
ends — so the existing tool-round / persistence logic downstream is unchanged.

### 7. BFF proxy + frontend

- **BFF** (`apps/web/src/app/api/orbit/[...path]/route.ts`): when the upstream
  response is `text/event-stream`, return `new NextResponse(upstream.body, …)`
  **unbuffered** (no `.text()`), with `Cache-Control: no-cache, no-transform`
  and `X-Accel-Buffering: no`. The proactive token refresh already runs before
  the request; an access token outlives a 120s stream.
- **Frontend**: a `streamTurn()` in `features/oscar/api.ts` using native
  `fetch` + `response.body.getReader()` + a ~40-line SSE line parser (no
  dependency, no `EventSource` — which can't POST). `OscarChat` renders `token`
  events into a live assistant bubble, `status` into the progress line,
  `proposedAction` into cards, `meta` into the §25.2 sections. On any failure
  before the first token it falls back to the non-streaming `useSendTurn`.

### 8. Persistence — unchanged

After `done`, `ConversationService` writes the same rows as M7 (user message +
accumulated prose + provider/model/`prompt_version=oscar-v2`/usage), just
triggered at stream end. `token_usage` comes from the final SSE chunk.

## Alternatives Considered

1. **Incremental JSON streaming** — keep the M7 pure-JSON contract, run a
   streaming JSON parser, emit the `message` value's characters as they arrive.
   Rejected — a stateful in-string scanner is fragile against escaping and field
   ordering; the sentinel format is trivially robust and the `message` field
   becomes redundant anyway.
2. **Stream prose, then a second cheap call to structure the evidence tags.**
   Rejected — adds a full round-trip (tokens + latency) after every answer for
   what the sentinel gives for free.
3. **`@microsoft/fetch-event-source`.** Rejected — native `fetch` streaming is
   ~40 lines and CSP-clean; the lib adds a dependency for retry/reconnect logic
   OSCAR does not need (a turn is one-shot).
4. **WebClient / migrate `apps/api` to WebFlux.** Rejected — a large change to a
   working MVC app for one streaming endpoint; JDK `HttpClient` streams fine.
5. **Mid-stream fail-over** (buffer tokens, switch provider, replay). Rejected —
   §5: a clean retry beats a two-model answer, and buffering defeats streaming.

## Consequences

- **Positive.** The 30–60s dead wait becomes a live answer starting in 1–3s.
  Tool rounds are visible. One prompt + one parser for both paths. No new
  dependency. The "prose that arrived is real" rule removes the most common
  unpersisted-turn case.
- **Negative.** Two turn endpoints to keep in step. A streamed turn holds an
  executor thread for its duration (fine single-instance; a bounded pool caps
  it). Fail-over is weaker once tokens flow (mitigated: retry-before-first-token
  still covers the common transient case). `oscar-v1` retires — old
  `ai_messages` keep their recorded version.
- **Neutral.** `ai_messages.prompt_version` starts recording `oscar-v2`. The
  provider adapters change HTTP client but not their external behaviour.

## Sign-off items

1. **SSE via MVC `SseEmitter`**, new `/turns/stream` endpoint, 120s async
   timeout; the non-streaming `/turns` endpoint is retained as tests use it, the
   nudge path uses it, and it is the client fallback.
2. **Prose-then-`===OSCAR-META===` format (`oscar-v2`)** replaces the pure-JSON
   contract for **both** paths; streamed prose that arrived is persisted and is
   never a "safe fallback".
3. **Provider adapters migrate to the JDK `HttpClient`** (no new dependency);
   `completeStream(AiRequest, TokenSink)`.
4. **Fail-over only before the first token.** After tokens stream, a dropped
   provider ends the stream (`error` + `done{persisted:false}`) — no mid-answer
   switch, the user re-asks.
5. **BFF streams `text/event-stream` through unbuffered**; frontend uses native
   `fetch` streaming with the M7 non-streaming path as the fallback.
6. **Tool rounds are `status` events**; `proposedAction` and `meta` are discrete
   SSE events; persistence is unchanged from M7.
