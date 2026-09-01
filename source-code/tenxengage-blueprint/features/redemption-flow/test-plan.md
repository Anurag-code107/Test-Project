# Test Plan: redemption-flow

_Cross-story integration tests for [spec.md](spec.md)._

_**Per-story tests** (unit tests, @WebMvcTest, Vitest, Playwright E2E) live inside each `stories/US-NN-*.md` alongside the code they verify. This file covers only tests that **span multiple stories** or require the full system to be running._

_Uses `extends AbstractLocalIntegrationTest` (Testcontainers PostgreSQL 16 + Kafka)._
_Path: `src/test/java/com/tenxengage/app/integration/`_

> ⚠️ Tests marked **[BLOCKED]** cannot run until US-05, US-06, and/or US-07 are unblocked (XTRM profile + Xoxoday credentials).

---

## Lifecycle & CRUD

| Test Class | Scenario | Expected | Depends on Stories |
|---|---|---|---|
| `RedemptionRequestIntegrationTest` | Flyway V16 migration applies | `redemption_requests` + `redemption_webhook_events` tables created with correct columns, indexes, and FKs; `max_in_flight_redemptions` column added to `tenant_redemption_settings` | Foundation |
| `RedemptionRequestIntegrationTest` | Personal redemption INSTANT lifecycle | Submit → RESERVED → (mock webhook) COMPLETED; RESERVE + DEBIT ledger entries present; wallet available balance unchanged at end | US-01, US-07 [BLOCKED] |
| `RedemptionRequestIntegrationTest` | Personal redemption failure lifecycle | Submit → RESERVED → (mock webhook failure) FAILED; RESERVE + RELEASE entries cancel; wallet fully restored | US-01, US-07 [BLOCKED] |

---

## State Machine Transitions

| Test Class | Scenario | Expected | Depends on Stories |
|---|---|---|---|
| `RedemptionRequestIntegrationTest` | Valid: RESERVED → PROCESSING | `BatchRedemptionProcessor.processBatch()` transitions eligible row; `processing_started_at` set | US-01, US-03 [BLOCKED] |
| `RedemptionRequestIntegrationTest` | Valid: PROCESSING → COMPLETED | Webhook completion handler transitions row; `completed_at` set; DEBIT ledger entry written | US-01, US-07 [BLOCKED] |
| `RedemptionRequestIntegrationTest` | Valid: PROCESSING → FAILED | Webhook failure handler transitions row; RELEASE ledger entry written; `failure_reason` populated | US-01, US-07 [BLOCKED] |
| `RedemptionRequestIntegrationTest` | Valid: RESERVED → CANCELLED | Cancellation (future F-04 or admin) transitions row; RELEASE ledger entry written | US-01 |
| `RedemptionRequestIntegrationTest` | Invalid: COMPLETED → any other status | Attempt to re-transition already-COMPLETED request → rejected; no ledger change | US-01, US-07 [BLOCKED] |
| `RedemptionRequestIntegrationTest` | Invalid: re-submit against reserved balance | Two submissions for same wallet/currency in flight → second returns 409 | US-01 |

---

## Business Rule Enforcement

| Test Class | Scenario | Expected | Depends on Stories |
|---|---|---|---|
| `RedemptionRequestIntegrationTest` | In-flight cap enforcement at boundary | Seed 10 RESERVED requests for user; submit 11th → 409; complete one → 11th succeeds | US-01 |
| `RedemptionRequestIntegrationTest` | In-flight cap uses `maxInFlightRedemptions` from tenant settings | Set client's `max_in_flight_redemptions=2`; submit 3rd → 409; confirms cap is configurable | US-01 |
| `RedemptionRequestIntegrationTest` | Amount below minimum rejected at service layer | Insert catalog item with `minimum_transaction_amount=50`; submit with amount=10 → 422 | US-01 |
| `RedemptionRequestIntegrationTest` | Balance reservation is atomic | Simulate DB failure after RESERVE ledger entry but before `RedemptionRequest` persist → both rolled back; wallet balance unchanged | US-01 |
| `RedemptionRequestIntegrationTest` | Batch date computation correctness | Submit BATCH redemption on Monday with weekly cadence → `scheduled_batch_date` = next configured weekly run date | US-01 |

---

## Webhook Idempotency

| Test Class | Scenario | Expected | Depends on Stories |
|---|---|---|---|
| `RedemptionWebhookIntegrationTest` | Duplicate webhook delivery — XTRM completion | Deliver same `idempotency_key` twice; assert exactly ONE DEBIT ledger entry; second webhook returns 200 | US-07 [BLOCKED] |
| `RedemptionWebhookIntegrationTest` | Duplicate webhook delivery — Xoxoday completion | Same as above for Xoxoday vendor | US-07 [BLOCKED] |
| `RedemptionWebhookIntegrationTest` | Webhook for already-COMPLETED request | Late/duplicate delivery after COMPLETED status; event DEAD_LETTERED; no second ledger write | US-07 [BLOCKED] |
| `RedemptionWebhookIntegrationTest` | Invalid HMAC signature rejected | POST with bad signature → 401; no `redemption_webhook_events` row created | US-07 [BLOCKED] |

---

## Multi-Entity Workflows

| Test Class | Scenario | Expected | Depends on Stories |
|---|---|---|---|
| `RedemptionFlowIntegrationTest` | Full INSTANT flow — personal wallet | Submit → RESERVED → vendor dispatch (mocked) → webhook completion → COMPLETED; ledger entries: RESERVE then DEBIT; wallet `available` unchanged, `reserved` = 0 | US-01, US-05/US-06, US-07 [BLOCKED] |
| `RedemptionFlowIntegrationTest` | Full BATCH flow | Submit → RESERVED with `scheduledBatchDate`; batch processor fires → PROCESSING; webhook → COMPLETED | US-01, US-03, US-07 [BLOCKED] |
| `RedemptionFlowIntegrationTest` | APPROVAL_REQUIRED flow gate | Submit → PENDING_APPROVAL; vendor NOT called; balance reserved; approval (F-04 stub) → RESERVED → vendor dispatch | US-01 (partial — F-04 not yet built) |
| `RedemptionFlowIntegrationTest` | Company wallet full flow | PARTNER_ADMIN submits company redemption; company wallet reserved; webhook completes; company wallet debited | US-02, US-07 [BLOCKED] |

---

## Contract Conformance

| Test Class | Scenario | Expected | Depends on Stories |
|---|---|---|---|
| `RedemptionContractConformanceTest` | `POST /api/v1/redemption/requests` response shape | 201 body matches `RedemptionSubmissionConfirmationResponse` contract schema; `Location` header present | US-01 |
| `RedemptionContractConformanceTest` | `POST /api/v1/redemption/requests/company` response shape | 201 body matches contract schema | US-02 |
| `RedemptionContractConformanceTest` | `GET /api/v1/redemption/requests` paginated list | 200 body matches `PaginatedResponse<RedemptionRequestResponse>` contract schema | US-01 |
| `RedemptionContractConformanceTest` | `GET /api/v1/redemption/requests/{id}` detail | 200 body matches `RedemptionRequestDetailResponse` contract schema | US-01 |

_Contract validator wired to `../tenxengage-contracts/endpoints/redemption-flow.yaml`. Run `/generate-contracts redemption-flow` first._

---

## Tenant Isolation & Security

| Test Class | Scenario | Expected | Depends on Stories |
|---|---|---|---|
| `RedemptionRequestIntegrationTest` | Tenant A creates; Tenant B queries by ID | `GET /api/v1/redemption/requests/{id}` as Tenant B → 404 (not 403 — prevents enumeration) | US-01 |
| `RedemptionRequestIntegrationTest` | Tenant A's redemption list not visible to Tenant B | `GET /api/v1/redemption/requests` as Tenant B → empty list, not Tenant A's rows | US-01 |
| `RedemptionRequestIntegrationTest` | PARTNER_SELLER cannot call company endpoint | `POST /api/v1/redemption/requests/company` as PARTNER_SELLER → 403 | US-02 |
| `RedemptionRequestIntegrationTest` | PARTNER_ADMIN can call both endpoints | Personal + company endpoints both return 201 for PARTNER_ADMIN | US-01, US-02 |
| `RedemptionWebhookIntegrationTest` | Webhook endpoints require no JWT | `POST /api/v1/webhooks/redemption/xtrm` without `Authorization` header → processed normally (not 401 for missing JWT) | US-07 [BLOCKED] |
| `RedemptionRequestIntegrationTest` | Optimistic lock on concurrent update | Two threads attempt to transition same `RedemptionRequest` simultaneously → one succeeds, one gets `OptimisticLockException` | US-01 |

---

## Audit & Events

| Test Class | Scenario | Expected | Depends on Stories |
|---|---|---|---|
| `RedemptionRequestIntegrationTest` | SUBMITTED audit record on personal redemption | `audit_log` has record with `action=SUBMITTED`, `resource_type=REDEMPTION_REQUEST`, correct `actor_id` and `resource_id` | US-01 |
| `RedemptionRequestIntegrationTest` | SUBMITTED audit record on company redemption | Same as above for company endpoint | US-02 |
| `RedemptionRequestIntegrationTest` | COMPLETED audit record on webhook completion | `audit_log` has record with `action=COMPLETED`, `resource_type=REDEMPTION_REQUEST` | US-07 [BLOCKED] |
| `RedemptionRequestIntegrationTest` | FAILED audit record on webhook failure | `audit_log` has `action=FAILED`, `resource_type=REDEMPTION_REQUEST` | US-07 [BLOCKED] |
| `RedemptionEventIntegrationTest` | `REDEMPTION_REQUESTED` Kafka event published and consumed | Submit personal redemption → verify `REDEMPTION_REQUESTED` event arrives on `redemption-events` topic with correct `redemptionRequestId`, `clientId`, `userId`, `amount`, `currencyId`, `category` | US-01, F5 |
| `RedemptionEventIntegrationTest` | `REDEMPTION_COMPLETED` event round-trip | Webhook completion → `REDEMPTION_COMPLETED` event → `RedemptionOrchestrationService` consumer receives → notification dispatched | US-04, US-07 [BLOCKED] |
| `RedemptionEventIntegrationTest` | `REDEMPTION_FAILED` event round-trip | Webhook failure → `REDEMPTION_FAILED` event → consumer receives → failure notification dispatched | US-04, US-07 [BLOCKED] |

---

## Cross-Cutting Checks [BE + FE]

| Check | Story / Foundation |
|---|---|
| Tenant isolation: resource created by Tenant A returns 404 to Tenant B | Foundation, US-01 |
| Soft delete: `deleted=true` redemption returns 404 on GET; not visible in list | US-01 |
| Optimistic locking: stale `version` on concurrent update → `OptimisticLockException` (no 409 exposed to API — internal) | US-01 |
| Audit log: every SUBMITTED, COMPLETED, FAILED creates record with correct `tenantId`, `userId`, timestamp | US-01, US-07 [BLOCKED] |
| PII in logs: verify log output does NOT contain `failureReason` content (may include PII from vendor error messages) | US-07 [BLOCKED] |
| Webhook signature validation: `X-Webhook-Signature` header missing → 401 | US-07 [BLOCKED] |
| Balance atomicity: any exception during submission rolls back both ledger entry and redemption request row | US-01 |

---

_Remove or update `[BLOCKED]` annotations on test rows as US-05, US-06, and US-07 are unblocked._
