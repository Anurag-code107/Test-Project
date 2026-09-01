# Learnings Log

Append-only record of findings promoted from ready-check reports to project docs.
Not referenced anywhere — exists to track the rate of new pitfalls discovered over time.
A declining number of entries per feature signals the conventions are working.

---

## 2026-04-22 — 001-enablement-courses

| Rule | Category | Applied to |
|---|---|---|
| Always handle `CompletableFuture` from `kafkaTemplate.send()` — attach `.whenComplete()`; never discard | kafka-events | docs/patterns/kafka-events.md |
| Always propagate `JsonProcessingException` from event producers — rethrow as `RuntimeException`; never swallow | kafka-events | docs/patterns/kafka-events.md |
| Always type Kafka event status fields with their enum, never `String` | kafka-events | docs/patterns/kafka-events.md |
| Always use `@PersistenceContext` to inject `EntityManager` in `@Aspect` classes — constructor injection gives a non-thread-safe singleton | tenant-isolation | docs/patterns/tenant-isolation.md |
| Always include an `else` branch to disable `tenantFilter` when `clientId` is null — omitting it risks cross-tenant data leakage | tenant-isolation | docs/patterns/tenant-isolation.md |
| `LIKE` queries on text columns require a GIN trigram index — leading-wildcard `LIKE` on unindexed columns is a full table scan | db-performance | PROJECT-CONTEXT.md |
| Custom `@EntityGraph` / `@Query` repository methods must add explicit `AND c.clientId = :clientId` — relying solely on the AOP tenant filter is a cross-tenant leak vector if the filter is inactive in async or test contexts | tenant-isolation | docs/patterns/tenant-isolation.md |
| Free-text `@RequestParam` (search, query) must have `@Size(max = 200)` — unbounded strings are passed into LIKE queries and cause excessive memory and full-table scans | security | PROJECT-CONTEXT.md |
| Escape LIKE special characters (`%`, `_`) in search params before passing to CONCAT — `@Size` limits length but not wildcard content, enabling full-table scan amplification | db-performance | PROJECT-CONTEXT.md |
| Two-query keyset pagination: use `idPage.getTotalElements()` for the empty-page short-circuit — never hardcode `0` | db-performance | PROJECT-CONTEXT.md |
| Pass exception as final SLF4J argument: `log.warn("...", detail, e)` not `log.warn("...", e.getMessage())` — omitting `e` loses the stack trace | logging | PROJECT-CONTEXT.md |
| Null-safe name concatenation: use `String.join(" ", Objects.toString(firstName, ""), Objects.toString(lastName, "")).strip()` — `firstName + " " + lastName` produces `"null null"` when either field is null | java-conventions | PROJECT-CONTEXT.md |

## 2026-05-08 — wallet-ledger-foundation

| Rule | Category | Applied to |
|---|---|---|
| Do not call `@Transactional(REQUIRES_NEW)` methods from within `@Transactional(REQUIRED)` when atomicity is required — the inner tx commits before the outer completes; outer rollback cannot undo it. Use outbox/Kafka or restructure. | adversarial | docs/patterns/transaction-boundaries.md, PROJECT-CONTEXT.md |
| Narrow `DataIntegrityViolationException` in retry loops to the specific known race only — broad catch retries FK/NOT NULL/unique failures indefinitely, masking the real error | adversarial | docs/patterns/transaction-boundaries.md, PROJECT-CONTEXT.md |
| First-call upsert race (`findForUpdate → orElseGet → save`): wrap the INSERT in try/catch for `DataIntegrityViolationException` and re-query with lock on collision | adversarial | PROJECT-CONTEXT.md |
| Ledger-style idempotency: back application-level `existsBy(walletId, referenceType, referenceId)` with a partial unique DB index — concurrent requests can both pass the exists check before either insert commits | db-performance | PROJECT-CONTEXT.md |
| `@Modifying` native queries require `flushAutomatically=true` alongside `clearAutomatically=true` — omitting it means unflushed Hibernate state is not visible to the native query, breaking the ensureExists + locking pattern | transaction-boundaries | docs/patterns/transaction-boundaries.md |
| `@Transactional(REQUIRED)` auto-creation paths need INSERT...ON CONFLICT DO NOTHING protection, same as REQUIRES_NEW paths — no retry wrapper means a concurrent first-insert rolls back the entire outer transaction | transaction-boundaries | docs/patterns/transaction-boundaries.md |
| Unlocked aggregate SUM queries for cap enforcement allow concurrent overrun — both callers read the same total, both commit below cap, cap is exceeded; use SELECT FOR UPDATE on a sentinel row or distributed lock | adversarial | docs/patterns/transaction-boundaries.md |
| Do not add `clearAutomatically=true` to advisory lock `@Modifying` queries — pg_advisory_xact_lock modifies no rows; clearing the persistence context detaches lazy proxies, causing LazyInitializationException on the very next association access | transaction-boundaries | docs/patterns/transaction-boundaries.md |
| Throw argument validation errors before the `@Transactional` boundary — throwing inside @Transactional wastes a DB connection on a programming-error condition that never needed one; validate at construction site or non-transactional entry point | transaction-boundaries | PROJECT-CONTEXT.md |

---

## 2026-05-15 — redemption-catalog

| Rule | Category | Applied to |
|---|---|---|
| Service mutation paths must derive `clientId` from `tenantValidator.getCurrentClientId()` (JWT-bound), not `TenantContext.getClientId()` (header-spoofable) — any authenticated caller can forge `X-Client-Subdomain` to write into another tenant's data | tenant-isolation | docs/patterns/tenant-isolation.md, PROJECT-CONTEXT.md |
| External sync jobs that deactivate/delete records must fail-closed on empty or suspiciously small provider responses — abort if response would deactivate >80% of currently active records | external-integrations | PROJECT-CONTEXT.md |
| Stub/fake implementations of external API clients must carry `@Profile({"local","localtest","test"})` — an unrestricted `@Component` stub silently activates in production and can cause mass data loss | spring-profiles | PROJECT-CONTEXT.md |

---

## 2026-05-19 — redemption-catalog

| Rule | Category | Applied to |
|---|---|---|
| Guard wallet credit and ledger entry creation with `awarded > 0` — cap reduction to zero still flows into `creditInCurrentTx`; a zero-amount ledger write violates `CHECK (amount > 0)`, turning a valid fully-capped outcome into a transaction rollback | adversarial | docs/patterns/transaction-boundaries.md |
| Ledger idempotency `existsBy(walletId, referenceType, referenceId)` must also filter by `entry_type` — a RESERVE row matches a DEBIT check on the same reference, causing settlement to be silently skipped | adversarial | docs/patterns/transaction-boundaries.md |
| Advisory lock key must match the cap being enforced — a per-user key does not guard a per-partner aggregate; use `incentiveId:partnerCompanyId` for partner-level caps | adversarial | docs/patterns/transaction-boundaries.md |
| `Pageable.unpaged()` upstream of in-memory filtering is a full table scan — push filter predicates to DB and pass the real `Pageable` to the repository | db-performance | PROJECT-CONTEXT.md |
| Bounded-set `@RequestParam`/`@PathVariable` must use enum types, not `String` — Spring rejects unknown names with 400 automatically | security | PROJECT-CONTEXT.md |
| String `@PathVariable` must carry `@Pattern` + `@Size` — unconstrained path vars pass arbitrary characters into service and repository calls | security | PROJECT-CONTEXT.md |

## 2026-05-26 — redemption-flow

| Rule | Category | Applied to |
|---|---|---|
| HMAC webhook signatures must use `MessageDigest.isEqual()` — not `equals()`/`equalsIgnoreCase()` (timing attack) | security | PROJECT-CONTEXT.md |
| Never log the expected HMAC value, even at DEBUG; validate vendor path via allowlist before HMAC check | security | PROJECT-CONTEXT.md |
| Never declare `static final ObjectMapper MAPPER = new ObjectMapper()` — inject the Spring-managed bean | spring-boot | PROJECT-CONTEXT.md |
| `@Transactional` must not span a batch dispatch loop — use `REQUIRES_NEW` per item | transaction-boundaries | docs/patterns/transaction-boundaries.md |
| Concurrent batch claim requires `SELECT … FOR UPDATE SKIP LOCKED` or distributed lock to prevent double-dispatch | transaction-boundaries | docs/patterns/transaction-boundaries.md |
| `ResponseBodyAdvice.beforeBodyWrite()` wraps ALL non-excluded controller responses — add `if (body instanceof byte[]) return body;` before `ApiResponse.success(body)` to avoid ClassCastException in `ByteArrayHttpMessageConverter` | spring-boot | PROJECT-CONTEXT.md |
| Terminal-state guard (e.g. "skip if already COMPLETED") must come AFTER acquiring `SELECT FOR UPDATE` on the entity — reading status before the lock allows two concurrent callers to both pass the guard | transaction-boundaries | docs/patterns/transaction-boundaries.md |
| Inflight-count cap must be enforced AFTER acquiring the wallet lock — checking count before lock allows concurrent submissions to both pass the cap | transaction-boundaries | docs/patterns/transaction-boundaries.md |
| Load `TenantRedemptionSettings` (or any settings entity) once per service method invocation and pass derived values as params — re-querying the same entity in helper methods makes N extra DB calls per request | db-performance | PROJECT-CONTEXT.md |
| Paginated config overlay: scope the config query to the current page's item IDs — using `Pageable.unpaged()` loads ALL tenant configs and scales with total catalog size | db-performance | PROJECT-CONTEXT.md |
| `thenReturn(Mockito.any(SomeClass.class))` outside a `when()` stubbing context returns null — Mockito matchers are only meaningful inside `when()`/`verify()`; use a constant or `new SomeClass()` as the return value | testing | PROJECT-CONTEXT.md |
| JaCoCo thresholds must track actual baseline (current coverage ± 5%), not aspirational targets — build failure on day-one is not informative; raise incrementally as service tests are added | testing | PROJECT-CONTEXT.md |
| Contracts are the source of truth — always cross-reference controller `@RequestParam` list against the contract spec before feature sign-off; missing params are silent omissions until a client call fails | contract-compliance | PROJECT-CONTEXT.md |

## 2026-06-08 — redemption-history

| Rule | Category | Applied to |
|---|---|---|
| `@Async` self-invocation via `this.method()` bypasses Spring proxy — method runs synchronously; use a self-reference bean or separate component | spring-boot | PROJECT-CONTEXT.md |
| Never use `PageRequest.of(0, Integer.MAX_VALUE)` for large result exports — use chunked iteration or streaming queries to avoid OOM | db-performance | PROJECT-CONTEXT.md |
| In-memory rate limiters reset on restart and are not shared across pods — use Redis-backed distributed counter for cluster-wide enforcement | security | PROJECT-CONTEXT.md |

## 2026-06-03 — redemption-approval-queue

| Rule | Category | Applied to |
|---|---|---|
| Use typed enum parameters in JPQL `@Query`, never string literals — `AND r.status = :status` with a `@Param` binding; string literals silently return zero rows after an enum rename with no compile error | db-performance | PROJECT-CONTEXT.md |
| Dispatch-attempt markers must be stamped inside the same REQUIRES_NEW lock as the status transition — stamping outside the lock creates a TOCTOU window where a recovery query re-dispatches an in-flight item | adversarial | docs/patterns/transaction-boundaries.md |
| Webhook signing secrets must fail-fast at startup when blank — add a `@PostConstruct` or `@Validated` config-properties guard; a blank secret silently drops all webhook callbacks | security | PROJECT-CONTEXT.md |

## 2026-06-13 — redemption-returns

| Rule | Category | Applied to |
|---|---|---|
| `@Min`, `@Max`, and other Bean Validation constraints on `@RequestParam` parameters require `@Validated` on the controller class — without it, annotations are silently ignored and never enforced at runtime | spring-boot | PROJECT-CONTEXT.md |
| `afterCommit` is necessary but not sufficient for at-least-once Kafka delivery — `kafkaTemplate.send()` returns a `CompletableFuture` the callback does not wait on; attach `.whenComplete()` to every send call, or use a transactional outbox | kafka-events | docs/patterns/event-publishing.md, PROJECT-CONTEXT.md |
| `ServletUriComponentsBuilder.fromCurrentRequest()` builds Location headers from the Host/X-Forwarded-Host request header — an attacker who controls those headers can inject arbitrary URLs into 201 responses (host-header injection); require `ForwardedHeaderFilter` in strict mode or use a configured base-URL property instead | security | PROJECT-CONTEXT.md |
| `@Size(max=N)` does not reject empty strings; blank values only become invalid when `min=1` or `@NotBlank` is also present — omitting it means `""` silently passes `@Valid` | spring-boot | PROJECT-CONTEXT.md |
