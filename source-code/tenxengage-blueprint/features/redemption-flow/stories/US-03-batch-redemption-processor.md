---
id: US-03
title: "Batch redemption processor"
layers: ["BE"]
seed_id: "F-03.S-05"
touches_entities: ["RedemptionRequest"]
depends_on_stories: ["US-01"]
---

# US-03: Batch redemption processor

> ℹ️ **US-05/US-06 dependency removed.** `RedemptionOrchestrationService.dispatch()` stub (commit `97738f8`) is in place — unit tests mock the call. Real XTRM/Xoxoday routing wired in US-05/US-06 replaces the stub post-merge.

## Description

**Actor:** Scheduled job (system-initiated, no user interaction)
**Trigger:** `@Scheduled` annotation fires on the client-configured `batchCadence` (daily or weekly); `BatchRedemptionProcessor.processBatch()` is invoked.

**Steps:**
1. For each active client with batch-mode redemptions, query `RedemptionRequestRepository` for all rows where `status=RESERVED`, `processingMode=BATCH`, and `scheduledBatchDate ≤ today`.
2. For each eligible row: transition `status` from RESERVED → PROCESSING (optimistic lock version bump).
3. Dispatch each row to the appropriate vendor via `RedemptionOrchestrationService.dispatch()`:
   - `category=CASH` → XTRM (US-05)
   - `category=NON_CASH` → Xoxoday (US-06)
4. Log results per item; items that fail dispatch are moved to FAILED + RELEASE ledger entry written.

**Expected outcome:** All due batch redemptions are submitted to their respective vendors and transition to PROCESSING.

**Negative paths:**
- No eligible rows → processor exits cleanly with no state changes.
- Vendor dispatch failure for an individual item → that item moves to FAILED; other items continue.
- Optimistic lock conflict (item updated by another thread between query and transition) → skip and log; do not crash processor.

---

## Acceptance Criteria

- **AC-1:** `BatchRedemptionProcessor.processBatch()` queries only rows with `status=RESERVED`, `processingMode=BATCH`, and `scheduledBatchDate ≤ today` for the given `clientId`.
- **AC-2:** Each eligible row transitions from RESERVED → PROCESSING before vendor dispatch is called.
- **AC-3:** Rows in status PROCESSING, COMPLETED, or FAILED are not reprocessed.
- **AC-4:** Vendor dispatch is routed correctly — `category=CASH` calls XTRM dispatch; `category=NON_CASH` calls Xoxoday dispatch.
- **AC-5:** Processor does not affect redemption rows belonging to a different client (tenant isolation per-client run).

---

## Out of Scope

- Actual XTRM or Xoxoday API HTTP calls (implemented in US-05 / US-06)
- Webhook response handling (US-07)
- Batch schedule configuration UI (deferred to Phase 2)
- Admin dashboard showing batch run status (F-07)

---

## Depends on

- **Foundation tasks:** F1, F2, F3, F4, F5
- **Prior stories:** US-01 (establishes `RedemptionRequest` entity and service structure), US-05 (XTRM dispatch), US-06 (Xoxoday dispatch)

---

## Spec references

- `## Functional Requirements` — FR-03.5
- `## Service Layer [BE]` — `BatchRedemptionProcessor.processBatch()` and `RedemptionOrchestrationService.dispatch()` business rules
- `## Data Model / Entities [BE]` — `RedemptionRequest.scheduledBatchDate`, `processingMode`, `status`
- `technical.md → ## Repository Queries [BE] → RedemptionRequestRepository` — `findByClientIdAndStatusAndProcessingModeAndScheduledBatchDateLessThanEqual(...)`

---

## BE tasks [BE]

### BE-1: BatchRedemptionProcessor + unit test

**File:** `src/main/java/com/tenxengage/app/service/BatchRedemptionProcessor.java`
- `@Component` with `@Scheduled(cron = "${redemption.batch.cron}")` or equivalent
- `processBatch()` method:
  1. Load all active clients (or iterate by client if scheduler is client-scoped)
  2. For each client: call `RedemptionRequestRepository.findByClientIdAndStatusAndProcessingModeAndScheduledBatchDateLessThanEqual(clientId, RESERVED, BATCH, LocalDate.now())`
  3. For each result: transition to PROCESSING, save, then call `RedemptionOrchestrationService.dispatch(request)`
  4. Handle exceptions per-item; do not let a single failure abort the entire batch

**File:** `src/test/java/com/tenxengage/app/service/BatchRedemptionProcessorTest.java`
- `processBatch_findsEligibleRows_andTransitionsToProcessing` _(AC-1, AC-2)_
- `processBatch_skipsAlreadyProcessingRows` _(AC-3)_
- `processBatch_routesCash_toXtrm` — Mockito verify `RedemptionOrchestrationService.dispatch()` called with CASH item _(AC-4)_
- `processBatch_routesNonCash_toXoxoday` — Mockito verify with NON_CASH item _(AC-4)_
- `processBatch_doesNotAffectOtherClients` — two clients seeded; only target client's rows transition _(AC-5)_
- `processBatch_noEligibleRows_exitsCleanly` _(AC-1)_

---

## Execution checklist

**BE session:**
- [ ] `BatchRedemptionProcessor.java` created with `@Scheduled` annotation _(AC-1)_
- [ ] `processBatch()` uses correct repository query with clientId + RESERVED + BATCH + date ≤ today _(AC-1)_
- [ ] Each eligible row transitions RESERVED → PROCESSING before dispatch call _(AC-2)_
- [ ] PROCESSING/COMPLETED/FAILED rows skipped _(AC-3)_
- [ ] Dispatch routed to `RedemptionOrchestrationService` by `category` _(AC-4)_
- [ ] Per-item exception handling — one failure does not abort batch
- [ ] `BatchRedemptionProcessorTest` all 6 cases pass _(AC-1–AC-5)_

---

## Done when

1. **BE:** `./gradlew test` passes — all `BatchRedemptionProcessorTest` cases green.
2. Every AC (AC-1 through AC-5) referenced by at least one passing test.

_Full end-to-end (scheduler fires → vendor called → webhook → COMPLETED) is only verifiable after US-05, US-06, and US-07 are unblocked._
