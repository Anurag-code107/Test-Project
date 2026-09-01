---
id: US-05
title: "Route cash redemptions to XTRM"
layers: ["BE"]
seed_id: "F-03.S-03"
touches_entities: ["RedemptionRequest"]
depends_on_stories: ["US-01"]
---

# US-05: Route cash redemptions to XTRM

> ⚠️ **BLOCKED** — XTRM profile setup is incomplete; the TransferFund API is not currently working. This story cannot be implemented or tested end-to-end until Vijay confirms the XTRM profile is fully configured. Notified 2026-05-21.

## Description

**Actor:** `RedemptionOrchestrationService` (system-initiated; invoked immediately after submission for INSTANT mode, or by `BatchRedemptionProcessor` for BATCH mode)
**Trigger:** A `RedemptionRequest` with `category=CASH` and `status=RESERVED` is ready for vendor dispatch.

**Steps:**
1. `RedemptionOrchestrationService.dispatch(request)` checks `category`; routes CASH requests to `XtrmVendorService`.
2. `XtrmVendorService` assembles the XTRM TransferFund API payload using the partner's user identity (name, email, country) — sourced from the user profile at call time. No credentials or payment account numbers are stored in the platform.
3. Calls the XTRM TransferFund API; handles transient failures with exponential backoff (configurable max attempts).
4. On successful API call: sets `vendorReferenceId` from the XTRM response; status remains PROCESSING (finalized by webhook — US-07).
5. On permanent failure: writes RELEASE ledger entry; transitions `status` to FAILED; publishes `REDEMPTION_FAILED` Kafka event.

**Expected outcome:** Cash redemptions are dispatched to XTRM; vendor reference ID captured; ledger finalized on webhook (US-07).

**Negative paths:**
- Transient XTRM API failure → exponential backoff retry (configurable max attempts via `redemption.xtrm.maxRetries`).
- Permanent failure (max retries exceeded) → RELEASE ledger entry + status=FAILED + `REDEMPTION_FAILED` Kafka event.
- `category=NON_CASH` never routes to XTRM.

---

## Acceptance Criteria

- **AC-1:** `category=CASH` redemptions are routed to `XtrmVendorService`; `category=NON_CASH` redemptions never reach XTRM.
- **AC-2:** XTRM API call includes user identity fields (name, email, country) sourced from the user profile at call time; no credential or account number is stored in the platform.
- **AC-3:** Transient XTRM API failure triggers exponential backoff retry up to the configured maximum.
- **AC-4:** Permanent failure (max retries exceeded) → RELEASE ledger entry written; `status=FAILED`; `REDEMPTION_FAILED` Kafka event published.
- **AC-5:** `vendorReferenceId` from XTRM response is stored on the `RedemptionRequest` on successful dispatch.

---

## Out of Scope

- Webhook confirmation handling (US-07)
- Non-cash routing (US-06)
- KYC/AML/OFAC screening — handled natively by XTRM; platform only passes identity fields
- Batch dispatch scheduling (US-03)

---

## Depends on

- **Foundation tasks:** F1, F2, F3, F4, F5
- **Prior stories:** US-01 (establishes `RedemptionRequest` entity and `RedemptionOrchestrationService` stub)

---

## Spec references

- `## Functional Requirements` — FR-03.3, FR-03.4, FR-03.10
- `## Service Layer [BE]` — `XtrmVendorService`, `RedemptionOrchestrationService.dispatch()` routing logic, retry policy
- `## Security Design [BE]` — no PII/credentials stored; user identity fields passed at call time only
- `## Domain Events [BE]` — `REDEMPTION_FAILED` event on permanent failure

---

## BE tasks [BE]

> Tasks below are documented as a scaffold to be filled when XTRM profile is confirmed working. File paths and method signatures are final.

### BE-1: XtrmVendorService + unit test

**File:** `src/main/java/com/tenxengage/app/service/XtrmVendorService.java`
- `dispatch(RedemptionRequest request, UserProfile userProfile)` — assembles XTRM TransferFund payload; calls XTRM API; stores `vendorReferenceId` on success; handles retry
- Retry: Spring Retry `@Retryable` or equivalent; exponential backoff from `application.yml`
- On `@Recover`: write RELEASE ledger entry + transition to FAILED + publish `REDEMPTION_FAILED`

**File:** `src/test/java/com/tenxengage/app/service/XtrmVendorServiceTest.java`
- `dispatch_cash_callsXtrmApi_storesVendorReferenceId` _(AC-1, AC-2, AC-5)_
- `dispatch_transientFailure_retriesWithBackoff` _(AC-3)_
- `dispatch_permanentFailure_writesReleaseAndFails` _(AC-4)_

### BE-2: RedemptionOrchestrationService routing + unit test

**File:** `src/main/java/com/tenxengage/app/service/RedemptionOrchestrationService.java` (extend)
- `dispatch(RedemptionRequest request)` — routes on `category`: CASH → `XtrmVendorService.dispatch()`; NON_CASH → `XoxodayVendorService.dispatch()` (US-06)

**File:** `src/test/java/com/tenxengage/app/service/RedemptionOrchestrationServiceTest.java` (extend)
- `dispatch_cashCategory_routesToXtrm` — Mockito verify `XtrmVendorService.dispatch()` called _(AC-1)_
- `dispatch_nonCashCategory_doesNotRouteToXtrm` _(AC-1)_

---

## Execution checklist

> Items remain unchecked until story is unblocked.

**BE session:**
- [ ] `XtrmVendorService.dispatch()` implemented — XTRM API call, retry, recover _(AC-1, AC-2, AC-3, AC-4, AC-5)_
- [ ] `vendorReferenceId` stored on success _(AC-5)_
- [ ] RELEASE ledger entry + FAILED status + Kafka event on permanent failure _(AC-4)_
- [ ] `XtrmVendorServiceTest` all 3 cases pass _(AC-1–AC-5)_
- [ ] `RedemptionOrchestrationService.dispatch()` routing logic added _(AC-1)_
- [ ] `RedemptionOrchestrationServiceTest` routing cases pass _(AC-1)_

---

## Done when

1. **BE:** `./gradlew test` passes — `XtrmVendorServiceTest` + routing tests green.
2. Every AC (AC-1 through AC-5) referenced by at least one passing test.
3. Manual end-to-end verified with real XTRM TransferFund API (XTRM profile fully configured).
