# Test Plan: reward-balance-expiration

_Cross-story integration tests for [spec.md](spec.md)._

_**Per-story tests** (unit, @WebMvcTest, Vitest, Playwright E2E) live inside each `stories/US-NN-*.md`. This file covers only tests that **span multiple stories** or require the full system running._

_Uses `extends AbstractLocalIntegrationTest` (Testcontainers PostgreSQL 16 + Kafka)._
_Path: `src/test/java/com/tenxengage/app/integration/`_

---

## Lifecycle & CRUD

| Test Class | Scenario | Expected | Depends on Stories |
|---|---|---|---|
| `BalanceExpirationIntegrationTest` | Flyway V32/V33 apply | `balance_expiration_policies` + `balance_expiry_notices` tables, all indexes, and seeds (perms/flag/3 notification_types) present | Foundation |
| `BalanceExpirationIntegrationTest` | Policy upsert round-trip | `PUT /policies/{currencyId}` persists; `GET /policies` returns it; `enabled_at` set on enable | US-01 |
| `BalanceExpirationIntegrationTest` | Full expiry lifecycle | enable → grace passes → warn (`NOTIFIED` + `BALANCE_EXPIRING_SOON`) → expire (`EXPIRY` ledger entry, `availableBalance` reduced, `EXPIRED` + `BALANCE_EXPIRED`) | US-01, US-02, US-03 |

---

## Entity Relationships & Cascades

| Test Class | Scenario | Expected | Depends on Stories |
|---|---|---|---|
| `BalanceExpirationIntegrationTest` | Notice → policy FK | `balance_expiry_notices.policy_id` references an existing policy; orphan insert → FK violation | US-02 |
| `BalanceExpirationIntegrationTest` | Notice → ledger FK | `ledger_entry_id` on an `EXPIRED` notice references the real `EXPIRY` entry | US-03 |

---

## State Machine Transitions

| Test Class | Scenario | Expected | Depends on Stories |
|---|---|---|---|
| `BalanceExpirationIntegrationTest` | Valid: SCHEDULED → NOTIFIED → EXPIRED | each transition persists; `notified_at`/`expired_at` set; audit + notifications emitted | US-02, US-03 |
| `BalanceExpirationIntegrationTest` | Valid: NOTIFIED → CANCELLED (relax) | disable/relax cancels the notice; `BALANCE_EXPIRY_CANCELLED` emitted | US-01, US-03 |
| `BalanceExpirationIntegrationTest` | Terminal guard: EXPIRED notice re-run | a second sweep does not re-debit or re-transition an `EXPIRED` notice (idempotent) | US-03 |

---

## Business Rule Enforcement

| Test Class | Scenario | Expected | Depends on Stories |
|---|---|---|---|
| `BalanceExpirationIntegrationTest` | Config validation | lead ≥ inactivity / past fixed date / out-of-bounds inactivity → `422` with `errorCode` (FR-09.9) | US-01 |
| `BalanceExpirationIntegrationTest` | Grace window | newly enabled policy expires nothing within one `lead_time_days` of `enabled_at` (FR-09.7) | US-01, US-02 |
| `BalanceExpirationIntegrationTest` | Idempotent expiry | re-running the sweep produces no second `EXPIRY` debit (unique notice key + ledger `existsBy`) (FR-09.8) | US-03 |
| `BalanceExpirationIntegrationTest` | Reserved balance protected | wallet with `reservedBalance>0` expires only `availableBalance`; reserved untouched (ADR #3) | US-03 |
| `BalanceExpirationIntegrationTest` | Concurrent redemption vs expiry | a reservation landing between warn and expire reduces the expired amount; no double-spend; row lock holds (FR-09.11) | US-03 |
| `BalanceExpirationIntegrationTest` | Cash opt-out | cash currency with no enabled policy → never notified or expired (FR-09.2) | US-02 |
| `BalanceExpirationIntegrationTest` | Policy disabled after warn | a `NOTIFIED` notice whose policy is disabled before the expire phase is NOT expired (US-03 AC-4 flow-gap) | US-01, US-03 |

---

## Multi-Entity Workflows

| Test Class | Scenario | Expected | Depends on Stories |
|---|---|---|---|
| `BalanceExpirationIntegrationTest` | Configure → warn → expire → breakage | after a full cycle, `GET /breakage` reflects the expired amount for that currency + period | US-01, US-02, US-03, US-04 |

---

## Contract Conformance

_`MockMvc` + OpenAPI validator wired to `../tenxengage-contracts/endpoints/reward-balance-expiration.yaml`._

| Test Class | Scenario | Expected | Depends on Stories |
|---|---|---|---|
| `BalanceExpirationContractConformanceTest` | `GET /policies` response shape | matches `BalanceExpirationPolicyResponse[]`; 200; no `client_id` | US-01 |
| `BalanceExpirationContractConformanceTest` | `PUT /policies/{currencyId}` response shape | 200 matches `BalanceExpirationPolicyResponse` | US-01 |
| `BalanceExpirationContractConformanceTest` | `PUT /policies/{currencyId}` validation error (422) | body matches the business-rule/`errorCode` schema | US-01 |
| `BalanceExpirationContractConformanceTest` | `GET /breakage` bad range (400) | body matches validation-error schema | US-04 |
| `BalanceExpirationContractConformanceTest` | `GET /breakage` response shape | matches `BalanceBreakageReportResponse` (rows of `BreakageRowDto`) | US-04 |

---

## Tenant Isolation & Security

| Test Class | Scenario | Expected | Depends on Stories |
|---|---|---|---|
| `BalanceExpirationIntegrationTest` | Unauthenticated `GET /policies` | 401, no body leakage | US-01 |
| `BalanceExpirationIntegrationTest` | Unauthenticated `GET /breakage/export` | 401 | US-04 |
| `BalanceExpirationIntegrationTest` | Tenant A policy; Tenant B `GET /policies` | B sees only its own (A's policy not present) | US-01 |
| `BalanceExpirationIntegrationTest` | Tenant A; Tenant B `PUT /policies/{currencyId}` | writes only B's own row — no cross-tenant overwrite (IDOR on write blocked) | US-01 |
| `BalanceExpirationIntegrationTest` | Cross-tenant breakage | Tenant B's `GET /breakage` never includes Tenant A's expiry entries | US-04 |
| `BalanceExpirationIntegrationTest` | Batch cross-tenant sweep isolation | a multi-tenant sweep writes each `EXPIRY` debit + notice under the correct `client_id`; no wallet expired under a wrong tenant | US-02, US-03 |
| `BalanceExpirationIntegrationTest` | Permission enforcement | CLIENT_ADMIN → 200; PARTNER_SELLER/PARTNER_ADMIN/ACTIVITY_APPROVER → 403 on configure + view_breakage | US-01, US-04 |

---

## Audit & Events

| Test Class | Scenario | Expected | Depends on Stories |
|---|---|---|---|
| `BalanceExpirationIntegrationTest` | Audit after policy upsert | `audit_log` row `action=EDITED`, `resource_type=BALANCE_EXPIRATION_POLICY`, correct actor | US-01 |
| `BalanceExpirationIntegrationTest` | Failed config (422) → NO audit | `audit_log` count unchanged after a 422 | US-01 |
| `BalanceExpirationIntegrationTest` | Audit after expiry | `audit_log` row `action=EXPIRED`, `resource_type=REWARD_WALLET`, **SYSTEM actor** | US-03 |
| `BalanceExpirationIntegrationTest` | Audit after CSV export | `audit_log` row `action=DATA_EXPORTED`, `resource_type=BALANCE_EXPIRY_BREAKAGE_EXPORT` | US-04 |
| `BalanceExpirationEventIntegrationTest` | Notifications emitted | `BALANCE_EXPIRING_SOON` (warn), `BALANCE_EXPIRED` (expire), `BALANCE_EXPIRY_CANCELLED` (relax) published to `notification-events` with correct payload (no PII); once-only on retry | US-02, US-03 |

---

## Query Correctness at Scale

| Test Class | Scenario | Expected | Depends on Stories |
|---|---|---|---|
| `BalanceBreakageQueryIT` | Aggregation over volume | breakage rows grouped by period+currency match manual SQL across N months of `EXPIRY` entries | US-04 |
| `BalanceBreakageQueryIT` | Granularity MONTH vs QUARTER | `date_trunc` buckets correctly for both granularities | US-04 |
| `BalanceExpiryBatchQueryIT` | Last-activity lookup | `findLastActivityAt` returns `MAX(created_at)` over `{CREDIT,DEBIT,RESERVE,RETURN_CREDIT}` only; ignores `RELEASE`/`REVERSAL`/`EXPIRY` | US-02 |

---

## E2E Cross-Story Scenarios (Real Stack)

_Generated by `/execute-integration-tests`; run with `run-tests --real-backend`. No `page.route()` mocking._

| Spec File | Scenario | Expected | Depends on Stories |
|---|---|---|---|
| `e2e/reward-balance-expiration/configure-then-breakage.spec.ts` | Admin enables a policy; after a seeded expiry the breakage report shows the expired total | UI renders saved policy + non-empty breakage row against the real API | US-01, US-04 |
| `e2e/reward-balance-expiration/breakage-cross-tenant.spec.ts` | Tenant B admin cannot see Tenant A's breakage | B's breakage report shows zero A rows | US-04 |

---

## Cross-Cutting Checks

| Test Class | Scenario | Expected | Depends on Stories |
|---|---|---|---|
| `CrossCuttingIT` | CSV formula injection | a currency/label cell beginning with `= + - @` is neutralized via `CsvUtil.escapeCsv` in the export | US-04 |
| `CrossCuttingIT` | Export rate limit | 4th export within the window → 429 with `Retry-After` (`AnalyticsExportRateLimiter`) | US-04 |
| `CrossCuttingIT` | No PII in logs | batch warn/expire logs contain wallet/ledger UUIDs + amounts only — no user PII (assert via log appender) | US-02, US-03 |
| `CrossCuttingIT` | Notification not sent on rollback | a deliberately rolled-back expiry transaction emits no `BALANCE_EXPIRED` (afterCommit gate) | US-03 |

---

_Categories not applicable to this feature (no FE soft-delete surface, no public upload) have been omitted._
