# Pattern: rate-limit-sensitive

## When this applies

Feature has endpoints that are expensive (search, AI calls, bulk imports), public-facing without auth, or known to be abuse-prone (signup, password reset, login). Even auth-gated endpoints may be rate-sensitive when expensive.

## Spec authoring guidance

- **Identify rate-sensitive endpoints explicitly** in the spec's Security Design → Rate Limits subsection. List each abuse-prone endpoint by HTTP method + path with its bucket configuration.
- **Bucket scope:** specify per-IP, per-user, or per-tenant:
  - Login / signup / password-reset → per-IP (defends against credential stuffing)
  - Authenticated expensive operations (AI, bulk export) → per-user or per-tenant
  - Public-facing search → per-IP plus a global cap
- **Bucket size + window:** specify both. E.g., "10 requests per minute per IP, with a 5-second cooldown after exhaustion."
- **Reference the existing `RateLimitFilter` mechanism** in `technical.md`. Do not invent a new rate-limiter. The filter lives at `com.tenxengage.app.security.RateLimitFilter`.

## Implementation guidance

- Use `RateLimitFilter` (Spring filter chain). Configuration is typically annotation-driven on controller methods or by URL pattern in security config.
- For AI-streaming endpoints, rate-limit the *request to start a stream*, not bytes-in-flight.
- Return `429 Too Many Requests` with `Retry-After` header. The error response shape must match the platform's standard error envelope.

## Examples in codebase

- Filter implementation: `tenxengage-backend/src/main/java/com/tenxengage/app/security/RateLimitFilter.java`
- Existing rate-limit configurations: `grep -rn "@RateLimited\|rateLimitConfig" tenxengage-backend/src/main/java/`

## Common gotchas

- **Per-IP behind a load balancer.** If the LB doesn't forward `X-Forwarded-For`, all traffic appears from one IP. Verify the chain.
- **Per-user without a user.** For unauthenticated endpoints (login, signup), only per-IP makes sense.
- **Token-bucket sizing.** A 100-req/min bucket allows 100 reqs in 1 second then nothing for 59 seconds. Specify smoothing (sliding window) if burst behavior matters.
- **Rate-limited error visibility.** Sustained 429 rates may indicate an attack — audit + alert on them.
