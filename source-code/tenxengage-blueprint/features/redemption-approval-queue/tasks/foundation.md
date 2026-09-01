# Foundation Tasks: redemption-approval-queue

_Horizontal bedrock that all stories depend on. Execute **sequentially** — each task depends on the previous. Session granularity: one session per task._

> **Step 0 — Generate contracts first (before any foundation task):**
> ```
> cd ../tenxengage-contracts && /generate-contracts redemption-approval-queue
> ```
> This enables FE story sessions to start immediately in parallel with BE foundation work.
> Contracts for this feature were partially generated in a prior session (`4490fa5`). Re-running `/generate-contracts` is idempotent — it will apply any spec updates from the final reviewed spec.

---

## Task Summary

| # | Task | Layer | Deps | Parallel With | Size | Done When |
|---|---|---|---|---|---|---|
| F1 | Enums | BE | None | — | S | `RedemptionRequestType` enum compiles; `./gradlew compileJava` passes |
| F2 | Flyway migrations | BE | F1 | — | S | `./gradlew flywayMigrate` applies V18 cleanly; 3 new columns exist on `redemption_requests` |
| F3 | Base entities + repos + fixtures | BE | F2 | — | M | Entity compiles with new fields; both new repo queries pass; `./gradlew test` passes |
| F4 | Permissions + feature flags seed | BE | F2 | F3 | S | V19 applies; `action.redemption.approve` row in `permissions` table; role grants exist |
| ~~F5~~ | ~~BE-only plumbing~~ | — | — | — | — | **SKIP** — no new Kafka infrastructure; `NotificationEventProducer` already exists and is reused directly inside story service methods |

---

## Task F1: Enums [BE] — Size: S

_Dependencies: None (after contracts generated)_
_Parallel with: None_
_Done when: `./gradlew compileJava` passes; `RedemptionRequestType` is importable_

**Context:** The spec's `## New Enums [BE]` section states "None" — all `RedemptionStatus`, `AuditAction`, and `AuditResourceType` values needed by F-04 already exist from F-03. However, the `requestType` query param on `GET /approval-queue` requires a Java enum for proper Spring MVC binding and validation. This task adds only that one enum.

**Files:**
- `src/main/java/com/tenxengage/app/entity/enums/RedemptionRequestType.java` — new enum

```java
// Values: REDEMPTION (standard redemption), RETURN (F-06 stub in F-04 — always returns empty)
public enum RedemptionRequestType {
    REDEMPTION,
    RETURN
}
```

Refer to `spec.md → ## API Endpoints [BE + FE]` — `requestType` query param allowlist.

---

## Task F2: Flyway Migrations [BE] — Size: S

_Dependencies: F1_
_Parallel with: None_
_Done when: `./gradlew flywayMigrate` applies V20 cleanly; `\d redemption_requests` shows the 3 new nullable columns_

**Context:** F-04 extends the existing `redemption_requests` table with 3 nullable columns. No new tables — ALTER TABLE only. The permission seed is in F4 (V21). Migration renumbered from V18→V20 because catalog enhancements (V18 image_url, V19 ISO codes) were merged in first.

**Files:**
- `src/main/resources/db/migration/V20__alter_redemption_request_add_approval_fields.sql`

Refer to `technical.md → ## Flyway Migrations [BE] → V18` for exact SQL (file renamed to V20). Key points:
- All 3 columns are NULLABLE (null until a decision is made)
- `reviewed_by` has FK → `users(id)` — no cascade, just a reference
- No new index needed — `idx_redemption_requests_client_status` on `(client_id, status)` already covers the approval queue query

---

## Task F3: Base Entities + Repositories + Fixtures [BE] — Size: M

_Dependencies: F2_
_Parallel with: None_
_Done when: Entity compiles with new fields; `findApprovalQueue` and `findByIdAndClientIdForUpdate` queries pass; `./gradlew test` passes including existing `RedemptionRequestFixtures` usages_

**Context:** F-04 modifies 3 existing files — no new entity classes. `RedemptionRequestFixtures.java` was created by F-03 and exists on `roadmaps/redemption-store`. It needs new builder methods for the approval fields.

**Files (all modifications to existing F-03 files):**
- `src/main/java/com/tenxengage/app/entity/RedemptionRequest.java` — add 3 fields:
  - `@ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "reviewed_by") User user` — association for JOIN FETCH in approval queue query
  - `@Column(name = "reviewed_at") Instant reviewedAt`
  - `@Column(name = "rejection_reason", length = 1000) String rejectionReason`

- `src/main/java/com/tenxengage/app/repository/RedemptionRequestRepository.java` — add 2 new query methods:
  - `findApprovalQueue(clientId, currencyId, catalogItemId, startDate, endDate, pageable)` — JPQL with `JOIN FETCH r.user JOIN FETCH r.catalogItem` to avoid N+1
  - `findByIdAndClientIdForUpdate(id, clientId)` — `@Lock(PESSIMISTIC_WRITE)` for approve/reject

  Refer to `technical.md → ## Repository Queries [BE]` for exact JPQL.

- `src/test/java/com/tenxengage/app/testdata/RedemptionRequestFixtures.java` — add builder methods:
  - `withReviewedBy(UUID reviewedBy)` — sets reviewedBy/reviewedAt together (reviewedAt = Instant.now())
  - `withRejectionReason(String reason)` — sets rejectionReason field
  - `pendingApproval()` — convenience builder returning a `PENDING_APPROVAL` status fixture

---

## Task F4: Permissions + Feature Flags Seed [BE] — Size: S

_Dependencies: F2 (tables must exist for FK references)_
_Parallel with: F3_
_Done when: V21 migration applies without error; `SELECT * FROM permissions WHERE permission_key = 'action.redemption.approve'` returns 1 row; `SELECT * FROM client_role_permissions WHERE permission_key = 'action.redemption.approve'` returns rows for CLIENT_ADMIN and ACTIVITY_APPROVER base roles_

**Files:**
- `src/main/resources/db/migration/V21__seed_redemption_approval_permissions.sql`

Refer to `technical.md → ## Flyway Migrations [BE] → V19` for exact SQL (file renamed to V21). Key points:
- Permission: `action.redemption.approve`, category `REDEMPTION_ACTIONS`, type `ACTION`, scope `INTERNAL`, sort_order `405`
- Grants: CLIENT_ADMIN + ACTIVITY_APPROVER base roles (via `client_roles` cross join)
- Dev/seed: Acme tenant grant (`client_id = 'a0000000-0000-0000-0000-000000000001'`)
- All inserts use `ON CONFLICT DO NOTHING` for idempotency
- No new feature flag — `redemption_store` flag (seeded in F-01 V8) covers this feature
