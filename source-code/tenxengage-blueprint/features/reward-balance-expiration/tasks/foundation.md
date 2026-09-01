# Foundation Tasks: reward-balance-expiration

_Horizontal bedrock that all stories depend on. Execute **sequentially** — each task depends on the previous. Session granularity: one session per task._

> **Step 0 — Generate contracts first (before any foundation task):**
> ```
> cd ../tenxengage-contracts && /generate-contracts reward-balance-expiration
> ```
> Pure spec → OpenAPI transform. Generating contracts first lets FE story sessions (US-01, US-04) scaffold against types + mocks from day one.
> **While there:** the `models/ledger-entry.md` enum is stale (missing `REVERSAL`); regenerating for F-09 must also add `EXPIRY` so it lists all 7 values, and update `enums.md` / `enums-index.md`.

---

## Task Summary

| # | Task | Layer | Deps | Parallel With | Size | Done When |
|---|---|---|---|---|---|---|
| F1 | Enums | BE | F0 | — | S | All enum classes compile; `LedgerEntryType` has `EXPIRY`; `AuditResourceType` has the 2 new values |
| F2 | Flyway migrations | BE | F1 | — | M | `./gradlew flywayMigrate` applies cleanly; both tables + indexes exist |
| F3 | Base entities + repositories + fixtures | BE | F2 | — | M | Entity classes compile; every repo query includes `clientId` (except the documented cross-tenant sweep); `./gradlew test` passes |
| F4 | Permissions + feature flag + notification-type seed | BE | F2 | — | S | Seed migration applies; 2 permission rows + flag + role/grant rows + 3 `notification_types` rows present |

_F5 (BE-only plumbing) is **N/A** — there is no standalone Kafka consumer. The `@Scheduled` expiry batch lives in US-02/US-03, and the `NotificationEventProducer` `.whenComplete` hardening folds into US-02._

---

## Task F1: Enums [BE] — Size: S

_Dependencies: F0_
_Done when: All enum classes compile; `LedgerEntryType` includes `EXPIRY`; `AuditResourceType` includes the 2 new values. No `AuditAction` change needed (`EXPIRED`, `EDITED`, `CANCELLED`, `DATA_EXPORTED` already exist)._

**Files:**
- `src/main/java/com/tenxengage/app/entity/enums/ExpirationMode.java` — new: `INACTIVITY, FIXED_DATE`
- `src/main/java/com/tenxengage/app/entity/enums/ExpiryNoticeStatus.java` — new: `SCHEDULED, NOTIFIED, EXPIRED, CANCELLED`
- `src/main/java/com/tenxengage/app/entity/enums/LedgerEntryType.java` — **add** `EXPIRY` (existing: `CREDIT, RESERVE, DEBIT, RELEASE, RETURN_CREDIT, REVERSAL`)
- `src/main/java/com/tenxengage/app/entity/enums/AuditResourceType.java` — **add** `BALANCE_EXPIRATION_POLICY`, `BALANCE_EXPIRY_BREAKAGE_EXPORT`

Refer to `spec.md → ## New Enums [BE]` and `technical.md → Java-only enum changes`. These are Java enums stored as `varchar` — no Flyway migration.

---

## Task F2: Flyway Migrations [BE] — Size: M

_Dependencies: F1_
_Done when: `./gradlew flywayMigrate` applies cleanly; confirm both tables, all indexes, and the ledger index exist via DB inspection._

**Files:**
- `src/main/resources/db/migration/V32__create_balance_expiration_tables.sql`

Creates `balance_expiration_policies` and `balance_expiry_notices` (both with `client_id`, `deleted`, `version`), the unique idempotency index `uq_balance_expiry_notices_event (wallet_id, currency_id, scheduled_expiry_date)`, the unique `uq_balance_expiration_policies_client_currency`, the partial `idx_balance_expiration_policies_enabled`, and the supporting `idx_ledger_entries_wallet_currency_created (client_id, reward_wallet_id, currency_id, created_at)`. Full DDL in `technical.md → ## Flyway Migrations [BE] → V32`.

---

## Task F3: Base Entities + Repositories + Fixtures [BE] — Size: M

_Dependencies: F2_
_Done when: Entity classes compile; every repository query includes `clientId` (except the documented cross-tenant sweep entry point in `SchedulerBalanceExpirationRepository`); `./gradlew test` passes including new fixtures._

**Files:**
- `src/main/java/com/tenxengage/app/entity/BalanceExpirationPolicy.java` — extends `BaseEntity`, implements `TenantAware`, `@Filter`
- `src/main/java/com/tenxengage/app/entity/BalanceExpiryNotice.java` — extends `BaseEntity`, implements `TenantAware`, `@Filter`
- `src/main/java/com/tenxengage/app/repository/BalanceExpirationPolicyRepository.java`
- `src/main/java/com/tenxengage/app/repository/BalanceExpiryNoticeRepository.java`
- `src/main/java/com/tenxengage/app/repository/SchedulerBalanceExpirationRepository.java` — **NOT `@Filter`-ed** (cross-tenant sweep); documented isolation deviation
- `src/main/java/com/tenxengage/app/repository/LedgerEntryRepository.java` — **add** `aggregateExpiryBreakage(...)` native query (for US-04)
- `src/test/java/com/tenxengage/app/testdata/BalanceExpirationPolicyFixtures.java` — builder-return (follow `UserFixtures.java`)
- `src/test/java/com/tenxengage/app/testdata/BalanceExpiryNoticeFixtures.java` — builder-return

Refer to `spec.md → ## Data Model / Entities [BE]` and `technical.md → ## Repository Queries [BE]`.

**Fixture rule:** new entities → fixtures are **required** here, not implicit.

---

## Task F4: Permissions + Feature Flag + Notification-type Seed [BE] — Size: S

_Dependencies: F2_
_Done when: Seed migration applies without error; the 2 permission rows visible; feature flag `reward_balance_expiration` row exists (false/true/true); CLIENT_ADMIN `client_role_permissions` rows + Acme `client_permission_grants` rows present; 3 `notification_types` rows present._

**Files:**
- `src/main/resources/db/migration/V33__seed_balance_expiration_permissions.sql`

Refer to `technical.md → ## Flyway Migrations [BE] → V33` for the complete SQL. All INSERTs use `ON CONFLICT DO NOTHING`. **Both** `client_role_permissions` (CLIENT_ADMIN) **and** `client_permission_grants` (Acme seed tenant) must be written in this migration — omitting the tenant grant strips the permission at Layer 0 of the 5-layer model (cf. V31 corrective).
