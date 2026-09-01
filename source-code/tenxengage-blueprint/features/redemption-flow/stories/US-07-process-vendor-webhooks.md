---
id: US-07
title: "Process vendor webhooks idempotently"
layers: ["BE"]
seed_id: "F-03.S-06"
touches_entities: ["RedemptionRequest", "RedemptionWebhookEvent"]
depends_on_stories: ["US-05", "US-06"]
---

# US-07: Process vendor webhooks idempotently

> ⚠️ **BLOCKED** — Depends on US-05 (XTRM) and US-06 (Xoxoday). Vendor webhook payload shapes are not finalized until the vendor APIs are confirmed working. Notified 2026-05-21.

## Description

**Actor:** XTRM or Xoxoday (inbound HTTP POST from vendor)
**Trigger:** XTRM or Xoxoday POSTs a status update to `POST /api/v1/webhooks/redemption/{vendor}` after a payout attempt completes or fails.

**Steps:**
1. `RedemptionWebhookController` receives the request — no JWT required; endpoints excluded from JWT filter chain.
2. Validates HMAC-SHA256 signature using the vendor-specific signing secret from config. Invalid signature → 401; no processing.
3. Extracts `idempotency_key` from the payload; checks `RedemptionWebhookEventRepository.findByIdempotencyKey()`. If found → return 200 (already processed); no further action.
4. Persists a `RedemptionWebhookEvent` with `status=RECEIVED`.
5. Dispatches to `RedemptionWebhookService.process()`:
   - Completion event → DEBIT ledger entry + `status=COMPLETED` + `completedAt` timestamp + `REDEMPTION_COMPLETED` Kafka event.
   - Failure event → RELEASE ledger entry + `status=FAILED` + `failureReason` + `REDEMPTION_FAILED` Kafka event.
6. Updates `RedemptionWebhookEvent.status` to PROCESSED.
7. Returns 200.
8. On unprocessable event (exception after max retries) → `RedemptionWebhookEvent.status=DEAD_LETTERED` for manual investigation.

**Expected outcome:** Vendor webhook finalizes the redemption ledger exactly once, regardless of duplicate delivery.

**Negative paths:**
- Invalid HMAC signature → 401, no webhook event persisted.
- Duplicate `idempotency_key` → 200 returned immediately; no second ledger write.
- Webhook for an already-COMPLETED or already-FAILED request → DEAD_LETTERED; no double-debit.
- Processing exception → max retries → DEAD_LETTERED with `failure_reason`.

---

## Acceptance Criteria

- **AC-1:** Invalid HMAC-SHA256 signature → 401 response; `RedemptionWebhookEvent` is NOT persisted.
- **AC-2:** Duplicate `idempotency_key` → 200 response; no new ledger entry written; `RedemptionWebhookEvent.status=DUPLICATE`.
- **AC-3:** Valid completion webhook → DEBIT ledger entry written; `RedemptionRequest.status=COMPLETED`; `completedAt` populated; `REDEMPTION_COMPLETED` Kafka event published.
- **AC-4:** Valid failure webhook → RELEASE ledger entry written; `RedemptionRequest.status=FAILED`; `failureReason` populated; `REDEMPTION_FAILED` Kafka event published.
- **AC-5:** Webhook event that cannot be processed after max retries → `RedemptionWebhookEvent.status=DEAD_LETTERED`; no exception propagated to the HTTP layer (returns 200 to vendor to prevent vendor retry storm).
- **AC-6:** Webhook endpoints (`/api/v1/webhooks/redemption/**`) are excluded from the JWT filter chain — no `Authorization` header required or checked.

---

## Out of Scope

- Partner notification dispatch on completion/failure (US-04 — separate consumer)
- DLQ admin review UI (F-07)
- Batch dispatch triggering (US-03)
- Non-cash returns (F-06)

---

## Depends on

- **Foundation tasks:** F1, F2, F3, F4, F5
- **Prior stories:** US-05 (XTRM dispatch — defines XTRM webhook payload shape), US-06 (Xoxoday dispatch — defines Xoxoday webhook payload shape)

---

## Spec references

- `## Functional Requirements` — FR-03.7, FR-03.8, FR-03.9, FR-03.10
- `## API Endpoints [BE + FE]` — `POST /api/v1/webhooks/redemption/{vendor}` (HMAC-gated, no JWT)
- `## Service Layer [BE]` — `RedemptionWebhookService.process()`, idempotency design, DLQ handling
- `## Security Design [BE]` — HMAC-SHA256 validation; webhook endpoints excluded from JWT filter chain
- `## Domain Events [BE]` — `REDEMPTION_COMPLETED` and `REDEMPTION_FAILED` events published from webhook handler
- `technical.md → ## Repository Queries [BE] → RedemptionWebhookEventRepository` — `findByIdempotencyKey()`

---

## BE tasks [BE]

> Tasks below are scaffolded — fill when unblocked.

### BE-1: DTOs (webhook request shapes)

**Files:** (one per vendor — shapes defined by vendor API; fill when credentials available)
- `src/main/java/com/tenxengage/app/dto/request/XtrmWebhookPayload.java`
- `src/main/java/com/tenxengage/app/dto/request/XoxodayWebhookPayload.java`

### BE-2: RedemptionWebhookService + unit test

**File:** `src/main/java/com/tenxengage/app/service/RedemptionWebhookService.java`
- `process(RedemptionWebhookEvent event)` — dispatches on event type; writes DEBIT or RELEASE ledger entry; transitions `RedemptionRequest` status; publishes Kafka event
- Idempotency check is performed in the controller before calling this method
- Optimistic lock on `RedemptionRequest` prevents double-processing under concurrent delivery

**File:** `src/test/java/com/tenxengage/app/service/RedemptionWebhookServiceTest.java`
- `process_completionEvent_writesDebitAndCompletes` _(AC-3)_
- `process_failureEvent_writesReleaseAndFails` _(AC-4)_
- `process_alreadyCompleted_deadLetters` — webhook for already-COMPLETED request → DEAD_LETTERED _(AC-5)_
- `process_kafkaEventPublished_afterCommit` — verify `REDEMPTION_COMPLETED` event _(AC-3)_

### BE-3: RedemptionWebhookController + @WebMvcTest

**File:** `src/main/java/com/tenxengage/app/controller/RedemptionWebhookController.java`
- Tag: `Redemption Webhooks`
- `POST /api/v1/webhooks/redemption/{vendor}` — no JWT; HMAC-SHA256 validation in controller/filter; idempotency check before dispatch

**File:** `src/test/java/com/tenxengage/app/controller/RedemptionWebhookControllerTest.java`
- `POST_invalidHmac_returns401` _(AC-1)_
- `POST_duplicateIdempotencyKey_returns200_noSideEffects` _(AC-2)_
- `POST_validCompletion_returns200` _(AC-3)_
- `POST_validFailure_returns200` _(AC-4)_
- `POST_noAuthorizationHeaderRequired` _(AC-6)_

### BE-4: Audit annotation

Add `@Audited(action = AuditAction.COMPLETED, resourceType = AuditResourceType.REDEMPTION_REQUEST, description = "XTRM confirmed fulfillment")` on the XTRM completion handler; `FAILED` equivalent for failure. See `technical.md → ## Audit Annotations [BE]`.

---

## Execution checklist

> Items remain unchecked until story is unblocked.

**BE session:**
- [ ] XTRM + Xoxoday webhook payload DTOs created _(AC-1)_
- [ ] HMAC-SHA256 validation implemented in controller/filter; invalid signature → 401 _(AC-1)_
- [ ] Idempotency check via `findByIdempotencyKey()` before processing; duplicate → 200 + DUPLICATE status _(AC-2)_
- [ ] `RedemptionWebhookService.process()` — completion path: DEBIT entry + COMPLETED + Kafka event _(AC-3)_
- [ ] `RedemptionWebhookService.process()` — failure path: RELEASE entry + FAILED + Kafka event _(AC-4)_
- [ ] Dead-letter on unprocessable event (max retries); returns 200 to vendor _(AC-5)_
- [ ] JWT filter exclusion for `/api/v1/webhooks/redemption/**` confirmed in security config _(AC-6)_
- [ ] `RedemptionWebhookServiceTest` all 4 cases pass _(AC-3–AC-5)_
- [ ] `RedemptionWebhookControllerTest` all 5 cases pass _(AC-1–AC-4, AC-6)_
- [ ] `@Audited` annotations on COMPLETED and FAILED handlers

---

## Done when

1. **BE:** `./gradlew test` passes — `RedemptionWebhookServiceTest` + `RedemptionWebhookControllerTest` all green.
2. Every AC (AC-1 through AC-6) referenced by at least one passing test.
3. Manual end-to-end verified: real vendor webhook delivered → ledger finalized → `status=COMPLETED`.
