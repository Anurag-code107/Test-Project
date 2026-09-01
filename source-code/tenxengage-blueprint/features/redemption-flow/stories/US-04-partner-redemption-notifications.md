---
id: US-04
title: "Partner redemption notifications"
layers: ["BE"]
seed_id: "F-03.S-07"
touches_entities: ["RedemptionRequest"]
depends_on_stories: ["US-01"]
---

# US-04: Partner redemption notifications

> ℹ️ **Partially implementable now.** The `REDEMPTION_REQUESTED` consumer (AC-1) is testable once US-01 is done. The `REDEMPTION_COMPLETED` and `REDEMPTION_FAILED` consumers (AC-2, AC-3) require US-07 to produce those events — their E2E tests are blocked until US-07 is unblocked.

## Description

**Actor:** Kafka consumer (system-initiated)
**Trigger:** A `REDEMPTION_REQUESTED`, `REDEMPTION_COMPLETED`, or `REDEMPTION_FAILED` event arrives on the `redemption-events` topic.

**Steps:**
1. `RedemptionOrchestrationService` (or a dedicated Kafka consumer) receives the event from `redemption-events`.
2. Inspects `eventType` field to determine notification type.
3. Dispatches to `NotificationService`:
   - `REDEMPTION_REQUESTED` → submission confirmation notification (in-app + email)
   - `REDEMPTION_COMPLETED` → fulfillment completion notification with payout details
   - `REDEMPTION_FAILED` → failure notification with `failureReason`
4. Notification service delivers via existing notification infrastructure.

**Expected outcome:** Partners receive timely notifications at each lifecycle stage of their redemption.

**Negative paths:**
- Unknown `eventType` value → log WARN at `[step=redemption-notification-skipped]` and skip; do not throw or DLQ.
- `NotificationService` throws → log ERROR; do not roll back or reprocess the redemption itself (notification is fire-and-forget).

---

## Acceptance Criteria

- **AC-1:** `REDEMPTION_REQUESTED` event consumed → submission confirmation notification dispatched to the partner (identified by `userId` in the event payload).
- **AC-2:** `REDEMPTION_COMPLETED` event consumed → completion notification dispatched with payout details.
- **AC-3:** `REDEMPTION_FAILED` event consumed → failure notification dispatched with `failureReason`.
- **AC-4:** Unknown `eventType` values are logged at WARN level and skipped — consumer does not crash or DLQ on unknown types.

---

## Out of Scope

- Balance release on failure — that is the responsibility of US-07 (webhook handler)
- Notification preference management (user settings for email vs. in-app)
- Notification delivery mechanism (existing `NotificationService` is reused; this story only wires the redemption events to it)

---

## Depends on

- **Foundation tasks:** F1, F2, F3, F4, F5
- **Prior stories:** US-01 (establishes `RedemptionEventProducer` and `redemption-events` topic in F5; `REDEMPTION_REQUESTED` event produced by US-01 submission flow)

---

## Spec references

- `## Functional Requirements` — FR-03.7 (notify on completion), FR-03.8 (notify on failure)
- `## Domain Events [BE]` — `REDEMPTION_REQUESTED`, `REDEMPTION_COMPLETED`, `REDEMPTION_FAILED` event payloads on `redemption-events` topic
- `## Service Layer [BE]` — `RedemptionOrchestrationService` notification dispatch
- `## Observability [BE]` — log step values for notification events

---

## BE tasks [BE]

### BE-1: Kafka consumer + unit test

**File:** `src/main/java/com/tenxengage/app/service/RedemptionOrchestrationService.java`
- `@KafkaListener(topics = KafkaConfig.REDEMPTION_EVENTS_TOPIC, groupId = "redemption-notifications")`
- Method `handleRedemptionEvent(RedemptionEventPayload event)`:
  - Switch on `event.eventType()`:
    - `REDEMPTION_REQUESTED` → `notificationService.sendRedemptionSubmitted(event.userId(), event.redemptionRequestId())`
    - `REDEMPTION_COMPLETED` → `notificationService.sendRedemptionCompleted(event.userId(), event.redemptionRequestId(), event.amount())`
    - `REDEMPTION_FAILED` → `notificationService.sendRedemptionFailed(event.userId(), event.redemptionRequestId(), event.failureReason())`
    - default → `log.warn("[step=redemption-notification-skipped] unknown eventType={}", event.eventType())`
  - Wrap `notificationService` calls in try-catch; on exception: `log.error(...)`, do not rethrow

**File:** `src/test/java/com/tenxengage/app/service/RedemptionOrchestrationServiceTest.java`
- `handleEvent_redemptionRequested_dispatchesSubmissionNotification` — Mockito verify `notificationService.sendRedemptionSubmitted()` called _(AC-1)_
- `handleEvent_redemptionCompleted_dispatchesCompletionNotification` _(AC-2)_
- `handleEvent_redemptionFailed_dispatchesFailureNotification` _(AC-3)_
- `handleEvent_unknownEventType_logsWarnAndSkips` — verify no notification dispatched, no exception thrown _(AC-4)_
- `handleEvent_notificationServiceThrows_doesNotRethrow` — verify consumer survives notification failure _(AC-4)_

---

## Execution checklist

**BE session:**
- [ ] `RedemptionOrchestrationService.java` created with `@KafkaListener` on `redemption-events` topic _(AC-1)_
- [ ] `REDEMPTION_REQUESTED` handler dispatches submission notification _(AC-1)_
- [ ] `REDEMPTION_COMPLETED` handler dispatches completion notification _(AC-2)_
- [ ] `REDEMPTION_FAILED` handler dispatches failure notification _(AC-3)_
- [ ] Unknown eventType logged at WARN, consumer does not throw _(AC-4)_
- [ ] `NotificationService` failures caught and logged; consumer does not rethrow _(AC-4)_
- [ ] `RedemptionOrchestrationServiceTest` all 5 cases pass _(AC-1–AC-4)_

---

## Done when

1. **BE:** `./gradlew test` passes — all `RedemptionOrchestrationServiceTest` notification cases green.
2. Every AC (AC-1 through AC-4) referenced by at least one passing test.

_Note: AC-2 and AC-3 unit tests are written with mock events. Round-trip E2E (real event produced by US-07 → consumed here → notification dispatched) is only testable after US-07 is unblocked. That round-trip test belongs in `test-plan.md → Audit & Events`._
