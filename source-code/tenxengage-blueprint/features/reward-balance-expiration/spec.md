---
slug: reward-balance-expiration
name: Balance Expiration
status: reviewed
reviewed: 2026-06-24
format: story-sliced
roadmap: redemption-store
domain: null
builder_type: null
created: 2026-06-24
contract: null
visual_reference:
  component_path: null
  notes: null
applicable_sections:
  source: null
  sections: []
---

# Feature: Balance Expiration

> **Reviewed**: 2026-06-24 — `/review-spec`: 0 critical, 3 warnings resolved (naming reconciliation, rate-limiter reference, planning seeds).
>
> **Format:** story-sliced
> **Stories, tasks, and per-story tests live in sibling files:**
> - [`stories.md`](stories.md) — story index + dependency graph
> - [`stories/`](stories/) — one `US-NN-*.md` per story (self-contained execution unit)
> - [`tasks/foundation.md`](tasks/foundation.md) — horizontal bedrock tasks
> - [`tracker.md`](tracker.md) — session status tracker
> - [`test-plan.md`](test-plan.md) — cross-story integration tests
>
> **This file is the design reference.** Implementers read it alongside their story file.
>
> **Technical artifacts** (Flyway SQL, file paths, hook specs): see [`technical.md`](technical.md).

---

## Overview

Balance Expiration lets a Client Admin define, per currency type, when unused reward balances expire — either after a configurable period of partner inactivity or on a fixed calendar date — with an advance notification before expiry, an immutable expiry-debit ledger entry at expiry, and a breakage report that accounts for the expired value. Expiration is **off by default and opt-in per currency type**; cash never expires unless explicitly enabled. This is a Phase 2 redemption-store capability (BRD: "Reward balance expiration with configurable policy and breakage reporting"). It builds entirely on the existing ledger-first wallet engine (F-01): expiry is modelled as a new `LedgerEntryType.EXPIRY` debit against a wallet's `availableBalance`, never a deletion of balance, so totals remain derivable from the ledger sum. Reserved/in-flight balance is never touched.

### Naming reconciliation

`roadmaps/redemption-store/digest-annex.md` is treated as **advisory** — the spec's named artifacts reflect codebase patterns, not BRD-stated names. Decisions:

| Concept (digest-annex / BRD) | Spec artifact | Adopted / New | Rationale |
|---|---|---|---|
| Wallet, available balance | `RewardWallet.availableBalance` | Adopted | Existing F-01 entity (`reward_wallets`) — no rename |
| Ledger, expiry debit | `LedgerEntry` + `LedgerEntryType.EXPIRY` | Adopted (extends enum) | Reuse the immutable ledger; `EXPIRY` extends the existing 6-value enum — not a new entity |
| "Inactivity period" | ledger-derived `MAX(created_at)` over activity entry types | New (derived, no entity) | No `last_activity` column exists on `reward_wallets`; ledger-first principle says derive it |
| Rewards notifications | `NotificationCategory.REWARDS` + `notification_types` seed rows | Adopted | Existing enum + DB-backed notification type registry |
| Expiration policy | `BalanceExpirationPolicy` (new) | New | No existing entity models per-currency expiry config |
| Notice / scheduling | `BalanceExpiryNotice` (new) | New | No existing entity tracks once-only notice + idempotent expiry events |
| "Breakage" | F-09-owned breakage report aggregating `LedgerEntry` (`entry_type = EXPIRY`) | New (report, no entity) | Review default C: F-09 owns its report; does not reopen F-07/F-08 analytics |

---

## Functional Requirements

| ID | Requirement | Entity / Endpoint | Notes |
|---|---|---|---|
| FR-09.1 | CLIENT_ADMIN can configure an expiration policy **per currency type** — either an **inactivity period** (expire N days after last activity) or a **fixed calendar date**. | `BalanceExpirationPolicy` · `PUT /redemption/expiration/policies/{currencyId}` | `expirationMode ∈ {INACTIVITY, FIXED_DATE}` |
| FR-09.2 | Cash balances do **not** expire by default; expiration is opt-in **per currency type and per client**. | `BalanceExpirationPolicy.enabled` | One policy row per `(client_id, currency_id)` |
| FR-09.3 | Expiration must be **explicitly enabled** by the Client Admin; there is no platform default policy. | `enabled = false` default | Gated by feature flag `reward_balance_expiration` |
| FR-09.4 | The system sends an **advance notification** at a configurable lead time before expiry (stating amount and expiry date), **exactly once per expiry event**. | `BalanceExpiryNotice` · `BALANCE_EXPIRING_SOON` notification | Dedup on `notified_at` marker |
| FR-09.5 | At expiry, the system writes an **expiry-debit ledger entry**, reduces the wallet's `availableBalance`, and **notifies the partner**. | `LedgerEntryType.EXPIRY` · `BALANCE_EXPIRED` notification | Ledger-first: entry written before total update |
| FR-09.6 | Breakage (expired value) is **separately trackable**; CLIENT_ADMIN can view and export it **by currency type and period**. | `GET /redemption/expiration/breakage[/export]` | Aggregates `LedgerEntry` where `entry_type = EXPIRY` |
| FR-09.7 | No **retroactive** expiry without prior notice — a balance is only expired if a delivered advance notice exists for that expiry event. | grace-period + `BalanceExpiryNotice` state | See ADR #4 (grace window) |
| FR-09.8 | The expiry batch is **idempotent** — each balance is expired at most once per expiry event; a retry never produces a double-debit. | unique `(wallet_id, currency_id, scheduled_expiry_date)` | Re-run safe |
| FR-09.9 | Policy configuration is **validated** per currency type: inactivity within allowed bounds, fixed date in the future, lead time ≥ 1 day and strictly less than the inactivity window; invalid config is rejected with `422`. | service-layer validation → `422` | `errorCode` shape, not generic `VALIDATION_ERROR` |
| FR-09.10 | Disabling or relaxing a policy **cancels not-yet-executed (already-notified) expirations** and **re-notifies** affected partners. | `BalanceExpiryNotice → CANCELLED` · `BALANCE_EXPIRY_CANCELLED` notification | Triggered on policy update |
| FR-09.11 | Expiry is **atomic with respect to concurrent redemption** — only currently `available`/unreserved balance is expired, under a row lock, so an in-flight reservation is never double-spent. | row lock on `RewardWallet` · `@Version` | Expiry uses live `availableBalance` at execution |

---

## Functional Completeness Audit

| # | Dimension | Status | FR / Notes |
|---|---|---|---|
| 1 | Per-currency, opt-in policy with two expiry modes (inactivity / fixed date) | ✓ Already covered | FR-09.1, FR-09.2 — inherited from feature brief |
| 2 | Advance-notice timing & once-only delivery | ⊕ Modified | FR-09.4 — "fires exactly once per expiry event" (review default A: dedup on `notified_at`) |
| 3 | Idempotent expiry batch (no double-debit on retry) | ⊕ Approved | FR-09.8 — unique `(wallet_id, currency_id, scheduled_expiry_date)` |
| 4 | Policy config validation (bounds, future date, lead-time relation) | ⊕ Approved | FR-09.9 — `422` with `errorCode` |
| 5 | Disable/relax cancels pending expirations + re-notifies | ⊕ Approved | FR-09.10 — `BalanceExpiryNotice → CANCELLED` |
| 6 | Atomicity vs concurrent redemption (reserved balance protected) | ⊕ Approved | FR-09.11 — row lock; only `availableBalance` expires |
| 7 | Wallet scope — individual vs company | ✓ Already covered | Business Rules (review default B: applies to **both** wallet types) |
| 8 | Lot/FIFO vs whole-balance expiry | ⊕ Rejected | Review default D: **whole available balance** expires; lot-level FIFO is Out of Scope |
| 9 | Activity definition for the inactivity clock | ⊕ Modified | Business Rules (review default E: **per-currency**; earn or redeem of currency X resets only X's clock — ADR #1, #5) |
| 10 | Breakage report ownership | ⊕ Approved | FR-09.6 — F-09 owns its own report/endpoint; does **not** reopen F-08 analytics (review default C) |

---

## Planning seeds (from feature brief)

_Verbatim from `roadmaps/redemption-store/features/F-09-redemption-store.md` → Suggested story seeds. `/create-stories` starts story identification from this skeleton. FRs FR-09.1–FR-09.7 are inherited verbatim from the brief (numbering preserved); FR-09.8–FR-09.11 are net-new, appended in sequence by this spec (the brief's open questions on inactivity definition, lead-time configurability, and reserved-balance interaction are resolved here via ADRs #1/#2/#3)._

| # | Title | Business outcome | Type | Depends on |
|---|---|---|---|---|
| S-01 | Configure balance expiration policy | Client Admin enables and configures per-currency expiration rules for their tenant | admin | F-01.S-01 |
| S-02 | Notify partners of approaching expiry | Partners receive advance warning before their balance expires so they can act | workflow | S-01 |
| S-03 | Execute balance expiration | System applies expiry debits and records immutable ledger entries at the configured expiry point | rules | S-01 |
| S-04 | Report on breakage | Client Admin views and exports expired balance totals by currency type and period | reporting | S-03 |

---

## Non-Functional Requirements

| Dimension | Requirement | Notes |
|---|---|---|
| **Response time (reads)** | P95 < 300ms | Policy list, breakage report (aggregate query) |
| **Response time (writes)** | P95 < 500ms | Policy upsert |
| **Expiry batch** | Off-peak, per-tenant; completes within the maintenance window | Scheduled cross-tenant sweep — not request-path; retry/degraded acceptable |
| **Peak concurrent users** | Low (Client-Admin config surface) | Config + report are admin-only, low volume |
| **Max page size** | 50 items | Hard cap on any paged list |
| **Availability** | Internal admin tool; batch retry/degraded acceptable | A missed batch run resumes on next schedule without data loss (idempotent) |
| **Data sensitivity** | CONFIDENTIAL (financial — wallet balances, ledger) | No PII stored by this feature; breakage report is aggregate-only |
| **Compliance** | Audit retention; expiry debits must be **distinguishable** from redemption debits | `LedgerEntryType.EXPIRY` |
| **Audit retention** | 7 years | Append-only; expiry events + policy changes + exports |

---

## Prerequisites

- [ ] Spec reviewed via `/review-spec` (status must be `reviewed`)
- [ ] Contracts generated via `/generate-contracts` in the backend repo
- [ ] Next Flyway migration number confirmed (current latest: **V31** → this feature uses **V32** for entity tables and **V33** for permissions + feature flag seed)
- [ ] F-01 wallet/ledger foundation present (`RewardWallet`, `LedgerEntry`, `LedgerEntryType`)

---

## New Enums [BE]

| Enum Class | Values | Notes |
|---|---|---|
| `LedgerEntryType.java` | add `EXPIRY` (existing: `CREDIT, RESERVE, DEBIT, RELEASE, RETURN_CREDIT, REVERSAL`) | The expiry debit type — keeps expiry distinguishable from redemption `DEBIT` (NFR compliance) |
| `ExpirationMode.java` | `INACTIVITY, FIXED_DATE` | Drives which policy fields are required (FR-09.1) |
| `ExpiryNoticeStatus.java` | `SCHEDULED, NOTIFIED, EXPIRED, CANCELLED` | `BalanceExpiryNotice` lifecycle |

_Path: `src/main/java/com/tenxengage/app/entity/enums/`_

---

## Data Model / Entities [BE]

### Entity-shape decisions

| Entity | Shape | Source |
|---|---|---|
| `BalanceExpirationPolicy` | Hardcoded JPA entity | This spec |
| `BalanceExpiryNotice` | Hardcoded JPA entity | This spec |

_Both are operational/financial entities with fixed schemas — not tenant-editable Managed Data._

### BalanceExpirationPolicy (table: `balance_expiration_policies`)

_Path: `src/main/java/com/tenxengage/app/entity/`_
_Extends `BaseEntity`, implements `TenantAware`_
_Carries `@Filter(name="tenantFilter", condition="client_id = :clientId")`_

| Column | Type | Constraints | Notes |
|---|---|---|---|
| `id` | `UUID` | PK, default `gen_random_uuid()` | Inherited from BaseEntity |
| `client_id` | `UUID` | NOT NULL, FK → clients | Tenant isolation — NEVER exposed in API responses |
| `currency_id` | `VARCHAR(50)` | NOT NULL | One of the four currency types (cash/points/credits/tickets) |
| `enabled` | `BOOLEAN` | NOT NULL, DEFAULT `false` | FR-09.2/09.3 — opt-in, no platform default |
| `expiration_mode` | `VARCHAR(20)` | NOT NULL | `ExpirationMode` — `INACTIVITY` \| `FIXED_DATE` |
| `inactivity_days` | `INTEGER` | NULL | Required when `expiration_mode = INACTIVITY`; the inactivity window |
| `fixed_expiry_date` | `DATE` | NULL | Required when `expiration_mode = FIXED_DATE`; must be in the future at config time |
| `lead_time_days` | `INTEGER` | NOT NULL, DEFAULT `30` | Advance-notice lead time (ADR #2). `≥ 1` and `< inactivity_days` when mode = INACTIVITY |
| `enabled_at` | `TIMESTAMPTZ` | NULL | Set when the policy is (re-)enabled or materially changed; **grace-window anchor** (ADR #4) |
| `created_at` | `TIMESTAMPTZ` | NOT NULL | Inherited from BaseEntity |
| `updated_at` | `TIMESTAMPTZ` | NOT NULL | Inherited from BaseEntity |
| `deleted` | `BOOLEAN` | NOT NULL, DEFAULT `false` | Soft delete |
| `version` | `BIGINT` | NOT NULL, DEFAULT `0` | Optimistic locking (`@Version`) |

**PII Fields:** none.

**Relationships:**
- `@OneToMany` → `BalanceExpiryNotice` (mappedBy: `policy`)

**Indexes:**
- `uq_balance_expiration_policies_client_currency` UNIQUE on `(client_id, currency_id)` — one policy per currency type per client
- `idx_balance_expiration_policies_enabled` on `(enabled)` `WHERE enabled = true AND deleted = false` — supports the cross-tenant batch's enabled-policy scan

### BalanceExpiryNotice (table: `balance_expiry_notices`)

_Path: `src/main/java/com/tenxengage/app/entity/`_
_Extends `BaseEntity`, implements `TenantAware`_
_Carries `@Filter(name="tenantFilter", condition="client_id = :clientId")`_

Tracks one scheduled expiry event for one wallet+currency: enforces once-only advance notice (FR-09.4), idempotent expiry (FR-09.8), and re-notify-on-cancel (FR-09.10).

| Column | Type | Constraints | Notes |
|---|---|---|---|
| `id` | `UUID` | PK, default `gen_random_uuid()` | Inherited from BaseEntity |
| `client_id` | `UUID` | NOT NULL, FK → clients | Tenant isolation — explicitly bound by the batch per wallet (no `@Filter` in scheduler path) |
| `wallet_id` | `UUID` | NOT NULL, FK → reward_wallets | The wallet whose balance is scheduled to expire |
| `currency_id` | `VARCHAR(50)` | NOT NULL | Currency type of the expiring balance |
| `policy_id` | `UUID` | NOT NULL, FK → balance_expiration_policies | The governing policy |
| `scheduled_expiry_date` | `DATE` | NOT NULL | Computed expiry date for this event |
| `status` | `VARCHAR(20)` | NOT NULL, DEFAULT `SCHEDULED` | `ExpiryNoticeStatus` |
| `notified_at` | `TIMESTAMPTZ` | NULL | When advance notice was sent — once-only dedup marker (FR-09.4) |
| `notified_amount` | `NUMERIC(18,2)` | NULL | Amount stated in the advance notice (snapshot at notify time) |
| `expired_at` | `TIMESTAMPTZ` | NULL | When the expiry debit executed |
| `expired_amount` | `NUMERIC(18,2)` | NULL | Amount actually expired (back-reference; breakage is sourced from the ledger) |
| `ledger_entry_id` | `UUID` | NULL, FK → ledger_entries | The `EXPIRY` debit entry (FR-09.5) |
| `cancelled_at` | `TIMESTAMPTZ` | NULL | When cancelled by a policy disable/relax (FR-09.10) |
| `created_at` | `TIMESTAMPTZ` | NOT NULL | Inherited |
| `updated_at` | `TIMESTAMPTZ` | NOT NULL | Inherited |
| `deleted` | `BOOLEAN` | NOT NULL, DEFAULT `false` | Soft delete (notices are retained for history; not hard-deleted) |
| `version` | `BIGINT` | NOT NULL, DEFAULT `0` | Optimistic locking |

**PII Fields:** none (`wallet_id` is an opaque UUID reference).

**Relationships:**
- `@ManyToOne` → `BalanceExpirationPolicy` (FK: `policy_id`)
- `@ManyToOne` → `RewardWallet` (FK: `wallet_id`)
- references `LedgerEntry` (FK: `ledger_entry_id`)

**Indexes:**
- `uq_balance_expiry_notices_event` UNIQUE on `(wallet_id, currency_id, scheduled_expiry_date)` — **idempotency key** (FR-09.8): at most one notice per wallet+currency+expiry-event
- `idx_balance_expiry_notices_status_date` on `(status, scheduled_expiry_date)` — supports the batch's "due to warn / due to expire" scans
- `idx_balance_expiry_notices_client` on `(client_id)`

### Reused entities (no new tables)

- **`RewardWallet`** (`reward_wallets`) — `availableBalance` is debited; `reservedBalance` is never touched (ADR #3). Optimistic `@Version` already present. No schema change.
- **`LedgerEntry`** (`ledger_entries`) — expiry writes a new entry with `entry_type = EXPIRY` (FR-09.5). The inactivity clock (ADR #1, #5) is **ledger-derived**: last activity for a wallet+currency = `MAX(created_at)` of entries with `entry_type ∈ {CREDIT, DEBIT, RESERVE, RETURN_CREDIT}` (earn/redeem/return movements). `RELEASE`, `REVERSAL`, and `EXPIRY` itself do **not** reset the clock. No schema change.
- **`TenantRedemptionSettings`** — unchanged; balance-expiration policy is a sibling config surface, not folded into settings.

**Supporting index (existing table):**
- `idx_ledger_entries_wallet_currency_created` on `ledger_entries (client_id, reward_wallet_id, currency_id, created_at)` — supports the ledger-derived last-activity lookup (`MAX(created_at)` filtered by `reward_wallet_id` + `currency_id` + activity entry types); `reward_wallet_id` is leading after `client_id` because `findLastActivityAt` filters per wallet. Distinct from the existing `idx_ledger_entries_client_currency_type` (V27).

---

## Permissions & Feature Flags [BE + FE]

### Permission Matrix

`module.redemption_store` (seeded F-01 V8) is **reused** — no new module key. Both new action keys are CLIENT_ADMIN-only; partner roles never configure or view breakage (they only receive expiry notifications). The expiry batch runs as a **SYSTEM actor** and is not permission-gated.

| Permission Key | Display Name | Type | Scope | Category | CLIENT_ADMIN | ACTIVITY_APPROVER | PARTNER_ADMIN | PARTNER_SELLER |
|---|---|---|---|---|---|---|---|---|
| `action.redemption.expiration.configure` | Configure Balance Expiration | ACTION | `INTERNAL` | REDEMPTION_ACTIONS | Y | — | — | — |
| `action.redemption.expiration.view_breakage` | View Balance Expiration Breakage | ACTION | `INTERNAL` | REDEMPTION_ACTIONS | Y | — | — | — |

_`sort_order`: 413 (`configure`), 414 (`view_breakage`) — continues the redemption-actions sequence (view_analytics = 412)._
_Every key above appears in the Flyway seed's role block AND the tenant-grant block (see `technical.md`)._

### Feature Flag

| Feature Key | Description | starterEnabled | professionalEnabled | enterpriseEnabled | Category |
|---|---|---|---|---|---|
| `reward_balance_expiration` | Reward Balance Expiration — per-currency expiration policies (inactivity or fixed date), advance-expiry + expiry notifications, and breakage reporting/CSV export | `false` | `true` | `true` | REWARDS |

_Flyway seed SQL for this matrix + flag lives in `technical.md → ## Flyway Migrations [BE]` (V33). The seed writes BOTH `client_role_permissions` (CLIENT_ADMIN) and `client_permission_grants` (Acme seed tenant) in the same migration — omitting the tenant grant strips the permission at Layer 0 of the 5-layer model (cf. V31 corrective)._

---

## DTOs [BE]

### Request DTOs

_Path: `src/main/java/com/tenxengage/app/dto/request/`_

| Record | Key Fields | Validation |
|---|---|---|
| `UpsertBalanceExpirationPolicyRequest` | `enabled` (Boolean), `expirationMode` (ExpirationMode), `inactivityDays` (Integer, nullable), `fixedExpiryDate` (LocalDate, nullable), `leadTimeDays` (Integer) | Structural only: `@NotNull enabled`, `@NotNull expirationMode`, `@NotNull leadTimeDays`. Cross-field + bounds rules (FR-09.9) live in the **service** and throw a domain exception so `GlobalExceptionHandler` returns the spec `errorCode` shape with `422`. Do NOT add `@Pattern`/range constraints that would mask the service error. |

**Validation rules (service layer → `422`, `errorCode`):**
- `expirationMode = INACTIVITY` ⇒ `inactivityDays` required, within `[MIN_INACTIVITY_DAYS=30, MAX_INACTIVITY_DAYS=1825]`; `fixedExpiryDate` must be null.
- `expirationMode = FIXED_DATE` ⇒ `fixedExpiryDate` required and strictly in the future (relative to tenant date); `inactivityDays` must be null.
- `leadTimeDays ≥ 1`; when mode = INACTIVITY, `leadTimeDays < inactivityDays`.
- `currencyId` (path) must be a recognised currency type.

### Response DTOs

_Path: `src/main/java/com/tenxengage/app/dto/response/`_

| Record | Static Factory | Rendered Fields |
|---|---|---|
| `BalanceExpirationPolicyResponse` | `from(BalanceExpirationPolicy)` | `currencyId` (String — currency code), `currencyDisplayName` (String — label shown in the form header), `enabled` (boolean — toggle state), `expirationMode` (enum — radio selection), `inactivityDays` (Integer — numeric field, shown when INACTIVITY), `fixedExpiryDate` (LocalDate — date field, shown when FIXED_DATE), `leadTimeDays` (Integer — numeric field), `enabledAt` (Instant — "active since" caption), `updatedAt` (Instant — "last changed" caption) |
| `BalanceBreakageReportResponse` | `from(from, to, granularity, List<BreakageRowDto>)` | `from` (LocalDate), `to` (LocalDate), `granularity` (enum `MONTH`\|`QUARTER`), `rows` (List<BreakageRowDto>) |
| `BreakageRowDto` (nested) | — | `periodStart` (LocalDate — column), `periodEnd` (LocalDate — column), `currencyId` (String — column), `currencyDisplayName` (String — column label), `expiredCount` (long — # of expiry events, column), `totalExpiredAmount` (BigDecimal — column, formatted with currency) |
| `ExpiringBalancePreviewResponse` | `from(...)` | `currencyId` (String), `currencyDisplayName` (String), `scheduledExpiryDate` (LocalDate), `affectedWalletCount` (long — aggregate count, no per-wallet identity), `totalAmountAtRisk` (BigDecimal) |

**Never include in responses:** `client_id`, `wallet_id` (individual wallet identity is not surfaced — preview is aggregate-only), `deleted`, `version`.

_`currencyDisplayName` is a convenience mirror of the currency label; per the FE convention (`config/currencies.ts`) the FE still renders labels via `getCurrency(currencyId.toLowerCase()).label` as the source of truth and treats `currencyDisplayName` as a fallback only — to avoid drift from `currencies.ts`._

_All response/request shapes are generated into `../tenxengage-contracts/`. Do not hand-write Java records in the spec._

---

## API Endpoints [BE + FE]

_Base path: `/api/v1/redemption/expiration`_
_Tag: `Balance Expiration`_

| Method | Path | Request Body | Response | Status | Permission | Audit |
|---|---|---|---|---|---|---|
| `GET` | `/policies` | — | `List<BalanceExpirationPolicyResponse>` | 200 | `action.redemption.expiration.configure` | — |
| `PUT` | `/policies/{currencyId}` | `UpsertBalanceExpirationPolicyRequest` | `BalanceExpirationPolicyResponse` | 200 | `action.redemption.expiration.configure` | `@Audited` (Created/Edited; relax/disable → Cancelled side-effects) |
| `GET` | `/expiring-soon` | — | `List<ExpiringBalancePreviewResponse>` | 200 | `action.redemption.expiration.configure` | — |
| `GET` | `/breakage` | — | `BalanceBreakageReportResponse` | 200 | `action.redemption.expiration.view_breakage` | — |
| `GET` | `/breakage/export` | — | `text/csv` stream | 200 | `action.redemption.expiration.view_breakage` | `@Audited` (`DATA_EXPORTED`) |

_The `Permission` column uses the exact `permission_key` values from the matrix above → `@RequiresPermission("...")` on each controller method (mirrors `RedemptionConfigController`)._

**Query parameters:**
- `GET /breakage` and `/breakage/export`: `from` (LocalDate, required), `to` (LocalDate, required), `currencyId` (optional filter), `granularity` (`MONTH`\|`QUARTER`, default `MONTH`). Reject `to < from` with `400`. Range capped at 24 months.
- `GET /expiring-soon`: `withinDays` (optional, default = max configured `leadTimeDays`), `currencyId` (optional filter).
- `GET /policies`: returns all currency-type policies for the tenant (≤ 4 rows) — no pagination needed.

**Error responses:**
- `400` — `to < from`, range > 24 months, unknown `currencyId` filter, unknown `granularity`
- `401` — Not authenticated
- `403` — Missing permission
- `404` — n/a (policies are keyed by currency, upsert-on-PUT; no cross-tenant ID lookups)
- `422` — Invalid policy configuration (FR-09.9) with `errorCode`
- `429` — Rate limit exceeded (CSV export)

---

## Service Layer [BE]

_Path: `src/main/java/com/tenxengage/app/service/redemption/`_

### BalanceExpirationPolicyService

| Method | Return Type | Notes |
|---|---|---|
| `getPolicies()` | `List<BalanceExpirationPolicyResponse>` | `@Transactional(readOnly=true)` — tenant-scoped via `@Filter` |
| `upsertPolicy(currencyId, request)` | `BalanceExpirationPolicyResponse` | `@Transactional` — validates (FR-09.9); sets `enabled_at` on enable/material change; on disable/relax, cancels pending notices + re-notifies (FR-09.10) |
| `getExpiringSoon(withinDays, currencyId)` | `List<ExpiringBalancePreviewResponse>` | `@Transactional(readOnly=true)` — aggregate preview |

### BalanceBreakageReportService

| Method | Return Type | Notes |
|---|---|---|
| `getBreakage(from, to, currencyId, granularity)` | `BalanceBreakageReportResponse` | `@Transactional(readOnly=true)` — aggregates `LedgerEntry` where `entry_type = EXPIRY` |
| `exportBreakageCsv(from, to, currencyId, granularity)` | `String` (CSV) | `@Transactional(readOnly=true)` — all string cells CSV-escaped for formula injection (CWE-1236) via a shared util; `escapeCsv` must be **promoted** from `RedemptionAnalyticsService` (currently `private`) to a shared `CsvUtil`/helper, not called cross-service. Audited `DATA_EXPORTED`; rate-limited via `AnalyticsExportRateLimiter` |

### BalanceExpiryBatchService (scheduled — SYSTEM actor)

| Method | Return Type | Notes |
|---|---|---|
| `runExpirySweep()` | `void` | `@Scheduled` off-peak. Cross-tenant: iterates enabled policies via a dedicated **`SchedulerBalanceExpirationRepository`** (NOT `@Filter`-ed); binds `client_id` explicitly per wallet. Two phases: (1) **warn** — create/advance `BalanceExpiryNotice` to `NOTIFIED` and send `BALANCE_EXPIRING_SOON` for balances entering the lead window; (2) **expire** — for `NOTIFIED` notices whose `scheduled_expiry_date ≤ today`, under a row lock on the wallet, write the `EXPIRY` ledger entry, reduce `availableBalance`, mark notice `EXPIRED`, send `BALANCE_EXPIRED`. Idempotent on the unique notice key. |

**Business rules:**
- **Opt-in only** (FR-09.2/09.3): only policies with `enabled = true` are processed; cash is never processed unless an enabled cash policy exists.
- **Wallet scope** (review default B): applies to **both** `INDIVIDUAL` and `COMPANY` wallets.
- **Inactivity clock** (ADR #1, #5): per-currency; `lastActivityAt(wallet, currency) = MAX(ledger_entries.created_at)` over `entry_type ∈ {CREDIT, DEBIT, RESERVE, RETURN_CREDIT}`. Expiry candidate when `lastActivityAt + inactivity_days ≤ today` (INACTIVITY) or `today ≥ fixed_expiry_date` (FIXED_DATE).
- **Grace window** (ADR #4, FR-09.7): a wallet cannot be expired until at least one full `lead_time_days` window has elapsed since `enabled_at` — a newly enabled or relaxed policy never expires anything immediately, and never retroactively without notice.
- **Reserved balance protected** (ADR #3, FR-09.11): only `availableBalance` is expired; `reservedBalance` is never touched. The expiry amount is the live `availableBalance` read under the row lock at execution.
- **Whole-balance** (review default D): the entire available balance of the currency expires — no FIFO/lot accounting.
- **Once-only notice** (FR-09.4): advance notice sent only if `notified_at IS NULL`; setting it dedups re-sends across batch retries.
- **Idempotent** (FR-09.8): the unique `(wallet_id, currency_id, scheduled_expiry_date)` constraint + status guard ensures a re-run never double-debits.

**Tenant isolation contract:** request-path services resolve `clientId` from `TenantContext.getCurrentClientId()` and never accept it from the API layer. The batch service runs outside request context: it resolves `clientId` per wallet/policy row and binds it explicitly (no ambient `TenantContext`).

---

## Workflow / Status Transitions [BE + FE]

**Policy** — `enabled` toggle (no separate status enum):
```
DISABLED → ENABLED  (action: upsertPolicy with enabled=true; sets enabled_at = now)
ENABLED  → DISABLED (action: upsertPolicy with enabled=false; cancels pending notices)
ENABLED  → ENABLED  (relax: lengthen inactivity / push fixed date / lengthen lead; resets enabled_at, cancels now-invalid pending notices, re-notifies)
```

**BalanceExpiryNotice** (`ExpiryNoticeStatus`):
```
SCHEDULED → NOTIFIED  (action: batch warn phase, trigger: balance enters lead window; sends BALANCE_EXPIRING_SOON)
NOTIFIED  → EXPIRED   (action: batch expire phase, trigger: scheduled_expiry_date reached; writes EXPIRY ledger debit, sends BALANCE_EXPIRED)
SCHEDULED → CANCELLED (action: policy disable/relax, trigger: CLIENT_ADMIN; no notice was sent yet)
NOTIFIED  → CANCELLED (action: policy disable/relax, trigger: CLIENT_ADMIN; sends BALANCE_EXPIRY_CANCELLED to already-notified partners — FR-09.10)
```

**Invalid transitions** (guarded; batch skips, no exception surfaced to users):
- `EXPIRED → *` — terminal; an expired event is immutable (the debit is on the ledger).
- `CANCELLED → *` — terminal; a new `SCHEDULED` notice is created if the policy is re-enabled later.

**Who can trigger:** policy transitions — `CLIENT_ADMIN` only. Notice transitions — the **SYSTEM** batch (warn/expire) and the policy-update side-effect (cancel).

**Concurrent transition handling:** wallet debit at expiry takes a row lock and uses `@Version`; a concurrent redemption reserving balance between warn and expire is respected (expiry only takes remaining `availableBalance`). Policy upsert uses `@Version` → concurrent admin edits get `409`.

---

## Security Design [BE]

### Data Classification

| Field / Dataset | Classification | Handling |
|---|---|---|
| Wallet `availableBalance`, `LedgerEntry` amounts | Confidential (financial) | Tenant-scoped; never cross-tenant; expiry debits tagged `EXPIRY` for auditability |
| `BalanceExpirationPolicy` config | Internal | CLIENT_ADMIN-only; not exposed to partner roles |
| Breakage report / CSV | Internal, **aggregate-only** | No per-user identity, no PII — counts + summed amounts per currency + period only |
| `BalanceExpiryNotice.wallet_id` | Internal (opaque UUID) | Never surfaced in the aggregate preview/report responses |

### Rate Limiting

_Reuses `AnalyticsExportRateLimiter` (`security/AnalyticsExportRateLimiter.java`) — the per-tenant export limiter from F-07/F-08, keyed by `clientId`, `tryAcquireWithRetryAfter(clientId)` → `RateLimitResult` with `Retry-After`. Not `RateLimitFilter` (that is an in-memory per-IP+path fixed-window limiter with hardcoded paths, unsuitable here). Note: in-memory limiters are not multi-instance-safe — acceptable for Phase-1 admin volume; a Redis-backed counter is the documented production upgrade path._

| Endpoint / Operation | Limit | Scope | Reason |
|---|---|---|---|
| `GET /breakage/export` (CSV) | 3 req / 60s | Per tenant (`clientId`) | Aggregation + CSV build is expensive; reuses `AnalyticsExportRateLimiter.tryAcquireWithRetryAfter(clientId)` (same bucket policy as the F-08 liability export) |
| `PUT /policies/{currencyId}` | — (not rate-limited) | — | Config thrash is bounded by optimistic locking (`@Version`); no existing per-tenant limiter enforces a config-write cap and admin volume is low, so a dedicated limit is deferred |

### OWASP Risks & Mitigations

| Risk | Where | Mitigation |
|---|---|---|
| **Broken Access Control (A01)** | All endpoints | `@RequiresPermission` (CLIENT_ADMIN-only keys); request services resolve `clientId` from JWT via `TenantContext`. The cross-tenant batch uses a dedicated non-`@Filter` repository and binds `client_id` explicitly per wallet — it never relies on ambient request context. |
| **Injection / CSV formula injection (A03, CWE-1236)** | `/breakage/export` | Every string cell routed through `escapeCsv()` (reused from `RedemptionAnalyticsService`); neutralises leading `= + - @`. Filter params are enum/typed, not free text. |
| **Financial integrity / double-spend** | Expiry batch vs concurrent redemption | Row lock on `RewardWallet` + `@Version`; only live `availableBalance` expired; reserved balance untouched; idempotent unique notice key prevents double-debit on retry (FR-09.8/09.11). |
| **Insecure Design — silent value loss** | Expiry without notice | Grace window + mandatory `NOTIFIED` precondition before `EXPIRED` (FR-09.7); no retroactive expiry. |
| **Mass Assignment** | `PUT /policies/{currencyId}` | Explicit record DTO; only declared fields bound. |

### Input Validation Summary

| Field | Constraints | Rejection |
|---|---|---|
| `enabled`, `expirationMode`, `leadTimeDays` | `@NotNull` (structural) | 400 field-level |
| `inactivityDays` / `fixedExpiryDate` / `leadTimeDays` relations | Service-layer bounds + cross-field (FR-09.9) | **422** with `errorCode` |
| `currencyId` (path) | Recognised currency type | 422 — unknown currency |
| `from` / `to` (breakage) | `to ≥ from`, range ≤ 24 months | 400 |
| `granularity` | enum `MONTH`\|`QUARTER` | 400 — unknown value |

---

## Audit Trail [BE]

_Path: `src/main/java/com/tenxengage/app/audit/` (existing `@Audited` infrastructure)._

| Operation | Entity | Data Captured | Who Can View |
|---|---|---|---|
| CREATE / UPDATE policy | `BalanceExpirationPolicy` | Changed fields (enabled, mode, days, date, lead time), `updatedBy`, source IP | `CLIENT_ADMIN` |
| Disable / relax → cancel pending | `BalanceExpirationPolicy` | Currency, count of notices cancelled, `updatedBy` | `CLIENT_ADMIN` |
| Expiry execution | `RewardWallet` | `walletId`, `currencyId`, `expiredAmount`, `ledgerEntryId`, `scheduledExpiryDate`, **actor = SYSTEM** | `CLIENT_ADMIN` |
| Breakage CSV export | breakage export | `from`, `to`, `currencyId`, `granularity`, `exportedBy` | `CLIENT_ADMIN` |

### New Audit Enum Values

| Enum | New Value | Reason |
|---|---|---|
| `AuditAction` | _None_ | `CREATED`, `EDITED`, `EXPIRED`, `CANCELLED`, `DATA_EXPORTED` all already exist |
| `AuditResourceType` | `BALANCE_EXPIRATION_POLICY` | Policy create/update/disable audits |
| `AuditResourceType` | `BALANCE_EXPIRY_BREAKAGE_EXPORT` | CSV export audit (mirrors `REDEMPTION_ADVANCED_ANALYTICS_EXPORT`) |

_Java enums stored as `varchar(50)` — no Flyway migration; per-wallet expiry execution reuses existing `REWARD_WALLET`._

### `@Audited` Annotation Details (Non-CRUD Only)

| Endpoint | `action` | `resourceType` | `description` |
|---|---|---|---|
| `PUT /policies/{currencyId}` (enable) | `Edited` | `BALANCE_EXPIRATION_POLICY` | `Configured balance expiration policy` |
| `GET /breakage/export` | `DATA_EXPORTED` | `BALANCE_EXPIRY_BREAKAGE_EXPORT` | `Exported balance expiration breakage report` |
| Expiry execution (batch, programmatic) | `EXPIRED` | `REWARD_WALLET` | `Expired unused balance` (logged via `auditLogService.logAsync` with SYSTEM actor) |

**Audit record retention:** 7 years. Append-only; never soft-deleted.

---

## Observability [BE]

### MDC Fields

| MDC Key | Value | Set By |
|---|---|---|
| `requestId` | UUID from `X-Request-ID` | `RequestContextFilter` (existing) |
| `tenantId` | `clientId` (request path) / per-wallet `clientId` (batch) | `TenantFilter` / set explicitly in batch loop |
| `userId` | User ID from JWT (request path); `SYSTEM` in batch | `JwtAuthenticationFilter` / batch |
| `featureArea` | `"balance-expiration"` | `BalanceExpirationPolicyService` / batch service |

### Key Log Events

| Event | Level | `step` value | Key Fields | Purpose |
|---|---|---|---|---|
| Policy updated | INFO | `balance_expiration_policy_updated` | `currencyId`, `enabled`, `expirationMode` | Config change tracking |
| Pending cancelled on relax | INFO | `balance_expiry_cancelled` | `currencyId`, `cancelledCount` | FR-09.10 audit trail |
| Batch started | INFO | `balance_expiry_batch_started` | `enabledPolicyCount` | Batch health |
| Advance notice sent | INFO | `balance_expiry_warned` | `walletId`, `currencyId`, `scheduledExpiryDate`, `amount` | FR-09.4 once-only verification |
| Balance expired | INFO | `balance_expired` | `walletId`, `currencyId`, `expiredAmount`, `ledgerEntryId` | Business event |
| Idempotent skip | DEBUG | `balance_expiry_idempotent_skip` | `walletId`, `currencyId`, `scheduledExpiryDate` | Confirms FR-09.8 on retry |
| Batch finished | INFO | `balance_expiry_batch_finished` | `warnedCount`, `expiredCount`, `durationMs` | Batch SLA |

### Metrics

| Metric Name | Type | Labels | Purpose |
|---|---|---|---|
| `balance_expiry.warned.total` | Counter | `currencyId` | Advance-notice volume |
| `balance_expiry.executed.total` | Counter | `currencyId`, `walletType` | Expiry volume |
| `balance_expiry.amount.total` | Counter | `currencyId` | Breakage value tracking |
| `balance_expiry.cancelled.total` | Counter | `currencyId` | Relax/disable cancellations |
| `balance_expiry.batch.duration_ms` | Histogram | — | Batch latency (alert if exceeds window) |

**Sensitive data in logs:** log wallet/ledger UUIDs and amounts only — never user PII.

---

## Domain Events [BE]

Balance Expiration does **not** introduce a new domain topic. It publishes via the existing **`NotificationEventProducer`** to the `notification-events` topic under `NotificationCategory.REWARDS` (per BRD integration intent). Events are emitted **after commit** (`TransactionSynchronizationManager.afterCommit`), so a notification is never sent for a rolled-back expiry. The `kafkaTemplate.send()` future must be handled with `.whenComplete(...)` for failure logging — **`NotificationEventProducer.publish()` must be hardened to add this** as part of this feature (it currently only catches `JsonProcessingException` and does not observe the send future), per the backend rule to always handle the returned `CompletableFuture`.

### Notification Types (new seed rows in `notification_types`)

| `key` | `category` | When | `default_roles` (recipients) | Payload (no PII) |
|---|---|---|---|---|
| `BALANCE_EXPIRING_SOON` | `REWARDS` | Advance notice at `lead_time_days` before expiry (FR-09.4) | `PARTNER_SELLER` (individual wallet) / `PARTNER_ADMIN` (company wallet) | `currencyId`, `amount`, `scheduledExpiryDate`, `walletId` |
| `BALANCE_EXPIRED` | `REWARDS` | At expiry, after the `EXPIRY` ledger debit commits (FR-09.5) | same | `currencyId`, `expiredAmount`, `expiredAt`, `walletId` |
| `BALANCE_EXPIRY_CANCELLED` | `REWARDS` | When a policy disable/relax cancels an already-notified expiry (FR-09.10) | same | `currencyId`, `scheduledExpiryDate`, `walletId` |

**Idempotency / once-only:** advance notice is sent only when `BalanceExpiryNotice.notified_at IS NULL`; the marker is set in the same transaction, so batch retries never double-notify (FR-09.4 + FR-09.8). Recipient resolution: individual wallet → the owning `user_id`; company wallet → the company's `PARTNER_ADMIN`(s) via `partner_company_id`.

---

## Frontend Specification [FE]

_TypeScript types live in `../tenxengage-contracts/`. Full FE file paths and hook specs: see `technical.md`._

### Pages

| Page | Route | Layout | Permission | Sidebar Entry |
|---|---|---|---|---|
| `BalanceExpirationSettingsPage` | `/settings/redemption/balance-expiration` | `ClientAdminLayout` | `action.redemption.expiration.configure` | Yes — under "Redemption Settings" |
| `BalanceBreakageReportPage` | `/redemption/breakage` | `ClientAdminLayout` | `action.redemption.expiration.view_breakage` | Yes — under "Redemption" |

### Key Components

| Component | Props | Data Source | Notes |
|---|---|---|---|
| `BalanceExpirationPolicyForm` | `currencyId`, `policy` | `useBalanceExpirationPolicies()` | Composite — see completeness rule. **Sections:** (a) **Currency header** — `currencyDisplayName`, enabled toggle; (b) **Mode** — radio `INACTIVITY` \| `FIXED_DATE`; (c) **Mode params** — `inactivityDays` (shown for INACTIVITY) or `fixedExpiryDate` date-picker (shown for FIXED_DATE); (d) **Notice** — `leadTimeDays`; (e) **Status caption** — `enabledAt`/`updatedAt`. **Interactions:** toggle enable; switch mode (re-renders param field); inline 422 errorCode mapping under the offending field; Save (disabled until dirty + valid). Keyboard: standard form tab order; radio arrow-key selection. **A11y & responsive:** per `../tenxengage-frontend/PROJECT-CONTEXT.md`. |
| `ExpiringSoonPreviewCard` | `withinDays`, `currencyId?` | `useExpiringSoon()` | Composite — **Sections:** per-currency rows showing `currencyDisplayName`, `scheduledExpiryDate`, `affectedWalletCount`, `totalAmountAtRisk`. Read-only impact preview shown beside the policy form. **Interactions:** currency filter; refresh on policy save. **A11y & responsive:** per PROJECT-CONTEXT. |
| `BreakageReportTable` | `from`, `to`, `currencyId?`, `granularity` | `useBalanceBreakage()` | Composite table — **Columns:** `periodStart`, `periodEnd`, `currencyDisplayName`, `expiredCount`, `totalExpiredAmount` (currency-formatted). **Sections:** filter bar (date range, currency, granularity) + table + Export CSV button. **Interactions:** apply filters (refetch); column sort (client-side allowlist); Export triggers `GET /breakage/export` download; 429 → toast. **A11y & responsive:** per PROJECT-CONTEXT; table scrolls horizontally in its own container on narrow viewports. |

### Forms

| Form | Fields | Validation | Submit Action |
|---|---|---|---|
| `BalanceExpirationPolicyForm` | `enabled`, `expirationMode`, `inactivityDays`, `fixedExpiryDate`, `leadTimeDays` | `balanceExpirationPolicySchema` (zod) — mirrors service rules (mode-conditional required fields, `leadTimeDays ≥ 1 < inactivityDays`, future date); server `422 errorCode` mapped to field errors | `PUT /api/v1/redemption/expiration/policies/{currencyId}` |

### Data Flow (TanStack Query)

| Hook | Query Key | Endpoint | StaleTime | Invalidation |
|---|---|---|---|---|
| `useBalanceExpirationPolicies()` | `['balance-expiration-policies', clientId]` | `GET /redemption/expiration/policies` | 5 min | On policy upsert |
| `useExpiringSoon(params)` | `['balance-expiring-soon', clientId, params]` | `GET /redemption/expiration/expiring-soon` | 1 min | On policy upsert |
| `useBalanceBreakage(params)` | `['balance-breakage', clientId, params]` | `GET /redemption/expiration/breakage` | 5 min | Manual (filter change) |

---

## Caching Strategy [BE]

No server-side caching. Policy reads are low-volume and expiry correctness depends on the freshest policy state (a stale cached policy could mis-time an irreversible expiry debit). Breakage aggregates change as the batch runs and must reflect committed ledger state. TanStack Query handles client-side caching (stale times above).

---

## Data Retention & Compliance [BE]

### Soft Delete vs Hard Delete

**Decision: Soft delete** (`deleted` flag) on both `BalanceExpirationPolicy` and `BalanceExpiryNotice`.
- **Why:** notices form the evidentiary trail that an advance notice was delivered before an irreversible expiry debit (FR-09.7); they are retained, never hard-deleted. Policies are soft-deleted so historical breakage retains its governing-policy reference.
- The `EXPIRY` `LedgerEntry` is **immutable and append-only** — never deleted (consistent with the ledger engine).

### PII Handling

This feature stores **no PII**. Entities hold currency codes, amounts, dates, and opaque UUID references (`wallet_id`, `client_id`, `policy_id`, `ledger_entry_id`). The breakage report and expiring-soon preview are aggregate-only. Notification *delivery* (recipient resolution) reads existing user/company records but stores no PII in F-09 tables.

| Field | Entity | PII Type | GDPR Treatment |
|---|---|---|---|
| _(none)_ | — | — | — |

### Data Retention Periods

| Data Type | Retention Period | Justification |
|---|---|---|
| `BalanceExpirationPolicy` (soft-deleted) | 7 years | Governance/audit of expiry policy history |
| `BalanceExpiryNotice` | 7 years | Proof of advance notice before expiry (FR-09.7) |
| `EXPIRY` ledger entries | Permanent (ledger immutability) | Financial record; breakage source of truth |

---

## Configurable Dimensions [BE]

| Dimension | Storage | Default | Notes |
|---|---|---|---|
| Expiration enabled | `BalanceExpirationPolicy.enabled` (per client × currency) | `false` | FR-09.2/09.3 |
| Expiration mode | `BalanceExpirationPolicy.expiration_mode` | — (required) | `INACTIVITY` \| `FIXED_DATE` |
| Inactivity window | `BalanceExpirationPolicy.inactivity_days` | — | Bounds `[30, 1825]` days |
| Fixed expiry date | `BalanceExpirationPolicy.fixed_expiry_date` | — | Must be future at config time |
| Advance-notice lead time | `BalanceExpirationPolicy.lead_time_days` | `30` | ADR #2; `≥ 1` and `< inactivity_days` |
| Inactivity bounds (`MIN`/`MAX_INACTIVITY_DAYS`) | Hardcoded constants | `30` / `1825` | Platform guardrails, not tenant-configurable |

---

## Edge Cases [BE + FE]

1. **Cash, no policy** — cash never expires; the batch skips currencies with no enabled policy (FR-09.2).
2. **Newly enabled policy** — grace window: nothing expires until ≥ one full `lead_time_days` elapses since `enabled_at` (ADR #4, FR-09.7). No retroactive expiry.
3. **Reserved balance** — only `availableBalance` is expired; a wallet fully reserved expires `0` (ADR #3).
4. **Concurrent redemption during expiry** — row lock ensures expiry takes only live `availableBalance`; a reservation that lands first reduces the expired amount; no double-spend (FR-09.11).
5. **Batch retry / re-run** — unique `(wallet_id, currency_id, scheduled_expiry_date)` + status guard → no second debit (FR-09.8); logged as `balance_expiry_idempotent_skip`.
6. **Disable mid-flight** — `SCHEDULED`/`NOTIFIED` notices → `CANCELLED`; already-notified partners get `BALANCE_EXPIRY_CANCELLED` (FR-09.10).
7. **Relax policy** (lengthen window / push date) — recomputes; now-invalid pending notices cancelled + re-notified; `enabled_at` reset (restarts grace window).
8. **Zero / negative-after-reserve balance** — no notice and no expiry created for non-positive `availableBalance`.
9. **Fixed date in the past at config time** — `422` (FR-09.9); cannot configure a retroactive fixed expiry.
10. **`leadTimeDays ≥ inactivityDays`** — `422` (advance notice must precede expiry).
11. **Breakage range invalid** — `to < from` or > 24 months → `400`.
12. **CSV export rate limit** — `429`; FE shows "You're exporting too frequently. Please wait a moment."
13. **Tenant context missing in batch** — batch binds `client_id` per wallet explicitly; a wallet with an unresolvable client is skipped and logged at ERROR (`step=tenant_isolation_violation`), never expired under the wrong tenant.
14. **Notification delivery failure** — advance notice send failure (via `.whenComplete`) does NOT mark `notified_at`; the next batch retries the warn so a partner is never expired without a delivered notice (FR-09.7).

---

## Acceptance Tests

_Tests are split across two locations:_
- **Per-story tests** (unit, @WebMvcTest, Vitest, E2E Playwright) — inside each `stories/US-NN-*.md` file.
- **Cross-story integration tests** (Testcontainers full lifecycle, idempotency, tenant isolation, audit/events) — in [test-plan.md](test-plan.md).

---

## Modified Existing Endpoints [BE + FE]

_None. Balance Expiration is additive: it introduces a new `LedgerEntryType.EXPIRY` value and reads existing wallet/ledger data, but changes no existing endpoint contract. Existing wallet/ledger reads naturally include `EXPIRY` entries once present (additive, non-breaking)._

---

## Out of Scope

- **Lot-level / FIFO expiry** — whole available balance expires per currency (review default D); no per-tranche aging.
- **Cash auto-expiry by default** — cash only expires if a Client Admin explicitly enables a cash policy.
- **Cross-currency** expiry rules or conversions.
- **Per-user breakage drill-down / PII export** — v1 breakage is aggregate-only (counts + amounts by currency/period).
- **Real-time / on-demand expiry** — expiry is a scheduled off-peak batch only.
- **Reserved/in-flight balance expiry** — never expires.
- **Reopening F-08 redemption analytics** — breakage is an F-09-owned report/endpoint (review default C); F-08 may link to it later.

---

## Verification Steps

### Backend Verification
1. `./gradlew bootRun` (local profile) — app starts; Flyway **V32** (tables) and **V33** (permissions + flag) apply cleanly.
2. `./gradlew test` — new + existing tests pass.
3. Config: `PUT /policies/cash` with `enabled=true, mode=INACTIVITY, inactivityDays=90, leadTimeDays=30` → `200`; invalid (`leadTimeDays=120`) → `422` with `errorCode`.
4. Lifecycle (Testcontainers): enable → grace → warn (`BALANCE_EXPIRING_SOON`, `notified_at` set once) → expire (`EXPIRY` ledger debit, `availableBalance` reduced, `BALANCE_EXPIRED`); re-run batch → no double-debit.
5. Security: cross-tenant breakage isolation; `view_breakage`-less role → `403`; CSV cells with leading `=` are escaped.
6. Observability: tail logs on a batch run; verify `step=balance_expired`, `tenantId`, `walletId`, `expiredAmount`.

### Frontend Verification
1. `npm run build` — no TypeScript errors.
2. `npm run test` (Vitest) + `npx playwright test` (E2E) pass.
3. UI: policy form mode-switch shows correct param field; 422 maps to field error; breakage table renders + CSV export downloads; empty state when no expirations.
