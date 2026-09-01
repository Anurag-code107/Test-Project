---
id: US-06
title: "Route non-cash redemptions to Xoxoday"
layers: ["BE"]
seed_id: "F-03.S-04"
touches_entities: ["RedemptionRequest"]
depends_on_stories: ["US-01"]
---

# US-06: Route non-cash redemptions to Xoxoday

> ⚠️ **BLOCKED** — Xoxoday API credentials are not yet available. This story cannot be implemented or tested until credentials are provided. Notified 2026-05-21.

## Description

**Actor:** `RedemptionOrchestrationService` (system-initiated)
**Trigger:** A `RedemptionRequest` with `category=NON_CASH` and `status=RESERVED` is ready for vendor dispatch (INSTANT mode or batch via US-03).

**Steps:**
1. `RedemptionOrchestrationService.dispatch(request)` checks `category`; routes NON_CASH requests to `XoxodayVendorService`.
2. `XoxodayVendorService` assembles the Xoxoday order placement API payload using the catalog item's Xoxoday product code, amount, and recipient details.
3. Calls the Xoxoday order placement API; handles transient failures with exponential backoff.
4. On successful API call: stores `vendorReferenceId` from Xoxoday response; status remains PROCESSING (finalized by webhook — US-07).
5. On permanent failure: writes RELEASE ledger entry; transitions `status` to FAILED; publishes `REDEMPTION_FAILED` Kafka event.

**Expected outcome:** Non-cash redemptions (gift cards, vouchers) are dispatched to Xoxoday; order reference ID captured; ledger finalized on webhook (US-07).

**Negative paths:**
- Transient Xoxoday API failure → exponential backoff retry.
- Permanent failure → RELEASE ledger entry + FAILED status + `REDEMPTION_FAILED` Kafka event.
- `category=CASH` never routes to Xoxoday.

---

## Acceptance Criteria

- **AC-1:** `category=NON_CASH` redemptions are routed to `XoxodayVendorService`; `category=CASH` redemptions never reach Xoxoday.
- **AC-2:** Xoxoday API call includes the catalog item's Xoxoday product code and recipient details; no credential or payment account is stored.
- **AC-3:** Transient Xoxoday API failure triggers exponential backoff retry up to the configured maximum.
- **AC-4:** Permanent failure → RELEASE ledger entry written; `status=FAILED`; `REDEMPTION_FAILED` Kafka event published.
- **AC-5:** `vendorReferenceId` from Xoxoday response is stored on `RedemptionRequest` on successful dispatch.

---

## Out of Scope

- Cash routing (US-05)
- Webhook confirmation handling (US-07)
- Non-cash returns (F-06 — separate feature)
- Batch dispatch scheduling (US-03)

---

## Depends on

- **Foundation tasks:** F1, F2, F3, F4, F5
- **Prior stories:** US-01 (establishes `RedemptionRequest` entity and `RedemptionOrchestrationService` stub)

---

## Spec references

- `## Functional Requirements` — FR-03.3, FR-03.4, FR-03.10
- `## Service Layer [BE]` — `XoxodayVendorService`, `RedemptionOrchestrationService.dispatch()` routing logic, retry policy
- `## Domain Events [BE]` — `REDEMPTION_FAILED` event on permanent failure

---

## BE tasks [BE]

> Tasks below are documented as a scaffold to be filled when Xoxoday credentials arrive.

### BE-1: XoxodayVendorService + unit test

**File:** `src/main/java/com/tenxengage/app/service/XoxodayVendorService.java`
- `dispatch(RedemptionRequest request)` — assembles Xoxoday order payload; calls Xoxoday order placement API; stores `vendorReferenceId` on success; handles retry
- Retry: Spring Retry `@Retryable`; exponential backoff from `application.yml`
- On `@Recover`: RELEASE ledger entry + FAILED status + `REDEMPTION_FAILED` Kafka event

**File:** `src/test/java/com/tenxengage/app/service/XoxodayVendorServiceTest.java`
- `dispatch_nonCash_callsXoxodayApi_storesVendorReferenceId` _(AC-1, AC-2, AC-5)_
- `dispatch_transientFailure_retriesWithBackoff` _(AC-3)_
- `dispatch_permanentFailure_writesReleaseAndFails` _(AC-4)_

### BE-2: RedemptionOrchestrationService routing (extend US-05)

**File:** `src/main/java/com/tenxengage/app/service/RedemptionOrchestrationService.java` (extend from US-05)
- `dispatch()` already routes CASH → XTRM; extend to route NON_CASH → `XoxodayVendorService.dispatch()`

**File:** `src/test/java/com/tenxengage/app/service/RedemptionOrchestrationServiceTest.java` (extend)
- `dispatch_nonCashCategory_routesToXoxoday` _(AC-1)_
- `dispatch_cashCategory_doesNotRouteToXoxoday` _(AC-1)_

---

## Execution checklist

> Items remain unchecked until story is unblocked.

**BE session:**
- [ ] `XoxodayVendorService.dispatch()` implemented _(AC-1, AC-2, AC-3, AC-4, AC-5)_
- [ ] `vendorReferenceId` stored on success _(AC-5)_
- [ ] RELEASE ledger entry + FAILED status + Kafka event on permanent failure _(AC-4)_
- [ ] `XoxodayVendorServiceTest` all 3 cases pass _(AC-1–AC-5)_
- [ ] `RedemptionOrchestrationService.dispatch()` NON_CASH routing added _(AC-1)_
- [ ] `RedemptionOrchestrationServiceTest` Xoxoday routing cases pass _(AC-1)_

---

## Done when

1. **BE:** `./gradlew test` passes — `XoxodayVendorServiceTest` + routing tests green.
2. Every AC (AC-1 through AC-5) referenced by at least one passing test.
3. Manual end-to-end verified with real Xoxoday order placement API (credentials confirmed).
