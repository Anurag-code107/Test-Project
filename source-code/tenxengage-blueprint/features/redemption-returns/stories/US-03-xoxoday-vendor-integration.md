---
id: US-03
title: Xoxoday vendor integration (notify + webhook)
layers: ["BE"]
touches_entities: ["RedemptionReturn"]
depends_on_stories: ["US-02"]
seed_id: ["S-03", "S-04"]
---

# US-03: Xoxoday vendor integration (notify + webhook)

## Description

**As the** TenXEngage platform (system actor),
**After** a CLIENT_ADMIN or ACTIVITY_APPROVER approves a return request,
**I want to** notify Xoxoday's return API asynchronously, receive Xoxoday's confirmation or rejection via webhook, and credit the partner's wallet only on confirmed returns,
**So that** wallet credits are issued only when the vendor has verified the return, preventing premature balance restoration.

**Flow (outbound — triggered by US-02 approval):**
1. `ReturnService.approveReturn()` fires `@Async ReturnVendorService.notifyXoxodayReturn(return)`.
2. `notifyXoxodayReturn()` calls the Xoxoday return API with exponential backoff: 5 attempts (initial 1s, multiplier 2×, cap 32s).
3. On success: stores `vendorReturnReference` on the `RedemptionReturn` record; logs `return_vendor_notify_success`.
4. On persistent failure (all 5 attempts): routes to DLQ, raises ops alert, logs `return_vendor_notify_failed_dlq`; return stays in `APPROVED` state (RETURN_TIMED_OUT scheduler fires at T+7d as safety net per FR-06.13).

**Flow (inbound — Xoxoday webhook):**
1. Xoxoday calls `POST /api/v1/webhooks/redemption-returns/xoxoday` with HMAC-SHA256 signature.
2. `ReturnWebhookController` validates HMAC (403 on failure); validates vendor path param (404 for unknown vendor).
3. Idempotency check: `findByVendorReturnReference(vendorReturnReference)` — if already terminal, return 200 no-op.
4. Parses payload to determine `confirmed = true` or `false`.
5. Calls `ReturnService.processVendorConfirmation(vendorReturnReference, confirmed, failureReason)`.
6. **Confirmed:** calls `WalletMutationDelegate.doReturnCreditInTx()` (writes `RETURN_CREDIT` ledger entry, restores available balance); transitions to `RETURN_CONFIRMED`; sets `confirmedAt`; publishes `RETURN_CONFIRMED` event; partner notified.
7. **Rejected:** transitions to `RETURN_REJECTED`; sets `rejectedAt`; publishes `RETURN_REJECTED` event; partner notified with vendor rejection reason. No wallet credit.
8. Returns `200` in all cases (including duplicates).

**No FE work in this story** — status changes are visible via the 2 min `staleTime` on existing hooks.

---

## Acceptance Criteria

- **AC-1** `ReturnVendorService.notifyXoxodayReturn()` is called `@Async` (non-blocking) immediately after `approveReturn()` completes; the approve API response returns `200` before the Xoxoday call completes.
- **AC-2** `notifyXoxodayReturn()` retries on transient failure with exponential backoff: 5 attempts, delays 1s / 2s / 4s / 8s / 32s; on all-5 failure: DLQ routed + ops alert raised + `return_vendor_notify_failed_dlq` logged at ERROR; return remains in `APPROVED` state (not auto-rejected).
- **AC-3** On Xoxoday webhook `confirmed = true`: `WalletMutationDelegate.doReturnCreditInTx()` called for the full original amount; return transitions to `RETURN_CONFIRMED`; `confirmedAt` set; `RETURN_CONFIRMED` Kafka event published; partner notified; response is `200`.
- **AC-4** On Xoxoday webhook `confirmed = false`: return transitions to `RETURN_REJECTED`; `rejectedAt` set; no wallet credit issued; `RETURN_REJECTED` Kafka event published; partner notified with vendor failure reason; response is `200`.
- **AC-5** Webhook idempotency: a duplicate webhook for a `vendorReturnReference` already in a terminal state (`RETURN_CONFIRMED`, `RETURN_REJECTED`) or `RETURN_TIMED_OUT` returns `200` with no state change; logged at WARN as `return_webhook_duplicate`.
- **AC-6** HMAC-SHA256 validation on webhook: invalid signature returns `403`; unknown vendor path param returns `404`; valid signature proceeds to processing.

---

## Out of Scope

- RETURN_TIMED_OUT scheduler — Foundation F5
- Admin manual resolve of RETURN_TIMED_OUT — US-04
- FE status updates — handled by existing 2 min staleTime on `useReturn` and `useAdminReturns` hooks (no change needed)
- Other vendors (only `xoxoday` is valid; other values → 404)

---

## Non-Functional Notes

Webhook endpoint: `POST /api/v1/webhooks/redemption-returns/{vendor}` — excluded from JWT security filter chain; HMAC-SHA256 gated (same mechanism as `RedemptionWebhookController`). Rate limit: 100 req/min per source IP.

`doReturnCreditInTx()` idempotency guard: if called twice for the same `returnId` (webhook and manual resolve race), the second call must be a no-op — the guard is inside `WalletMutationDelegate`.

---

## Depends on

- **US-02 BE done** — `ReturnService.approveReturn()` must exist and fire the async hook
- **F-05 merged** — `WalletMutationDelegate.doReturnCreditInTx()` must exist
- **F-03 merged** — `LedgerEntryType.RETURN_CREDIT` must exist
- **Foundation F5** — `ReturnEventProducer` must be in place for publishing `RETURN_CONFIRMED` / `RETURN_REJECTED` events

---

## Spec References

- FR-06.4 (async Xoxoday call after approval), FR-06.5 (webhook confirm + wallet credit), FR-06.6 (webhook reject), FR-06.13 (exponential backoff + DLQ)
- `spec.md → ## Service Layer → ReturnVendorService, ReturnService.processVendorConfirmation()`
- `spec.md → ## API Endpoints → Webhook`
- `spec.md → ## Observability → return_vendor_notify_*, return_webhook_*`
- `spec.md → ## Edge Cases → 7, 8`
- `technical.md → ## Package Layout [BE] → ReturnVendorService, ReturnWebhookController`

---

## BE Tasks

### BE-1: DTOs / request parsing

- [ ] Define internal record `XoxodayReturnWebhookPayload` for parsing the raw JSON body — fields: `vendorReturnReference (String)`, `confirmed (boolean)`, `failureReason (String, nullable)`. This is an internal parsing record, not a response DTO.

### BE-2: Service + unit test

- [ ] `ReturnVendorService.notifyXoxodayReturn(RedemptionReturn)` annotated `@Async`:
  - Calls Xoxoday return API (HTTP POST to configured Xoxoday endpoint) with return details
  - Exponential backoff retry: 5 attempts — delays 1s / 2s / 4s / 8s / 32s (use Spring `@Retryable` or manual retry loop)
  - On success: sets `vendorReturnReference` on the return entity, saves, logs `return_vendor_notify_success`
  - On all-5 failure: routes event to DLQ (`return-events.DLT` or dedicated DLQ topic), raises ops alert (log at ERROR with `step=return_vendor_notify_failed_dlq`), does NOT change return status
- [ ] `ReturnService.processVendorConfirmation(vendorReturnReference, confirmed, failureReason)` annotated `@Transactional`:
  - Fetches return via `findByVendorReturnReference(vendorReturnReference)`; if null → log WARN + return (idempotency)
  - Idempotency check: if return is already in `RETURN_CONFIRMED`, `RETURN_REJECTED`, or `RETURN_TIMED_OUT` → log at WARN (`return_webhook_duplicate`), return 200 no-op
  - `confirmed = true` path: call `doReturnCreditInTx(return)`, set `status=RETURN_CONFIRMED`, set `confirmedAt`, save, publish `RETURN_CONFIRMED` event
  - `confirmed = false` path: set `status=RETURN_REJECTED`, set `rejectedAt`, set `reviewNotes` to `failureReason`, save, publish `RETURN_REJECTED` event, notify partner
- [ ] `ReturnVendorServiceTest` — unit tests (mock HTTP client):
  - Successful API call → `vendorReturnReference` stored
  - Transient failure → retries up to 5 attempts
  - All-5 failure → DLQ + ops alert; return stays APPROVED
- [ ] `ReturnServiceTest` additions — processVendorConfirmation: confirm path (credit called), reject path (no credit), idempotency (already RETURN_CONFIRMED → no-op), unknown vendorReturnReference → WARN log

### BE-3: Controller + `@WebMvcTest`

- [ ] `ReturnWebhookController` at `/api/v1/webhooks/redemption-returns`:
  - `POST /{vendor}` — raw JSON body (`@RequestBody String rawBody`):
    - Unknown vendor → 404
    - HMAC-SHA256 validation using shared secret (same mechanism as `RedemptionWebhookController`) — 403 on failure
    - Parse `rawBody` → `XoxodayReturnWebhookPayload`
    - Call `ReturnService.processVendorConfirmation(...)`
    - Always return `200` (including duplicates)
  - Not behind JWT filter — excluded from security filter chain
  - Rate limit: 100 req/min per source IP
- [ ] `ReturnWebhookControllerTest` (`@WebMvcTest`):
  - Valid HMAC + confirm=true → 200
  - Valid HMAC + confirm=false → 200
  - Invalid HMAC → 403
  - Unknown vendor → 404
  - Duplicate (idempotent) → 200 with no service call

### BE-4: Audit

- [ ] `@Audited(action = COMPLETED, resourceType = REDEMPTION_RETURN, description = "Return confirmed by Xoxoday")` on webhook confirm path
- [ ] `@Audited(action = REJECTED, resourceType = REDEMPTION_RETURN, description = "Return rejected by Xoxoday")` on webhook reject path

---

## E2E Scenarios

| File | Scenario | AC coverage |
|---|---|---|
| `e2e/redemption-returns/webhook-confirm.spec.ts` | Simulate Xoxoday confirm webhook → partner return shows RETURN_CONFIRMED; wallet balance restored | AC-3 |
| `e2e/redemption-returns/webhook-idempotency.spec.ts` | Send same webhook twice → second returns 200 with no state change | AC-5 |

---

## Execution Checklist

- [ ] BE-1: Define `XoxodayReturnWebhookPayload` internal parsing record
- [ ] BE-2: Write `ReturnVendorService.notifyXoxodayReturn()` with `@Async` + exponential backoff + DLQ on failure
- [ ] BE-2: Write `ReturnService.processVendorConfirmation()` with idempotency guard + confirm/reject paths
- [ ] BE-2: Write `ReturnVendorServiceTest` (mock HTTP — success, retry, all-fail)
- [ ] BE-2: Extend `ReturnServiceTest` with processVendorConfirmation unit tests
- [ ] BE-3: Write `ReturnWebhookController` with HMAC validation + vendor guard
- [ ] BE-3: Write `ReturnWebhookControllerTest`
- [ ] BE-4: Add `@Audited` on webhook confirm (COMPLETED) and reject (REJECTED) paths
- [ ] Run `./gradlew test` — all tests pass
- [ ] Update `tracker.md` — set US-03 BE status to `done`

---

## Done When

- [ ] Approve API returns 200 before Xoxoday call completes (async verified in unit test via thread assertion)
- [ ] `notifyXoxodayReturn()` retries 5 times on failure; routes to DLQ on all-fail; return stays `APPROVED`
- [ ] Webhook confirm → `RETURN_CONFIRMED` + wallet credit (`doReturnCreditInTx()` called)
- [ ] Webhook reject → `RETURN_REJECTED` + no wallet credit
- [ ] Duplicate webhook → 200 no-op; `return_webhook_duplicate` logged at WARN
- [ ] Invalid HMAC → 403; unknown vendor → 404
- [ ] Audit records written for webhook confirm (COMPLETED) and reject (REJECTED)
- [ ] `./gradlew test` passes
