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
