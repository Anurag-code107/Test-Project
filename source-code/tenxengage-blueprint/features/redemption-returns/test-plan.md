# Test Plan: redemption-returns

_Cross-story integration tests for [spec.md](spec.md)._

_**Per-story tests** (unit tests, @WebMvcTest, Vitest, Playwright E2E) live inside each `stories/US-NN-*.md` alongside the code they verify. This file covers only tests that **span multiple stories** or require the full system to be running._

_Uses `extends AbstractLocalIntegrationTest` (Testcontainers PostgreSQL 16 + Kafka)._
_Path: `src/test/java/com/tenxengage/app/integration/`_

---

## Lifecycle & CRUD

_Full persistence round-trip through a real DB._

| Test Class | Scenario | Expected | Depends on Stories |
|---|---|---|---|
| `RedemptionReturnLifecycleIT` | Full return lifecycle: submit → approve → webhook confirm | Return created in `PENDING_APPROVAL`; transitions to `APPROVED` after approve; transitions to `RETURN_CONFIRMED` and wallet credited after confirm webhook | US-01, US-02, US-03 |
| `RedemptionReturnLifecycleIT` | Flyway V25 and V26 apply cleanly | `redemption_returns` table with all 22 columns + 5 indexes; `return_status` enum created; both permissions seeded; feature flag seeded | Foundation |

---

## Entity Relationships & Cascades

_Tests FK and relationship behavior against real DB._

| Test Class | Scenario | Expected | Depends on Stories |
|---|---|---|---|
| `RedemptionReturnLifecycleIT` | Submit return referencing a non-existent `redemptionId` | FK constraint violation → 422 (or 404 if not found lookup is done at service layer before FK check) | US-01 |
| `RedemptionReturnLifecycleIT` | Submit return referencing a `redemptionId` from a different tenant | Service-level check: `redemption.clientId ≠ jwt.clientId` → 422 eligibility failure | US-01 |

---

## State Machine Transitions

_Valid and invalid transitions through a real DB._

| Test Class | Scenario | Expected | Depends on Stories |
|---|---|---|---|
| `RedemptionReturnLifecycleIT` | Valid: `PENDING_APPROVAL → APPROVED` | Status updated; `approvedAt` set; `reviewedBy` set; audit record created | US-01, US-02 |
| `RedemptionReturnLifecycleIT` | Valid: `APPROVED → RETURN_CONFIRMED` (webhook) | Status updated; `confirmedAt` set; `RETURN_CREDIT` ledger entry written; wallet available balance increased | US-02, US-03 |
| `RedemptionReturnLifecycleIT` | Valid: `APPROVED → RETURN_REJECTED` (webhook) | Status updated; `rejectedAt` set; wallet balance unchanged | US-02, US-03 |
| `RedemptionReturnLifecycleIT` | Valid: `PENDING_APPROVAL → CANCELLED` | Status updated; `cancelledAt` set; resubmission allowed for same redemption | US-01 |
| `RedemptionReturnLifecycleIT` | Valid: `APPROVED → RETURN_TIMED_OUT` (scheduler) | Status updated; `timedOutAt` set; `RETURN_TIMED_OUT` Kafka event published | Foundation (F5) |
| `RedemptionReturnLifecycleIT` | Valid: `RETURN_TIMED_OUT → RETURN_CONFIRMED` (manual resolve) | Status updated; `doReturnCreditInTx()` called; wallet credited | US-04 |
| `RedemptionReturnLifecycleIT` | Valid: `RETURN_TIMED_OUT → RETURN_REJECTED` (manual resolve) | Status updated; no wallet credit | US-04 |
| `RedemptionReturnLifecycleIT` | Invalid: `RETURN_CONFIRMED → APPROVED` | `409` — terminal state, no rollback | US-01, US-02, US-03 |
| `RedemptionReturnLifecycleIT` | Invalid: `RETURN_REJECTED → PENDING_APPROVAL` | `409` — terminal state | US-01, US-02 |
| `RedemptionReturnLifecycleIT` | Full lifecycle: all states traversed in one test | Each transition succeeds; entity state correct after each step; audit trail has one row per transition | US-01–US-04 |

---

## Business Rule Enforcement

_Business rules from spec Service Layer and edge cases using real DB._

| Test Class | Scenario | Expected | Depends on Stories |
|---|---|---|---|
| `RedemptionReturnSubmitIT` | Submit for a XTRM (cash) redemption | `422` with message "Cash redemptions cannot be returned" | US-01 |
| `RedemptionReturnSubmitIT` | Submit for a non-COMPLETED redemption | `422` with message indicating non-COMPLETED status | US-01 |
| `RedemptionReturnSubmitIT` | Submit for a catalog item with `isReturnable = false` | `422` with message "This item is not eligible for return" | US-01 |
| `RedemptionReturnSubmitIT` | Submit outside the client-configured return window | `422` with message "Return window for this redemption has expired" | US-01 |
| `RedemptionReturnSubmitIT` | Submit when active return exists (`PENDING_APPROVAL`) | `409` — duplicate active return | US-01 |
| `RedemptionReturnSubmitIT` | Submit when active return exists (`APPROVED`) | `409` — duplicate active return | US-01, US-02 |
| `RedemptionReturnSubmitIT` | Submit after prior return is `CANCELLED` | `201` — resubmission allowed after cancellation | US-01 |
| `RedemptionReturnSubmitIT` | Amount in request body is ignored; amount equals original redemption amount | `ReturnDetailResponse.amount` equals `RedemptionRequest.amount` regardless of submitted value | US-01 |
| `RedemptionReturnLifecycleIT` | Reject with blank `rejectionReason` | `400` field-level validation error | US-02 |
| `RedemptionReturnLifecycleIT` | Resolve non-TIMED_OUT return | `409` — wrong state for resolve | US-04 |

---

## Multi-Entity Workflows

_End-to-end workflows spanning multiple entities._

| Test Class | Scenario | Expected | Depends on Stories |
|---|---|---|---|
| `RedemptionReturnLifecycleIT` | Submit return → Admin approves → Xoxoday webhook confirms → wallet balance restored | Full chain: `PENDING_APPROVAL → APPROVED → RETURN_CONFIRMED`; ledger entry `RETURN_CREDIT` written; `RedemptionRequest` is linked; `RedemptionReturn.amount == RedemptionRequest.amount` | US-01, US-02, US-03 |
| `RedemptionReturnLifecycleIT` | Submit return → Admin approves → scheduler fires at T+7d → Admin resolves to CONFIRM | Full chain including RETURN_TIMED_OUT; wallet credited at resolve time, not at approval | US-01, US-02, F5, US-04 |
| `RedemptionReturnIdempotencyIT` | Webhook arrives after admin manual resolve (CONFIRM) | Second doReturnCreditInTx call is no-op; wallet not double-credited; webhook returns 200 | US-03, US-04 |

---

## Contract Conformance

_Response body shape verified against the generated OpenAPI contract._

_Uses RestAssured or MockMvc + OpenAPI validator wired to `../tenxengage-contracts/endpoints/redemption-returns.yaml`._

| Test Class | Scenario | Expected | Depends on Stories |
|---|---|---|---|
| `RedemptionReturnContractIT` | `GET /api/v1/redemption/returns` response shape | Body matches `Page<ReturnSummaryResponse>` contract; `200`; all required fields present with correct types | US-01 |
| `RedemptionReturnContractIT` | `POST /api/v1/redemption/returns` response shape | `201` body matches `ReturnDetailResponse` contract schema | US-01 |
| `RedemptionReturnContractIT` | `POST /api/v1/redemption/returns` validation error shape | `400` body matches `ValidationErrorResponse` contract; `errors[]` field shape correct | US-01 |
| `RedemptionReturnContractIT` | `GET /api/v1/redemption/returns/{id}` not-found shape | `404` body matches `ErrorResponse` contract schema | US-01 |
| `RedemptionReturnContractIT` | `POST /api/v1/redemption/admin/returns/{id}/approve` state-violation shape | `409` body matches `BusinessRuleViolationResponse` contract; `code` field present | US-02 |
| `RedemptionReturnContractIT` | `GET /api/v1/redemption/admin/returns` response shape | Body matches `Page<ReturnQueueItemResponse>` contract; `200` | US-02 |
| `RedemptionReturnContractIT` | `POST /api/v1/redemption/admin/returns/{id}/reject` validation error shape | `400` body matches `ValidationErrorResponse`; `rejectionReason` field in errors | US-02 |
| `RedemptionReturnContractIT` | `POST /webhooks/redemption-returns/xoxoday` HMAC failure shape | `403` response body matches `ErrorResponse` contract | US-03 |

---

## Tenant Isolation & Security

_Cross-story security boundaries requiring multiple stories or full stack._

| Test Class | Scenario | Expected | Depends on Stories |
|---|---|---|---|
| `RedemptionReturnPermissionsIT` | Unauthenticated `GET /api/v1/redemption/returns` | `401` with no body leakage; not `403` or `500` | US-01 |
| `RedemptionReturnPermissionsIT` | Unauthenticated `POST /api/v1/redemption/returns` | `401` | US-01 |
| `RedemptionReturnPermissionsIT` | Unauthenticated `POST /api/v1/redemption/admin/returns/{id}/approve` | `401` | US-02 |
| `RedemptionReturnTenantIsolationIT` | Tenant A submits return; Tenant B calls `GET /returns/{id}` | `404` — not `403`; prevents tenant enumeration | US-01 |
| `RedemptionReturnTenantIsolationIT` | Tenant A submits return; Tenant B calls `DELETE /returns/{id}` | `404` — IDOR on cancel blocked | US-01 |
| `RedemptionReturnTenantIsolationIT` | Tenant A submits return; Tenant B admin calls `POST /admin/returns/{id}/approve` | `404` — cross-tenant admin action blocked | US-01, US-02 |
| `RedemptionReturnPermissionsIT` | Concurrent approve + cancel on same return (`@Version` conflict) | Second writer receives `409` "updated concurrently" | US-01, US-02 |
| `RedemptionReturnPermissionsIT` | `PARTNER_SELLER` can call `POST /returns`; `CLIENT_ADMIN` cannot | `PARTNER_SELLER` → `201`; `CLIENT_ADMIN` → `403` | US-01 |
| `RedemptionReturnPermissionsIT` | `CLIENT_ADMIN` can call `POST /admin/returns/{id}/approve`; `PARTNER_SELLER` cannot | `CLIENT_ADMIN` → `200`; `PARTNER_SELLER` → `403` | US-02 |
| `RedemptionReturnPermissionsIT` | `PARTNER_SELLER` calls `GET /admin/returns` | `403` | US-02 |
| `RedemptionReturnPermissionsIT` | `action.redemption.non_cash_returns` feature flag disabled (starter tier) | `POST /returns` returns `403` | US-01 |

---

## Audit & Events

_Audit trail and Kafka event consumer round-trips._

| Test Class | Scenario | Expected | Depends on Stories |
|---|---|---|---|
| `RedemptionReturnLifecycleIT` | Audit record on submit | `audit_log` has record with `action=SUBMITTED`, `resourceType=REDEMPTION_RETURN`, correct `actorId` | US-01 |
| `RedemptionReturnLifecycleIT` | Failed submit (422 ineligible) → no audit row | `audit_log` count unchanged; failed operations must not produce audit trail | US-01 |
| `RedemptionReturnLifecycleIT` | Audit record on approve | `action=APPROVED`, `oldStatus=PENDING_APPROVAL`, `newStatus=APPROVED`, `reviewedBy` set | US-02 |
| `RedemptionReturnLifecycleIT` | Audit record on reject | `action=REJECTED`, `oldStatus=PENDING_APPROVAL`, `newStatus=RETURN_REJECTED`, `rejectionReason` captured | US-02 |
| `RedemptionReturnLifecycleIT` | Audit record on cancel | `action=CANCELLED`, `oldStatus=PENDING_APPROVAL`, `newStatus=CANCELLED` | US-01 |
| `RedemptionReturnLifecycleIT` | Kafka `RETURN_REQUESTED` event on submit | Correct event on `return-events` with expected base payload; no PII in payload | US-01, Foundation F5 |
| `RedemptionReturnLifecycleIT` | Kafka `RETURN_CONFIRMED` event on webhook confirm | Correct event; `vendorReturnReference` in payload | US-03 |
| `RedemptionReturnLifecycleIT` | Kafka `RETURN_TIMED_OUT` event on scheduler fire | Correct event; `timedOutAt` timestamp in payload | Foundation F5 |

---

## Query Correctness at Scale

_Cross-story queries against real data volumes._

| Test Class | Scenario | Expected | Depends on Stories |
|---|---|---|---|
| `RedemptionReturnQueryIT` | Admin list: 75 returns, page 2 size 25 | Returns page 2 with exactly 25 rows; `totalElements = 75`; correct ordering by `createdAt DESC` | US-02 |
| `RedemptionReturnQueryIT` | Admin list: status filter `PENDING_APPROVAL` with mixed statuses | Result set contains only `PENDING_APPROVAL` rows; no leakage of other statuses | US-02 |
| `RedemptionReturnQueryIT` | Partner list: 2 tenants, each with 10 returns | Partner from Tenant A sees only their own 10 returns; Tenant B's returns absent | US-01 |
| `RedemptionReturnQueryIT` | Pagination edge: `page=999` with 2 pages of data | `200` with `content: []`; `totalElements` correct | US-01 |
| `RedemptionReturnQueryIT` | `sort=amount` ascending | Returns ordered by `amount ASC`; correct | US-01 |

---

## E2E Cross-Story Scenarios (Real Stack)

_Playwright against real running backend. No `page.route()` mocking._

_Setup: `beforeAll` creates state via real API calls with test-tenant JWT. `afterAll` does not clean up._

| Spec File | Scenario | Expected | Depends on Stories |
|---|---|---|---|
| `e2e/redemption-returns/full-happy-path.spec.ts` | Partner submits return from F-05 history → Admin approves in F-04 queue → Xoxoday webhook confirms → Partner sees RETURN_CONFIRMED in My Returns tab; wallet balance restored | Each step renders correct UI against real API; final status badge is green RETURN_CONFIRMED | US-01, US-02, US-03 |
| `e2e/redemption-returns/cross-tenant-isolation.spec.ts` | Partner A submits return; Partner B (different tenant) tries to access the same return URL directly | Partner B's returns list shows zero matches; direct `GET /returns/{id}` returns 404 page | US-01 |

---

## Cross-Cutting Checks

| Test Class | Scenario | Expected | Depends on Stories |
|---|---|---|---|
| `CrossCuttingIT` | Soft delete: `RedemptionReturn` with `deleted=true` not visible via `GET /returns` | `@SQLRestriction` filters deleted returns; `GET /returns/{id}` for deleted return returns 404 | US-01 |
| `CrossCuttingIT` | Optimistic locking: stale `version` on concurrent `POST /approve` | `409` with concurrency conflict body | US-02 |
| `CrossCuttingIT` | PII in logs: POST /returns with `reason` containing personal text | Log output at INFO level does NOT contain `reason` value; `step=return_submitted` present | US-01 |
| `CrossCuttingIT` | XSS in `reason` field: `<script>alert(1)</script>` submitted as reason | Stored sanitized via Jsoup `Safelist.basic()`; not reflected as script in `GET /returns/{id}` response | US-01 |
| `CrossCuttingIT` | Rate limit: > 5 submit requests within 1 minute from same tenant | 6th request returns `429` with `Retry-After` header | US-01 |
