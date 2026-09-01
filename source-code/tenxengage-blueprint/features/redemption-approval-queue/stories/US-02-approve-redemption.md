---
id: US-02
title: "Approve redemption"
layers: ["BE", "FE"]
seed_id: "F-04.S-02"
touches_entities: ["RedemptionRequest"]
depends_on_stories: ["US-01"]
---

# US-02: Approve redemption

## Description

**Actor:** CLIENT_ADMIN or ACTIVITY_APPROVER
**Trigger:** User clicks "Approve" on a row in the `ApprovalQueueTable`.

**Steps:**
1. User clicks "Approve" button on a PENDING_APPROVAL queue row
2. `ApproveConfirmDialog` opens: "Approve this redemption?" with Approve + Cancel buttons
3. User clicks "Approve" → `useApproveRedemption` fires `POST /api/v1/redemption/requests/{id}/approve`
4. On success: dialog closes; `['approval-queue']` query invalidates → item disappears from table; toast "Redemption approved"
5. Vendor routing initiated by BE (calls `RedemptionRoutingService`); Kafka event published

**Expected outcome:** Redemption transitions to `RESERVED`; `reviewedBy` + `reviewedAt` set; `RedemptionRequestDetailResponse` extended with approval fields returned. Partner is notified via `redemption.approved` Kafka event.

**Negative paths:**
- 409 (concurrent approval): toast "This redemption was just actioned by another approver. Please refresh the queue."
- 404 (not found / wrong tenant): toast "Redemption not found"
- Vendor routing failure: entire transaction rolls back; redemption stays `PENDING_APPROVAL`; approver sees 500

---

## Acceptance Criteria

- **AC-1:** `POST /api/v1/redemption/requests/{id}/approve` with valid `action.redemption.approve` JWT returns 200 + `RedemptionRequestDetailResponse` with `status=RESERVED`, `reviewedBy` = caller's user UUID, `reviewedAt` non-null, `rejectionReason` null
- **AC-2:** Audit row written: `action=APPROVED`, `resourceType=REDEMPTION_REQUEST`, `resourceId={id}`, `actorId=callerId`
- **AC-3:** Redemption not in `PENDING_APPROVAL` state → 409 with message `"Redemption is not in PENDING_APPROVAL state"`
- **AC-4:** `id` not found or belongs to different tenant → 404 (never 403)
- **AC-5:** `RedemptionRequestDetailResponse` includes `reviewedBy: UUID`, `reviewedAt: Instant`, `rejectionReason: String | null` (additive change — existing fields unchanged)
- **AC-6:** FE: on success, `['approval-queue']` query invalidated → item disappears from table; toast "Redemption approved" shown; dialog closes

---

## Out of Scope

- Reject flow (US-03)
- Kafka event round-trip consumer test (test-plan.md T1 — Mockito unit test here verifies producer fires)
- Approver notification when new item enters queue (Phase 2)
- Quorum / multi-approver (ADR-01 deferred to Phase 2)

---

## Non-Functional Notes

- **Transaction boundary:** `RedemptionRoutingService.routeApprovedRedemption()` is called **inside** the `@Transactional` boundary. If routing throws, the entire transaction rolls back and `PENDING_APPROVAL` status is preserved (edge case #9 in spec). Kafka publish happens **after** commit (out-of-transaction advisory).
- **Pessimistic lock:** `findByIdAndClientIdForUpdate` acquires `PESSIMISTIC_WRITE` lock before state check — prevents concurrent double-approval at DB level. `@Version` optimistic lock provides secondary defense.

---

## UI States

- [ ] **Loading (dialog submit):** "Approve" button shows loading spinner while POST is in flight; disabled to prevent double-submit
- [ ] **Error (409):** Toast "This redemption was just actioned by another approver. Please refresh the queue." — dialog closes
- [ ] **Error (404):** Toast "Redemption not found" — dialog closes
- [ ] **Error (5xx):** Toast "Something went wrong. Please try again." — dialog stays open for retry

### Verbatim microcopy

- Dialog title: "Approve this redemption?"
- Dialog description: "This will approve the redemption and initiate vendor processing."
- Confirm button: "Approve"
- Cancel button: "Cancel"
- Success toast: "Redemption approved"
- 409 toast: "This redemption was just actioned by another approver. Please refresh the queue."
- 404 toast: "Redemption not found"
- 5xx toast: "Something went wrong. Please try again."

### Conditional rendering

**Input: mutation state**
- `idle`: Approve button enabled, normal appearance
- `pending`: Approve button disabled + spinner; Cancel disabled
- `error (409 or 404)`: dialog auto-closes; toast shown
- `error (5xx)`: dialog stays open; Approve button re-enabled; toast shown

---

## Depends on

- **Foundation tasks:** F1, F2, F3, F4
- **Prior stories:** US-01 (RedemptionApprovalController and RedemptionApprovalService must exist; FE needs queue page to surface the Approve button)

---

## Spec references

- `spec.md → ## Functional Requirements` — FR-04.3, FR-04.5
- `spec.md → ## API Endpoints [BE + FE]` — `POST /{id}/approve` row
- `spec.md → ## DTOs [BE]` — `RedemptionRequestDetailResponse` extended fields
- `spec.md → ## Service Layer [BE]` — `approveRedemption()` business rules; routing service call; Kafka emit
- `spec.md → ## Workflow / Status Transitions` — `PENDING_APPROVAL → RESERVED`; pessimistic lock; concurrent handling
- `spec.md → ## Audit Trail [BE]` — `APPROVED / REDEMPTION_REQUEST` annotation
- `spec.md → ## Domain Events [BE]` — `redemption.approved` on `notification-events`; out-of-transaction emit
- `spec.md → ## Security Design [BE]` — A01 broken access control; cross-tenant 404
- `spec.md → ## Edge Cases` — #2 (concurrent), #3 (cross-tenant), #4 (approve already-rejected), #8 (notification failure), #9 (vendor routing failure)
- `technical.md → ## Package Layout [BE]` — controller + service file paths
- `technical.md → ## Package Layout [FE]` — hook + component file paths
- `technical.md → ## Repository Queries [BE]` — `findByIdAndClientIdForUpdate` JPQL
- `technical.md → ## Audit Annotations [BE]`

---

## BE tasks [BE]

### BE-1: Extend RedemptionRequestDetailResponse

**File:** `src/main/java/com/tenxengage/app/dto/response/RedemptionRequestDetailResponse.java` (modify existing F-03 file)

Add 3 nullable fields to the existing record: `UUID reviewedBy`, `Instant reviewedAt`, `String rejectionReason`.

Update `from(RedemptionRequest r)` static factory to map these fields (null-safe — null until decision made).

This is an additive, non-breaking change. Existing fields and their mappings are unchanged.

See `spec.md → ## Modified Existing Endpoints [BE + FE]`.

### BE-2: Service method + unit test

**Files:** `src/main/java/com/tenxengage/app/service/redemption/RedemptionApprovalService.java` (add method), `src/test/java/com/tenxengage/app/service/redemption/RedemptionApprovalServiceTest.java`

`approveRedemption(UUID redemptionId, UUID approverId)` — `@Transactional`:
1. Resolve `clientId` from `tenantValidator.getCurrentClientId()`
2. `findByIdAndClientIdForUpdate(redemptionId, clientId)` → 404 if empty
3. Assert `status == PENDING_APPROVAL` → throw `BusinessRuleException("Redemption is not in PENDING_APPROVAL state")` → 409
4. Set `reviewedBy = approverId`, `reviewedAt = Instant.now()`, `status = RESERVED`
5. Call `redemptionRoutingService.routeApprovedRedemption(redemptionRequest)` — inside transaction; throws rolls back all
6. Save entity
7. **After commit** (use `@TransactionalEventListener(AFTER_COMMIT)` or equivalent): publish `redemption.approved` via `notificationEventProducer`; catch publish exception, log WARN, do NOT rethrow

Unit test coverage (Mockito):
- Happy path: entity saved with RESERVED status + reviewedBy/At; `routeApprovedRedemption` called
- State guard: non-PENDING_APPROVAL → `BusinessRuleException` thrown; no routing call
- Cross-tenant: `findByIdAndClientIdForUpdate` returns empty → 404 thrown
- Notification producer fires with `notificationTypeKey="redemption.approved"` and correct `targetUserIds`
- Routing failure: `routeApprovedRedemption` throws → transaction rolls back; entity not saved

See `spec.md → ## Service Layer [BE]` business rules.

### BE-3: Controller endpoint + @WebMvcTest

**File:** `src/main/java/com/tenxengage/app/controller/redemption/RedemptionApprovalController.java` (add method), `src/test/java/com/tenxengage/app/controller/redemption/RedemptionApprovalControllerTest.java`

```
@PostMapping("/{id}/approve")
@RequiresPermission("action.redemption.approve")
@Audited(action = AuditAction.APPROVED, resourceType = AuditResourceType.REDEMPTION_REQUEST,
         description = "Approved redemption request")
public ResponseEntity<RedemptionRequestDetailResponse> approveRedemption(@PathVariable UUID id)
```

Resolve `approverId` from JWT inside method (via `SecurityContextHolder` or injected `UserContext`).

@WebMvcTest coverage:
- 200 happy path: returns `RedemptionRequestDetailResponse` with `status=RESERVED`, `reviewedBy` set _(AC-1, AC-5)_
- 409: mock service throws `BusinessRuleException` → response message "Redemption is not in PENDING_APPROVAL state" _(AC-3)_
- 404: mock service throws `ResourceNotFoundException` _(AC-4)_
- 403: no `action.redemption.approve` permission _(AC-4)_
- 401: no JWT _(AC-4)_

### BE-4: Audit annotation

`@Audited(action = AuditAction.APPROVED, resourceType = AuditResourceType.REDEMPTION_REQUEST, description = "Approved redemption request")` on `approveRedemption` controller method.

See `spec.md → ## Audit Trail [BE] → @Audited Annotation Details` and `technical.md → ## Audit Annotations [BE]`.

---

## FE tasks [FE]

### FE-1: Mutation hook

**File:** `src/hooks/redemption/useRedemptionApproval.ts`

Export `useApproveRedemption()`:
```ts
mutationFn: (redemptionId: string) => redemptionApprovalService.approve(redemptionId)
onSuccess: () => queryClient.invalidateQueries({ queryKey: ['approval-queue'] })
```

Add `approve(redemptionId: string)` call to `redemption-approval.service.ts`.

See `technical.md → ## Hook Specs [FE] → useApproveRedemption`.

### FE-2: ApproveConfirmDialog component + test

**Files:** `src/components/redemption/ApproveConfirmDialog.tsx`, `src/components/redemption/__tests__/ApproveConfirmDialog.test.tsx`

Props: `redemptionId: string, open: boolean, onOpenChange: (open: boolean) => void`

Uses shadcn `<Dialog>` (exists at `src/components/ui/dialog.tsx`). Calls `useApproveRedemption()` on confirm.

Renders: dialog title "Approve this redemption?", description, Cancel + Approve buttons. Approve button shows spinner while `isPending`. Auto-closes on success; shows toast "Redemption approved". On 409/404 shows specific toast and closes. On 5xx shows generic toast and stays open.

Vitest tests:
- Renders title and both buttons _(AC-6)_
- Clicking Approve calls mutation with correct redemptionId _(AC-1)_
- Cancel button closes dialog without calling mutation _(AC-6)_
- Shows loading state while pending _(UI states)_

### FE-3: Wire Approve button in ApprovalQueueTable

**File:** `src/components/redemption/ApprovalQueueTable.tsx` (modify from US-01)

Replace stub `onApprove` handler stub with state that opens `ApproveConfirmDialog` for the selected row id. Render `<ApproveConfirmDialog>` inside the table component (or page).

---

## E2E test [FE]

**File:** `e2e/redemption-approval-queue.spec.ts` (extend existing file from US-01)

---

**Scenario 1:** `'approve redemption happy path — item disappears from queue'` _(covers AC-1, AC-5, AC-6)_

| Field | Value |
|---|---|
| **User flow** | Navigate to queue → click "Approve" on first row → `ApproveConfirmDialog` opens → click "Approve" → toast visible → item gone from table |
| **APIs to mock via `page.route()`** | `GET /approval-queue` → 200 + 1 item; `POST /api/v1/redemption/requests/{id}/approve` → 200 + `RedemptionRequestDetailResponse` with `status=RESERVED`; second GET → 200 + 0 items |
| **Visible assertion** | `expect(page.getByText('Redemption approved')).toBeVisible()`; `expect(page.getByText('No pending redemptions')).toBeVisible()` |
| **Negative case** | — |

---

**Scenario 2:** `'approve concurrent 409 — shows specific toast'` _(covers AC-3)_

| Field | Value |
|---|---|
| **User flow** | Click Approve → confirm → 409 response → toast with exact message → dialog closed |
| **APIs to mock via `page.route()`** | `POST /api/v1/redemption/requests/{id}/approve` → 409 + `{ message: "Redemption is not in PENDING_APPROVAL state" }` |
| **Visible assertion** | `expect(page.getByText('This redemption was just actioned by another approver. Please refresh the queue.')).toBeVisible()` |
| **Negative case** | Dialog closed after 409 |

---

**Scenario 3:** `'approve 404 — shows not found toast'` _(covers AC-4)_

| Field | Value |
|---|---|
| **User flow** | Click Approve → confirm → 404 response → error toast → dialog closed |
| **APIs to mock via `page.route()`** | `POST /api/v1/redemption/requests/{id}/approve` → 404 |
| **Visible assertion** | `expect(page.getByText('Redemption not found')).toBeVisible()` |
| **Negative case** | — |

---

## Execution checklist

**BE session:**
- [ ] `RedemptionRequestDetailResponse` extended with `reviewedBy`, `reviewedAt`, `rejectionReason` fields + factory updated _(AC-5)_
- [ ] `RedemptionApprovalService.approveRedemption()` method added: pessimistic lock, state guard, set fields, call routing service, save _(AC-1, AC-3, AC-4)_
- [ ] Kafka `redemption.approved` published after commit via `NotificationEventProducer` _(FR-04.5)_
- [ ] `RedemptionApprovalServiceTest` unit tests pass: happy, state guard, cross-tenant 404, producer fires, routing rollback _(AC-1, AC-3, AC-4)_
- [ ] `POST /{id}/approve` added to `RedemptionApprovalController` with `@RequiresPermission` + `@Audited(APPROVED, REDEMPTION_REQUEST)` _(AC-1, AC-2)_
- [ ] `RedemptionApprovalControllerTest` @WebMvcTest passes: 200, 409, 404, 403, 401 _(AC-1, AC-3, AC-4)_

**FE session:**
- [ ] `approve()` call added to `redemption-approval.service.ts`
- [ ] `useApproveRedemption` mutation hook created; `onSuccess` invalidates `['approval-queue']` _(AC-6)_
- [ ] `ApproveConfirmDialog` component created with shadcn `<Dialog>`, loading state, toast messages _(AC-6)_
- [ ] `ApproveConfirmDialog.test.tsx` Vitest tests pass: renders, calls mutation, cancel, loading _(AC-1, AC-6)_
- [ ] Approve button in `ApprovalQueueTable` wired to open `ApproveConfirmDialog` _(AC-6)_
- [ ] E2E Scenario 1 passes: happy path _(AC-1, AC-5, AC-6)_
- [ ] E2E Scenario 2 passes: 409 toast _(AC-3)_
- [ ] E2E Scenario 3 passes: 404 toast _(AC-4)_

---

## Done when

1. **BE:** `./gradlew test` passes — `RedemptionApprovalServiceTest` (approve scenarios) + `RedemptionApprovalControllerTest` (approve endpoint) all green
2. **FE:** `npm run test` passes + `npx playwright test e2e/redemption-approval-queue.spec.ts -g 'approve'` passes against real BE
3. Every AC (AC-1 through AC-6) is referenced by at least one passing test
