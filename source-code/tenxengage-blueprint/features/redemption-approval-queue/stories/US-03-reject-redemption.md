---
id: US-03
title: "Reject redemption"
layers: ["BE", "FE"]
seed_id: "F-04.S-03"
touches_entities: ["RedemptionRequest"]
depends_on_stories: ["US-01", "US-02"]
---

# US-03: Reject redemption

## Description

**Actor:** CLIENT_ADMIN or ACTIVITY_APPROVER
**Trigger:** User clicks "Reject" on a row in the `ApprovalQueueTable`.

**Steps:**
1. User clicks "Reject" button on a PENDING_APPROVAL queue row
2. `RejectDialog` opens with a required text area labeled "Rejection reason"
3. Submit button is disabled until the text area contains at least 1 non-whitespace character
4. User enters reason → submit enabled → clicks "Reject"
5. `useRejectRedemption` fires `POST /api/v1/redemption/requests/{id}/reject` with `{ rejectionReason }`
6. On success: dialog closes; `['approval-queue']` query invalidates → item disappears; toast "Redemption rejected"
7. Reserved balance released back to partner wallet; Kafka event `redemption.rejected` published

**Expected outcome:** Redemption transitions to `CANCELLED`; `reviewedBy`, `reviewedAt`, `rejectionReason` all set on record. Partner notified via Kafka. Reserved balance released.

**Negative paths:**
- Submit button disabled with blank/whitespace reason
- 400 (blank reason at API level): toast "Rejection reason is required"
- 409 (concurrent action): toast "This redemption was just actioned by another approver. Please refresh the queue."
- 404 (not found / wrong tenant): toast "Redemption not found"

---

## Acceptance Criteria

- **AC-1:** `POST /api/v1/redemption/requests/{id}/reject` with non-blank `rejectionReason` (≤1000 chars) and valid JWT returns 200 + `RedemptionRequestDetailResponse` with `status=CANCELLED`, `rejectionReason` set, `reviewedBy` = callerId, `reviewedAt` non-null
- **AC-2:** Blank or missing `rejectionReason` → 400 with field-level error; FE submit button stays disabled until reason has ≥1 non-whitespace character
- **AC-3:** Redemption not in `PENDING_APPROVAL` state → 409 with message `"Redemption is not in PENDING_APPROVAL state"`
- **AC-4:** `id` not found or belongs to different tenant → 404 (never 403)
- **AC-5:** Audit row written: `action=REJECTED`, `resourceType=REDEMPTION_REQUEST`, `resourceId={id}`, `actorId=callerId`
- **AC-6:** FE: on success, `['approval-queue']` query invalidated → item disappears; `RejectDialog` closes; toast "Redemption rejected" shown

---

## Out of Scope

- Approve flow (US-02)
- Rejection reason exposed to the partner in the Kafka notification payload (partner receives notification only — reason is omitted from event per spec confidentiality rule)
- Return request rejection (F-06)

---

## Non-Functional Notes

- **Transaction boundary:** `WalletService.releaseReservedBalance()` is called **inside** the `@Transactional` boundary. If release throws, the entire transaction rolls back; status remains `PENDING_APPROVAL` (edge case #10 in spec).
- **Kafka publish:** out-of-transaction advisory emit after commit — `rejectionReason` is NOT included in the event payload (confidentiality: Kafka log retention).

---

## UI States

- [ ] **Submit disabled:** "Reject" button disabled when `rejectionReason.trim().length === 0` — no error text shown until first submit attempt
- [ ] **Loading (dialog submit):** "Reject" button shows spinner while POST in flight; disabled to prevent double-submit; Cancel also disabled
- [ ] **Error (400):** Toast "Rejection reason is required" — dialog stays open
- [ ] **Error (409):** Toast "This redemption was just actioned by another approver. Please refresh the queue." — dialog closes
- [ ] **Error (404):** Toast "Redemption not found" — dialog closes
- [ ] **Error (5xx):** Toast "Something went wrong. Please try again." — dialog stays open for retry

### Verbatim microcopy

- Dialog title: "Reject redemption"
- Text area label: "Rejection reason"
- Text area placeholder: "Enter reason for rejection..."
- Text area helper: "Required. Max 1000 characters."
- Submit button: "Reject"
- Cancel button: "Cancel"
- Success toast: "Redemption rejected"
- 400 toast: "Rejection reason is required"
- 409 toast: "This redemption was just actioned by another approver. Please refresh the queue."
- 404 toast: "Redemption not found"
- 5xx toast: "Something went wrong. Please try again."

### Conditional rendering

**Input: `rejectionReason` field value**
- Empty or whitespace only: "Reject" submit button disabled
- ≥1 non-whitespace character: "Reject" submit button enabled
- > 1000 characters: "Reject" submit button disabled; character count indicator shown (e.g., "1001/1000")

**Input: mutation state**
- `idle`: Reject button state driven by field value; Cancel enabled
- `pending`: Reject button disabled + spinner; Cancel disabled
- `error (400)`: dialog stays open; toast shown; Reject button re-enabled when field corrected
- `error (409 or 404)`: dialog auto-closes; toast shown
- `error (5xx)`: dialog stays open; toast shown; Reject button re-enabled

---

## Depends on

- **Foundation tasks:** F1, F2, F3, F4
- **Prior stories:** US-01 BE (controller + service class must exist to add `rejectRedemption` method); US-02 BE (completes the controller — US-03 BE adds the reject endpoint to the same class)
- **FE:** US-01 FE (queue page + table with Reject button); US-03 FE can run in parallel with US-02 FE

---

## Spec references

- `spec.md → ## Functional Requirements` — FR-04.4, FR-04.5
- `spec.md → ## API Endpoints [BE + FE]` — `POST /{id}/reject` row
- `spec.md → ## DTOs [BE]` — `RejectRedemptionRequest` validation; `RedemptionRequestDetailResponse` extended fields
- `spec.md → ## Service Layer [BE]` — `rejectRedemption()` business rules; wallet release; Kafka emit
- `spec.md → ## Workflow / Status Transitions` — `PENDING_APPROVAL → CANCELLED`; pessimistic lock
- `spec.md → ## Audit Trail [BE]` — `REJECTED / REDEMPTION_REQUEST` annotation
- `spec.md → ## Domain Events [BE]` — `redemption.rejected`; rejection reason NOT in payload
- `spec.md → ## Security Design [BE]` — `rejectionReason` input validation; cross-tenant 404
- `spec.md → ## Edge Cases` — #5 (reject without reason), #8 (notification failure), #10 (wallet release failure)
- `technical.md → ## Package Layout [BE]` — `RejectRedemptionRequest.java` path; service + controller paths
- `technical.md → ## Package Layout [FE]` — hook + component file paths
- `technical.md → ## Repository Queries [BE]` — `findByIdAndClientIdForUpdate` (reused)
- `technical.md → ## Audit Annotations [BE]`

---

## BE tasks [BE]

### BE-1: Request DTO

**File:** `src/main/java/com/tenxengage/app/dto/request/redemption/RejectRedemptionRequest.java`

```java
public record RejectRedemptionRequest(
    @NotBlank @Size(max = 1000) String rejectionReason
) {}
```

Returns 400 with field-level error `{ field: "rejectionReason", message: "must not be blank" }` if blank or null.

See `spec.md → ## DTOs [BE] → Request DTOs`.

### BE-2: Service method + unit test

**Files:** `src/main/java/com/tenxengage/app/service/redemption/RedemptionApprovalService.java` (add method), `src/test/java/com/tenxengage/app/service/redemption/RedemptionApprovalServiceTest.java`

`rejectRedemption(UUID redemptionId, String rejectionReason, UUID approverId)` — `@Transactional`:
1. Resolve `clientId` from `tenantValidator.getCurrentClientId()`
2. `findByIdAndClientIdForUpdate(redemptionId, clientId)` → 404 if empty
3. Assert `status == PENDING_APPROVAL` → throw `BusinessRuleException("Redemption is not in PENDING_APPROVAL state")` → 409
4. Set `reviewedBy = approverId`, `reviewedAt = Instant.now()`, `rejectionReason = rejectionReason`, `status = CANCELLED`
5. Call `walletService.releaseReservedBalance(redemptionRequest)` — inside transaction; throws rolls back all
6. Save entity
7. **After commit:** publish `redemption.rejected` via `notificationEventProducer` (rejectionReason NOT in payload); catch publish exception, log WARN, do NOT rethrow

Unit test coverage (Mockito):
- Happy path: entity saved with CANCELLED status + all rejection fields set; `releaseReservedBalance` called
- State guard: non-PENDING_APPROVAL → `BusinessRuleException`; no wallet release
- Cross-tenant: `findByIdAndClientIdForUpdate` returns empty → 404
- Producer fires with `notificationTypeKey="redemption.rejected"` and `rejectionReason` absent from payload
- Wallet release failure: `releaseReservedBalance` throws → transaction rolls back; entity not saved

### BE-3: Controller endpoint + @WebMvcTest

**File:** `src/main/java/com/tenxengage/app/controller/redemption/RedemptionApprovalController.java` (add method), `src/test/java/com/tenxengage/app/controller/redemption/RedemptionApprovalControllerTest.java`

```
@PostMapping("/{id}/reject")
@RequiresPermission("action.redemption.approve")
@Audited(action = AuditAction.REJECTED, resourceType = AuditResourceType.REDEMPTION_REQUEST,
         description = "Rejected redemption request")
public ResponseEntity<RedemptionRequestDetailResponse> rejectRedemption(
    @PathVariable UUID id,
    @RequestBody @Valid RejectRedemptionRequest request)
```

@WebMvcTest coverage:
- 200 happy path: returns `RedemptionRequestDetailResponse` with `status=CANCELLED`, `rejectionReason` set _(AC-1)_
- 400: blank `rejectionReason` → field-level error _(AC-2)_
- 400: missing request body _(AC-2)_
- 409: mock service throws `BusinessRuleException` _(AC-3)_
- 404: mock service throws `ResourceNotFoundException` _(AC-4)_
- 403: no `action.redemption.approve` permission _(AC-4)_
- 401: no JWT _(AC-4)_

### BE-4: Audit annotation

`@Audited(action = AuditAction.REJECTED, resourceType = AuditResourceType.REDEMPTION_REQUEST, description = "Rejected redemption request")` on `rejectRedemption` controller method.

See `technical.md → ## Audit Annotations [BE]`.

---

## FE tasks [FE]

### FE-1: TypeScript types + service call

**Files:** `src/types/redemption/redemption.types.ts` (extend if needed), `src/services/redemption/redemption-approval.service.ts` (add method)

Copy `RejectRedemptionRequest` type from `../tenxengage-contracts/` — do not hand-write.

Add `reject(redemptionId: string, body: RejectRedemptionRequest): Promise<RedemptionRequestDetailResponse>` to service.

### FE-2: Mutation hook

**File:** `src/hooks/redemption/useRedemptionApproval.ts` (add `useRejectRedemption`)

```ts
export function useRejectRedemption() {
  return useMutation({
    mutationFn: ({ redemptionId, rejectionReason }: { redemptionId: string; rejectionReason: string }) =>
      redemptionApprovalService.reject(redemptionId, { rejectionReason }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['approval-queue'] }),
  })
}
```

See `technical.md → ## Hook Specs [FE] → useRejectRedemption`.

### FE-3: RejectDialog component + test

**Files:** `src/components/redemption/RejectDialog.tsx`, `src/components/redemption/__tests__/RejectDialog.test.tsx`

Props: `redemptionId: string, open: boolean, onOpenChange: (open: boolean) => void`

Uses shadcn `<Dialog>` + `<Textarea>` (both exist in `src/components/ui/`). Form managed with `react-hook-form` + zod schema `rejectRedemptionSchema`:
```ts
const rejectRedemptionSchema = z.object({
  rejectionReason: z.string().min(1, 'Required').max(1000)
})
```

Calls `useRejectRedemption()`. Submit button disabled when `rejectionReason.trim().length === 0` OR `isPending`. Character counter shown (`{count}/1000`). Specific toast messages per error code. Auto-closes on success or 409/404; stays open on 5xx and 400.

Vitest tests:
- Renders dialog with textarea and both buttons _(AC-6)_
- Submit button disabled when reason empty _(AC-2)_
- Submit button enabled after typing reason _(AC-2)_
- Submitting calls mutation with correct `{ redemptionId, rejectionReason }` _(AC-1)_
- Cancel closes dialog without calling mutation _(AC-6)_
- Shows loading state on submit _(UI states)_
- `rejectionReason > 1000 chars` → submit disabled _(AC-2)_

### FE-4: Wire Reject button in ApprovalQueueTable

**File:** `src/components/redemption/ApprovalQueueTable.tsx` (modify from US-01)

Replace stub `onReject` handler with state that opens `RejectDialog` for the selected row id. Render `<RejectDialog>` inside the table component (or page, consistent with `<ApproveConfirmDialog>` placement).

---

## E2E test [FE]

**File:** `e2e/redemption-approval-queue.spec.ts` (extend existing file)

---

**Scenario 1:** `'reject redemption happy path — item disappears from queue'` _(covers AC-1, AC-5, AC-6)_

| Field | Value |
|---|---|
| **User flow** | Navigate to queue → click "Reject" on first row → `RejectDialog` opens → type "Duplicate request" → click "Reject" → toast → item gone |
| **APIs to mock via `page.route()`** | `GET /approval-queue` → 200 + 1 item; `POST /api/v1/redemption/requests/{id}/reject` → 200 + `RedemptionRequestDetailResponse` with `status=CANCELLED`; second GET → 200 + 0 items |
| **Visible assertion** | `expect(page.getByText('Redemption rejected')).toBeVisible()`; `expect(page.getByText('No pending redemptions')).toBeVisible()` |
| **Negative case** | — |

---

**Scenario 2:** `'reject dialog submit disabled with blank reason'` _(covers AC-2, AC-6)_

| Field | Value |
|---|---|
| **User flow** | Click Reject → dialog opens → submit button disabled; type whitespace only → still disabled |
| **APIs to mock via `page.route()`** | None needed (purely FE) |
| **Visible assertion** | `expect(page.getByRole('button', { name: 'Reject' })).toBeDisabled()` |
| **Negative case** | Enter whitespace `"   "` → button remains disabled |

---

**Scenario 3:** `'reject concurrent 409 — shows specific toast'` _(covers AC-3)_

| Field | Value |
|---|---|
| **User flow** | Click Reject → type reason → click Reject → 409 → toast → dialog closes |
| **APIs to mock via `page.route()`** | `POST /api/v1/redemption/requests/{id}/reject` → 409 + `{ message: "Redemption is not in PENDING_APPROVAL state" }` |
| **Visible assertion** | `expect(page.getByText('This redemption was just actioned by another approver. Please refresh the queue.')).toBeVisible()` |
| **Negative case** | Dialog closed after 409 |

---

## Execution checklist

**BE session:**
- [ ] `RejectRedemptionRequest.java` record created with `@NotBlank @Size(max=1000)` on `rejectionReason` _(AC-2)_
- [ ] `RedemptionApprovalService.rejectRedemption()` method added: pessimistic lock, state guard, set fields, release balance, save _(AC-1, AC-3, AC-4)_
- [ ] Kafka `redemption.rejected` published after commit (rejectionReason absent from payload) _(FR-04.5)_
- [ ] `RedemptionApprovalServiceTest` unit tests pass: happy, state guard, cross-tenant 404, producer fires without reason, wallet release rollback _(AC-1, AC-3, AC-4)_
- [ ] `POST /{id}/reject` added to `RedemptionApprovalController` with `@RequiresPermission` + `@Audited(REJECTED, REDEMPTION_REQUEST)` _(AC-1, AC-5)_
- [ ] `RedemptionApprovalControllerTest` @WebMvcTest passes: 200, 400 (blank), 409, 404, 403, 401 _(AC-1, AC-2, AC-3, AC-4)_

**FE session:**
- [ ] `RejectRedemptionRequest` TypeScript type added from contracts
- [ ] `reject()` call added to `redemption-approval.service.ts`
- [ ] `useRejectRedemption` mutation hook created; `onSuccess` invalidates `['approval-queue']` _(AC-6)_
- [ ] `RejectDialog` component created: textarea, zod validation, submit disabled when empty, character counter, toast messages per error code _(AC-2, AC-6)_
- [ ] `RejectDialog.test.tsx` Vitest tests pass: renders, disabled when empty, enabled after input, calls mutation, cancel, loading, max-length disabled _(AC-2, AC-6)_
- [ ] Reject button in `ApprovalQueueTable` wired to open `RejectDialog` _(AC-6)_
- [ ] E2E Scenario 1 passes: happy path _(AC-1, AC-5, AC-6)_
- [ ] E2E Scenario 2 passes: submit disabled with blank reason _(AC-2)_
- [ ] E2E Scenario 3 passes: 409 toast _(AC-3)_

---

## Done when

1. **BE:** `./gradlew test` passes — `RedemptionApprovalServiceTest` (reject scenarios) + `RedemptionApprovalControllerTest` (reject endpoint) all green
2. **FE:** `npm run test` passes + `npx playwright test e2e/redemption-approval-queue.spec.ts -g 'reject'` passes against real BE
3. Every AC (AC-1 through AC-6) is referenced by at least one passing test
