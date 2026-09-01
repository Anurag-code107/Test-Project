# Foundation Tasks: Non-Cash Returns

_Pre-implementation bedrock for [spec.md](../spec.md)._
_All paths relative to `../tenxengage-backend/` unless otherwise noted._

---

> **Step 0 — BEFORE ANY FOUNDATION TASK:**
> Run `/generate-contracts redemption-returns` in `../tenxengage-contracts/`.
> This generates the OpenAPI spec and TypeScript types that FE sessions scaffold against from day one.
> Do NOT begin F1 until the contracts repo has been updated.

---

## F1: Enums

**Repo:** `tenxengage-backend`
**Session type:** BE

### Files

| Action | Path |
|---|---|
| Create | `src/main/java/com/tenxengage/app/entity/enums/ReturnStatus.java` |
| Create | `src/main/java/com/tenxengage/app/entity/enums/ReturnResolution.java` |
| Edit | `src/main/java/com/tenxengage/app/entity/enums/AuditResourceType.java` — append `REDEMPTION_RETURN` |

### `ReturnStatus` values (6)

```java
PENDING_APPROVAL, APPROVED, RETURN_CONFIRMED, RETURN_REJECTED, CANCELLED, RETURN_TIMED_OUT
```

### `ReturnResolution` values (2)

```java
CONFIRM, REJECT
```

### `AuditResourceType` edit

Append `REDEMPTION_RETURN` to the existing enum — do not remove or reorder existing values.

### Done when

- `./gradlew compileJava` passes with no errors
- `ReturnStatus.java` has 6 values
- `ReturnResolution.java` has 2 values
- `AuditResourceType.REDEMPTION_RETURN` is present and all existing values are intact

---

## F2: Flyway V25 — Schema Migration

**Repo:** `tenxengage-backend`
**Session type:** BE
**Deps:** F1

### Files

| Action | Path |
|---|---|
| Create | `src/main/resources/db/migration/V25__create_redemption_returns_table.sql` |

### Content

Copy the full DDL verbatim from [technical.md](../technical.md) → `## Flyway Migrations → V25__create_redemption_returns_table.sql`.

Covers: `CREATE TYPE return_status`, `CREATE TABLE redemption_returns` (22 columns), 5 indexes.

### Done when

- `./gradlew bootRun` applies V25 without error
- `redemption_returns` table exists with all 22 columns
- `return_status` Postgres enum type created
- All 5 indexes created

---

## F3: Entity, Repository, and Fixtures

**Repo:** `tenxengage-backend`
**Session type:** BE
**Deps:** F1, F2

### Files

| Action | Path |
|---|---|
| Create | `src/main/java/com/tenxengage/app/entity/RedemptionReturn.java` |
| Create | `src/main/java/com/tenxengage/app/repository/RedemptionReturnRepository.java` |
| Create | `src/test/java/com/tenxengage/app/testdata/RedemptionReturnFixtures.java` |

### Entity requirements (`RedemptionReturn.java`)

- `extends BaseEntity, implements TenantAware`
- `@SQLRestriction("deleted = false")` — NOT `@Where`
- `@Version` on `version` field (optimistic locking)
- `@ManyToOne(fetch = FetchType.LAZY)` on `redemptionRequest` → `RedemptionRequest`
- All 22 columns from V25 mapped with correct JPA annotations
- `@Filter(name = "tenantFilter", condition = "client_id = :clientId")` inherited from `TenantAware`

### Repository requirements (`RedemptionReturnRepository`)

`extends JpaRepository<RedemptionReturn, UUID>` — 8 methods (exact signatures from [technical.md](../technical.md) → `## Repository Queries [BE]`):

| Method | Notes |
|---|---|
| `findByIdAndClientId(UUID id, UUID clientId)` | Admin single fetch |
| `findByIdAndClientIdAndPartnerUserId(UUID id, UUID clientId, UUID partnerUserId)` | Partner ownership |
| `findByClientIdAndPartnerUserId(UUID clientId, UUID partnerUserId, Pageable pageable)` | Partner list — NO `AndDeletedFalse` |
| `findByClientId(UUID clientId, Pageable pageable)` | Admin list — NO `AndDeletedFalse` |
| `existsByRedemptionIdAndClientIdAndStatusNotIn(UUID redemptionId, UUID clientId, List<ReturnStatus> excluded)` | Duplicate check |
| `findByVendorReturnReference(String vendorReturnReference)` | Webhook idempotency |
| `@Lock(PESSIMISTIC_WRITE) @Query("SELECT r FROM RedemptionReturn r WHERE r.id = :id") findByIdForUpdate(@Param("id") UUID id)` | Concurrent transition guard |
| `@Query("SELECT r FROM RedemptionReturn r WHERE r.clientId = :clientId AND r.status = 'APPROVED' AND r.approvedAt < :cutoff") findApprovedTimedOut(...)` | Scheduler query — NO `AND r.deleted = false` |

> **`@SQLRestriction` naming rule:** derived query methods must NOT include `AndDeletedFalse`. JPQL SELECT queries must NOT include `AND r.deleted = false`. Both are automatically applied by `@SQLRestriction`.

### Fixtures requirements (`RedemptionReturnFixtures.java`)

Builder pattern — mandatory. Include at minimum:

```java
aSubmittedReturn()      // status=PENDING_APPROVAL
anApprovedReturn()      // status=APPROVED, approvedAt set
aConfirmedReturn()      // status=RETURN_CONFIRMED, confirmedAt set
aRejectedReturn()       // status=RETURN_REJECTED, rejectedAt set
aCancelledReturn()      // status=CANCELLED, cancelledAt set
aTimedOutReturn()       // status=RETURN_TIMED_OUT, timedOutAt set
```

Reuses `RedemptionRequestFixtures`, `PartnerFixtures`, `ClientFixtures`.

### Done when

- `./gradlew compileJava` passes
- Hibernate DDL validation (schema validation mode) passes for `redemption_returns`
- All 8 repository methods compile
- `RedemptionReturnFixtures` builds all 6 status variants without error

---

## F4: Flyway V26 — Permissions + Feature Flag Seed

**Repo:** `tenxengage-backend`
**Session type:** BE
**Deps:** F3

### Files

| Action | Path |
|---|---|
| Create | `src/main/resources/db/migration/V26__seed_redemption_return_permissions.sql` |

### Content

Copy the full SQL verbatim from [technical.md](../technical.md) → `## Flyway Migrations → V26__seed_redemption_return_permissions.sql`.

Covers:
- 2 permissions: `action.redemption.return.request` (EXTERNAL, sort_order 410), `action.redemption.return.review` (INTERNAL, sort_order 411)
- Feature flag: `redemption_non_cash_returns` (starter=false, professional=true, enterprise=true)
- 4 role-grant blocks: PARTNER_ADMIN → request, PARTNER_SELLER → request, CLIENT_ADMIN → review, ACTIVITY_APPROVER → review
- Acme tenant grants for both permissions

All inserts use `ON CONFLICT DO NOTHING` for idempotency.

### Done when

- `./gradlew bootRun` applies V26 without error
- Both permission keys present in `permissions` table
- Feature flag `redemption_non_cash_returns` present with correct tier enablement
- All 4 role grant blocks applied (verify via `SELECT COUNT(*) FROM client_role_permissions WHERE permission_key LIKE 'action.redemption.return%'`)

---

## F5: BE Plumbing (Kafka + Scheduler)

**Repo:** `tenxengage-backend`
**Session type:** BE
**Deps:** F3

### Files

| Action | Path |
|---|---|
| Edit | `src/main/java/com/tenxengage/app/config/KafkaConfig.java` — add `return-events` topic bean |
| Create | `src/main/java/com/tenxengage/app/event/ReturnEvent.java` |
| Create | `src/main/java/com/tenxengage/app/service/ReturnEventProducer.java` |
| Create | `src/main/java/com/tenxengage/app/service/redemption/ReturnTimeoutScheduler.java` |

### KafkaConfig edit

Add a `NewTopic` bean for `return-events`: 3 partitions, 1 replica — consistent with existing topic beans in the file.

### `ReturnEvent` record

Java record. Fields matching base payload schema from [spec.md](../spec.md) → `## Domain Events`:

```java
UUID eventId, String eventType, Instant occurredAt, UUID clientId,
UUID returnId, UUID redemptionId, BigDecimal amount, String currencyId,
ReturnStatus status,
@Nullable UUID reviewedBy,        // present on RETURN_APPROVED, RETURN_REJECTED
@Nullable String vendorReturnReference  // present on RETURN_CONFIRMED
```

No PII — no `reason`, no `reviewNotes`.

### `ReturnEventProducer`

Publishes to topic `return-events` with partition key = `clientId` (String form). One method per event type:

```java
publishReturnRequested(RedemptionReturn)
publishReturnApproved(RedemptionReturn)
publishReturnConfirmed(RedemptionReturn)
publishReturnRejected(RedemptionReturn)
publishReturnCancelled(RedemptionReturn)
publishReturnTimedOut(RedemptionReturn)
```

Each method constructs a `ReturnEvent` and calls `kafkaTemplate.send(topic, partitionKey, event)`.

### `ReturnTimeoutScheduler`

```java
@Scheduled(cron = "0 0 * * * *")  // hourly
```

Logic:
1. Query all active tenants (or iterate over all clients)
2. For each tenant: call `findApprovedTimedOut(clientId, cutoff, pageable)` where `cutoff = Instant.now().minus(7, DAYS)`
3. Paginate — do NOT load all approved returns into memory at once
4. For each result: transition to `RETURN_TIMED_OUT`, set `timedOutAt = now()`, save, publish `RETURN_TIMED_OUT` event via `ReturnEventProducer`
5. Notify partner + CLIENT_ADMIN via notification framework (`NotificationCategory.REWARDS`)

### Unit test

Write `ReturnTimeoutSchedulerTest` — mocks `RedemptionReturnRepository` and `ReturnEventProducer`. Verifies:
- Returns beyond 7 days are transitioned to `RETURN_TIMED_OUT`
- Returns within 7 days are NOT transitioned
- `ReturnEventProducer.publishReturnTimedOut()` called once per transitioned return

### Done when

- `./gradlew compileJava` passes
- `return-events` topic bean resolves in Spring context
- `ReturnEventProducer` compiles with all 6 publish methods
- `ReturnTimeoutScheduler` unit test passes
