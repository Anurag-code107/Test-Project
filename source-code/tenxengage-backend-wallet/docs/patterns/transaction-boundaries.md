# Transaction Boundaries

Patterns and pitfalls when designing service methods that cross transaction boundaries.

## REQUIRES_NEW + outer @Transactional atomicity gap

When a method annotated `@Transactional(propagation = REQUIRES_NEW)` is called from within a `@Transactional(REQUIRED)` outer method, the inner transaction **commits independently**. If the outer transaction subsequently rolls back, the inner transaction's side effects are permanent.

```java
// WRONG — credit commits immediately; if grantReward rolls back after, wallet is credited but no RewardTransaction recorded
@Transactional
public RewardGrantResult grantReward(RewardGrantRequest request, Incentive incentive) {
    rewardTransactionRepository.save(transaction);          // in outer tx
    walletService.credit(...);                              // REQUIRES_NEW — commits NOW
    notificationEventProducer.publish(...);                 // if this throws, outer rolls back
}                                                           // RewardTransaction rolled back, wallet credit STAYS

// CORRECT OPTIONS:
// 1. Move walletService.credit() to AFTER all validation; keep it in the same tx boundary
// 2. Use Kafka outbox pattern: publish a domain event that a consumer applies the credit after outer tx commits
// 3. Remove @Transactional from the outer orchestration method and manage atomicity at the caller
```

**When to use REQUIRES_NEW safely:** Only on isolated, stand-alone operations where partial commit is acceptable and expected by the caller (e.g., audit writes that must survive caller rollback).

## Pitfalls

- **Do not place retry-wrapper logic in a non-transactional outer method** while the retried inner method uses `REQUIRES_NEW` — this is the intended and correct pattern for optimistic-lock retry. The wrapper must stay outside any outer `@Transactional` context.
- **Optimistic-lock retry + REQUIRES_NEW** works correctly when the outer caller is NOT @Transactional. Each retry opens a fresh transaction and reads the latest committed state.
- **Narrowing `DataIntegrityViolationException` in retry loops:** When catching `DataIntegrityViolationException` for known wallet-upsert races, limit the catch scope. A broad catch that retries on ALL `DataIntegrityViolationException` subtypes will silently retry FK violations, NOT NULL violations, and other non-transient failures, masking the real error and returning a misleading generic message after N doomed retries.
- **`@Modifying` native queries need `flushAutomatically = true` when the result must be visible in the same transaction:** `@Modifying(clearAutomatically = true)` evicts the first-level cache after the native query, but without `flushAutomatically = true`, any unflushed dirty entities in the current Hibernate session are NOT written to the DB before the native query executes. This means the native INSERT/UPDATE may operate on stale data. The pattern `ensureExists()` + `findByX...ForUpdate()` is only safe when both annotations are set: `@Modifying(clearAutomatically = true, flushAutomatically = true)`.
- **`@Transactional(REQUIRED)` auto-creation paths need the same upsert protection as `REQUIRES_NEW` paths:** A method that uses `REQUIRED` propagation and creates a row on first call (`findForUpdate → orElseGet → save`) has no outer retry wrapper — unlike `REQUIRES_NEW` callers that wrap in `withOptimisticRetry()`. Two concurrent callers that both find no row will both attempt INSERT; the loser's 23505 exception propagates up and rolls back the entire outer transaction with no recovery. Apply `INSERT ... ON CONFLICT DO NOTHING` before `findForUpdate()` (same as `ensureExists()` pattern) rather than relying on the absence of a row.
- **Aggregate cap checks (`SELECT SUM`) are not concurrency-safe without row-level locking:** Unlocked aggregate queries used to enforce per-user or per-partner caps read a point-in-time total that can be stale by the time the new transaction commits. Two concurrent callers both reading the same total below the cap will both commit, overrunning the cap. Either acquire a `SELECT ... FOR UPDATE` lock on a sentinel row for the (user, incentive) combination, or use a distributed lock to serialize concurrent grants for the same key.
- **`clearAutomatically = true` must NOT be added to advisory lock `@Modifying` queries:** `pg_advisory_xact_lock` (and similar lock-only SELECTs) modify no rows — there is no stale cache to clear. Adding `clearAutomatically = true` evicts every entity from the persistence context immediately after the lock call. Any lazy `@OneToMany` or `@ManyToOne` association that has not yet been initialised becomes a detached proxy; the first navigation (e.g., `incentive.getBudgets()`) throws `LazyInitializationException` and rolls back the entire transaction. Use `flushAutomatically = true` alone on advisory lock queries. Reserve `clearAutomatically = true` for bulk INSERT/UPDATE/DELETE statements where the DB has mutated rows that Hibernate's first-level cache no longer reflects.
