# Foundation Tasks: redemption-analytics-basic

_Horizontal bedrock that all stories depend on. Execute **sequentially** — each task depends on the previous. Session granularity: one session per task._

> **Step 0 — Generate contracts first (before any foundation task):**
> ```
> cd ../tenxengage-contracts && /generate-contracts redemption-analytics-basic
> ```
> This enables FE story sessions to scaffold against generated TypeScript types from day one.

---

## Task Summary

| # | Task | Layer | Deps | Size | Done When |
|---|---|---|---|---|---|
| F1 | Enums — AuditResourceType | BE | F0 | S | `./gradlew compileJava` — `REDEMPTION_ANALYTICS_EXPORT` value present |
| F2 | Flyway V27 — permission seed + indexes | BE | F1 | S | `./gradlew flywayMigrate` — permission row + 3 composite indexes verified in DB |
| F3 | Repository query extensions | BE | F2 | M | `./gradlew test` — all new query methods compile; existing test suite still green |

_F4 skipped — permission seed is part of F2 (V27). F5 skipped — no Kafka consumers or producers in this feature._

---

## Task F1: Enums [BE] — Size: S

_Dependencies: F0 (contracts generated)_
_Done when: `./gradlew compileJava` — `REDEMPTION_ANALYTICS_EXPORT` value present in `AuditResourceType`_

**What to do:**
Add one value to an existing enum. No new enum classes needed — `CurrencyType` (MONETARY/NON_MONETARY) and all `RedemptionStatus` / `RedemptionCategory` values already exist in the codebase.

**Files:**
- `src/main/java/com/tenxengage/app/entity/enums/AuditResourceType.java`
  — add `REDEMPTION_ANALYTICS_EXPORT` to the enum values list

**No Flyway migration needed** — Java enums stored as varchar strings; the new value is added to the enum class only.

Refer to `spec.md → ## New Enums [BE]` and `## Audit Trail [BE] → New Audit Enum Values`.

---

## Task F2: Flyway V27 — Permission Seed + Indexes [BE] — Size: S

_Dependencies: F1_
_Done when: `./gradlew flywayMigrate` applies cleanly; confirm via DB inspection:_
- _`permissions` table has row for `action.redemption.view_analytics`_
- _`client_role_permissions` has rows linking `CLIENT_ADMIN` roles to this permission_
- _`client_permission_grants` has the Acme dev-seed row_
- _Three composite indexes present: `idx_ledger_entries_client_currency_type`, `idx_reward_wallets_client_currency`, `idx_redemption_requests_client_status_submitted`_

**Files:**
- `src/main/resources/db/migration/V27__seed_redemption_analytics_permissions.sql`

Copy the full SQL verbatim from `technical.md → ## Flyway Migrations [BE]` (permission seed block) and `technical.md → ## Index Recommendations [BE]` (three `CREATE INDEX CONCURRENTLY` statements). Both blocks belong in the same V27 file — or split into `V27a` + `V27b` if the team prefers DDL/DML separation; keep numbering sequential.

Refer to `spec.md → ## Permissions & Feature Flags [BE + FE]` for the permission matrix and `spec.md → ## Non-Functional Requirements` for the scaling context behind the indexes.

---

## Task F3: Repository Query Extensions [BE] — Size: M

_Dependencies: F2_
_Done when: `./gradlew test` — all new query methods compile and return correct types; zero regressions in existing test suite_

**What to do:**
Add new query methods to three **existing** repository interfaces. No new entity classes, no new repository files, no test fixtures (all source entities pre-exist from F-01 and F-03). These methods are called by `RedemptionAnalyticsService` in US-01 and US-02.

**Files (extend existing interfaces):**

`src/main/java/com/tenxengage/app/repository/LedgerEntryRepository.java`
- `sumAmountByClientIdAndCurrencyIdAndEntryType(UUID clientId, String currencyId, LedgerEntryType entryType): Long`
  — see `technical.md → ## Repository Queries [BE] → Extensions to LedgerEntryRepository`
- `findDistinctCurrencyIdsByClientId(UUID clientId): List<String>`
  — same section

`src/main/java/com/tenxengage/app/repository/RewardWalletRepository.java`
- `sumBalancesByClientIdAndCurrencyId(UUID clientId, String currencyId): BalanceSumProjection`
- `findAllByClientIdForExport(UUID clientId): List<RewardWalletExportProjection>`
  — see `technical.md → ## Repository Queries [BE] → Extensions to RewardWalletRepository`

`src/main/java/com/tenxengage/app/repository/redemption/RedemptionRequestRepository.java`
  _(or equivalent path — confirm by checking existing `RedemptionHistoryController` imports)_
- `countByClientIdAndCurrencyIdAndSubmittedAtBetween(UUID clientId, String currencyId, Instant from, Instant toExclusive): Long`
- `countByClientIdAndCurrencyIdAndStatusInAndSubmittedAtBetween(UUID clientId, String currencyId, Collection<RedemptionStatus> statuses, Instant from, Instant toExclusive): Long`
- `countGroupByStatusByClientIdAndSubmittedAtBetween(UUID clientId, Instant from, Instant toExclusive): List<StatusCountProjection>`
  — see `technical.md → ## Repository Queries [BE] → Extensions to RedemptionRequestRepository`

**Important:** `RedemptionRequest.submittedAt` is `Instant` — date params from the API layer must be converted before calling these methods:
```java
Instant from = dateFrom.atStartOfDay(ZoneOffset.UTC).toInstant();
Instant toExclusive = dateTo.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
```
This conversion lives in `RedemptionAnalyticsService`, not the repository. Refer to `technical.md → ## Repository Queries [BE] → Extensions to RedemptionRequestRepository → Date conversion note`.

**Projection interfaces to create** (if not already present):
- `BalanceSumProjection` — `getAvailable(): BigDecimal`, `getReserved(): BigDecimal`
- `RewardWalletExportProjection` — `getUserId()`, `getUserName()`, `getCompanyId()`, `getCompanyName()`, `getCurrencyType()`, `getAvailableBalance()`, `getReservedBalance()`
- `StatusCountProjection` — `getStatus(): RedemptionStatus`, `getCount(): Long`

These are Spring Data projection interfaces; they may live in a `repository/projection/` sub-package or inline in the repository file — follow the existing convention in the codebase.
