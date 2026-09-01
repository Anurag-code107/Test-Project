# Pattern: sse-streaming

## When this applies

Feature uses Server-Sent Events (SSE) for one-way server-to-client streaming (typing indicators, real-time notifications, AI streaming completions, progress updates for long-running operations).

## Spec authoring guidance

- **JWTs in query params are forbidden.** The spec's Security Design section MUST NOT specify `?token=...` for `EventSource` authentication. JWTs in query strings are logged by every server and proxy in the chain and appear in browser history. Spec one of:
  - **Option A (preferred for simple cases):** `fetch` + `ReadableStream` on the client, which supports the `Authorization: Bearer` header. The spec must explicitly call out that the FE uses `fetch`, not `EventSource`.
  - **Option B (for cases where `EventSource` is required, e.g., automatic reconnection):** a short-lived SSE-specific token obtained from a separate authenticated endpoint (e.g., `POST /api/auth/sse-token`). The token is single-use, tied to the user session, and expires within seconds. Spec the issue endpoint, the token TTL, and the validation flow.
- **Document the chosen approach** in the spec's Security Design section. The choice has implications for the FE implementation pattern.
- **Heartbeat / keep-alive cadence** must be specified — typically every 15–30 seconds — to prevent intermediary timeouts.
- **Reconnection / replay semantics** must be addressed: does the client resume from a missed event ID, or just receive new events after reconnect? Spec the answer.

## Implementation guidance

- BE: use `SseEmitter` (Spring Web) or reactive equivalent. Always set the heartbeat interval.
- FE Option A: `fetch('/api/...', { headers: { Authorization: ... } })` then process `response.body.getReader()` with text decoding.
- FE Option B: hit the token-issue endpoint, receive a short-lived token, then `new EventSource('/api/...?sseToken=' + token)`. Token validation server-side checks expiry and issued-to-user-id.
- Idle timeout: tear down the SseEmitter after N minutes of client inactivity (no pings) to free server resources.

## Examples in codebase

- Existing SSE usage (if any): `grep -rn "SseEmitter" tenxengage-backend/src/main/java/`
- FE streaming examples: `grep -rn "EventSource\|ReadableStream" tenxengage-frontend/src/`

## Common gotchas

- **Browser history and proxy logs.** This is the entire reason `?token=jwt` is forbidden. Treat it as a hard rule, not a soft preference.
- **CORS and credentials.** SSE with `EventSource` does NOT support `Authorization` header — that's why this pattern exists. Don't fight the API; pick the right approach (A or B).
- **Connection limits.** Browsers limit concurrent EventSource connections per origin (typically 6). Heavy SSE features need to plan for this — consider multiplexing channels into one connection.
- **Buffering proxies (nginx, ALB).** Some proxies buffer responses, breaking real-time streaming. Spec the deployment requirement (e.g., `proxy_buffering off` for nginx) in the technical.md infrastructure section.
- **`SseEmitter` must be returned directly — never wrapped in `ResponseEntity<?>`.**  Spring's `HttpEntityMethodProcessor` has no converter for `SseEmitter`; wrapping it causes `No converter for [SseEmitter]` at runtime and the endpoint returns HTTP 500 instead of streaming. For 4xx error cases in SSE controller methods, throw `AuthenticationFailedException` / `AccessDeniedException` — do not return `ResponseEntity.status(4xx)`.
