---
id: US-04
title: RETURN_TIMED_OUT and manual resolution
layers: ["BE", "FE"]
touches_entities: ["RedemptionReturn"]
depends_on_stories: ["US-02"]
seed_id: "S-05"
---

# US-04: RETURN_TIMED_OUT and manual resolution

## Description

**As a** CLIENT_ADMIN or ACTIVITY_APPROVER,
**When** an approved return has been waiting for Xoxoday's webhook for 7 days (transitioned to `RETURN_TIMED_OUT` by the Foundation F5 scheduler),
**I want to** manually resolve the timed-out return by either confirming it (credits the partner's wallet) or rejecting it (closes without credit),
**So that** partners are never left in indefinite limbo when Xoxoday is unresponsive.

**Context:** The `ReturnTimeoutScheduler` (Foundation F5) marks `APPROVED` returns as `RETURN_TIMED_OUT` at T+7d. This story adds the admin UI and endpoint to act on those timed-out returns.

**Flow:**
1. Admin sees a `RETURN_TIMED_OUT` return in `ReturnsApprovalTab` (the "Resolve" action appears in the row kebab for this status).
2. Admin clicks "Resolve" → `ResolveTimedOutDialog` opens.
3. Admin selects radio: "Confirm return (credit wallet)" or "Reject return (no credit)"; optionally enters notes (max 1000 chars).
4. Admin submits → `POST /api/v1/redemption/admin/returns/{id}/resolve` with `ResolveTimedOutReturnRequest`.
5. **CONFIRM path:** `WalletMutationDelegate.doReturnCreditInTx()` called → `RETURN_CREDIT` ledger entry written; balance restored; return transitions to `RETURN_CONFIRMED`; `RETURN_CONFIRMED` event published; partner notified.
6. **REJECT path:** return transitions to `RETURN_REJECTED`; no wallet credit; `RETURN_REJECTED` event published; partner notified.
7. Negative path: `POST /resolve` on a non-`RETURN_TIMED_OUT` return returns `409`.

---

## Acceptance Criteria

- **AC-1** `POST /admin/returns/{id}/resolve` with `resolution = CONFIRM` calls `WalletMutationDelegate.doReturnCreditInTx()`, transitions return to `RETURN_CONFIRMED`, sets `confirmedAt`, publishes `RETURN_CONFIRMED` event, and returns `200` with `ReturnDetailResponse`.
- **AC-2** `POST /admin/returns/{id}/resolve` with `resolution = REJECT` transitions return to `RETURN_REJECTED`, sets `rejectedAt`, does NOT call `doReturnCreditInTx()`, publishes `RETURN_REJECTED` event, and returns `200` with `ReturnDetailResponse`.
- **AC-3** `POST /admin/returns/{id}/resolve` returns `409` when the return status is not `RETURN_TIMED_OUT`; returns `404` for wrong tenant.
- **AC-4** `doReturnCreditInTx()` idempotency: if a Xoxoday webhook arrives concurrently with admin resolve and both attempt to call `doReturnCreditInTx()`, the second call is a no-op — no double-credit. The `@Version` optimistic lock on `RedemptionReturn` also prevents simultaneous transition races.
- **AC-5** FE — `ResolveTimedOutDialog`: radio group with two options ("Confirm return (credit wallet)" / "Reject return (no credit)"); optional notes textarea (max 1000 chars); Resolve button enabled only when a radio option is selected. On success: dialog closes; admin queue and detail caches invalidated.
- **AC-6** FE — "Resolve" action visible in `ReturnsApprovalTab` row kebab and in `ReturnDetailSheet` admin panel only when `status = RETURN_TIMED_OUT` + caller has `action.redemption.return.review`.

---

## Out of Scope

- The `ReturnTimeoutScheduler` itself — Foundation F5
- Xoxoday retry logic and DLQ — US-03
- Partner cancel — US-01
- Admin approve / reject of PENDING_APPROVAL — US-02

---

## UI States

### `ResolveTimedOutDialog`
| State | Behavior |
|---|---|
| No radio selected | Resolve button disabled |
| Radio selected, no notes | Resolve button enabled |
| Submitting | Resolve button shows spinner; radio + notes disabled |
| Error | Inline error in dialog (stays open) |
| Success | Dialog closes; admin queue cache invalidated |

### `ReturnDetailSheet` — admin resolve extension
| Return status | Resolve button |
|---|---|
| `RETURN_TIMED_OUT` + review permission | Resolve button visible |
| All other statuses | Resolve button hidden |

---

### Verbatim microcopy

| Surface | String |
|---|---|
| Row action (kebab) | "Resolve" |
| Dialog title | "Resolve Timed-Out Return" |
| Dialog subtitle | "This return has been waiting for Xoxoday confirmation for more than 7 days." |
| Radio option — confirm | "Confirm return (credit wallet)" |
| Radio option — reject | "Reject return (no credit)" |
| Notes label | "Notes (optional)" |
| Notes placeholder | "Add a note about your resolution decision…" |
| Notes char counter | "{n}/1000" |
| Resolve button | "Resolve Return" |
| Cancel button | "Cancel" |

---

### Conditional rendering

| Condition | Behavior |
|---|---|
| `status = RETURN_TIMED_OUT` + review permission | "Resolve" in `ReturnsApprovalTab` row kebab |
| `status = RETURN_TIMED_OUT` + review permission | Resolve button in `ReturnDetailSheet` admin panel |
| `status ≠ RETURN_TIMED_OUT` | Resolve action/button hidden |
| No radio selected | Resolve button disabled in dialog |

---

## Depends on

- **F5 done** — `ReturnTimeoutScheduler` must be in place so `RETURN_TIMED_OUT` returns exist; `ReturnEventProducer.publishReturnConfirmed()` and `publishReturnRejected()` must exist
- **US-02 BE done** — `ReturnAdminController` exists and `resolveTimedOut()` method is added to it
- **US-02 FE done** — `ReturnDetailSheet` admin panel and `ReturnsApprovalTab` kebab must exist before extending with Resolve action
- **US-03 done** — `WalletMutationDelegate.doReturnCreditInTx()` called in the same idempotency context

---

## Spec References

- FR-06.12 (RETURN_TIMED_OUT + resolve)
- `spec.md → ## Service Layer → ReturnService.resolveTimedOut()`
- `spec.md → ## API Endpoints → Admin — Return Review → POST /{id}/resolve`
- `spec.md → ## Workflow / Status Transitions → RETURN_TIMED_OUT → RETURN_CONFIRMED | RETURN_REJECTED`
- `spec.md → ## Frontend Specification → ResolveTimedOutDialog`
- `spec.md → ## Edge Cases → 6, 7`
- `technical.md → ## Package Layout [FE] → ResolveTimedOutDialog, useResolveTimedOutReturn`

---

## BE Tasks

### BE-1: DTOs

- [ ] `ResolveTimedOutReturnRequest` record: `resolution (ReturnResolution)`, `notes (String, nullable)` — `@NotNull` on `resolution`; `@Size(max=1000)` on `notes`; `@ValidEnum(ReturnResolution.class)` on `resolution`

### BE-2: Service + unit test

- [ ] `ReturnService.resolveTimedOut(id, resolution, notes, reviewerId, clientId)` annotated `@Transactional`:
  - Fetch via `findByIdForUpdate(id)` for `PESSIMISTIC_WRITE` locking (race guard)
  - Validate `RETURN_TIMED_OUT` state → 409 otherwise
  - `CONFIRM` path: call `WalletMutationDelegate.doReturnCreditInTx(return)`, set `status=RETURN_CONFIRMED`, set `confirmedAt`, set `reviewedBy`, set `reviewNotes`, save, publish `RETURN_CONFIRMED` event via `ReturnEventProducer`
  - `REJECT` path: set `status=RETURN_REJECTED`, set `rejectedAt`, set `reviewedBy`, set `reviewNotes`, save, publish `RETURN_REJECTED` event via `ReturnEventProducer`
  - Return `ReturnDetailResponse` in both cases
- [ ] `ReturnServiceTest` additions:
  - resolveTimedOut CONFIRM → `doReturnCreditInTx()` called, status RETURN_CONFIRMED
  - resolveTimedOut REJECT → `doReturnCreditInTx()` NOT called, status RETURN_REJECTED
  - resolveTimedOut on non-TIMED_OUT → 409
  - resolveTimedOut on wrong tenant → 404

### BE-3: Controller + `@WebMvcTest`

- [ ] Add `POST /{id}/resolve` to `ReturnAdminController`:
  - Body: `ResolveTimedOutReturnRequest`
  - Response: `200` with `ReturnDetailResponse`
  - `@RequiresPermission("action.redemption.return.review")`
  - Rate limit: shared 30 req/min per admin user (same pool as approve + reject)
- [ ] `ReturnAdminControllerTest` additions:
  - Resolve CONFIRM → 200
  - Resolve REJECT → 200
  - Resolve 409 wrong state
  - Resolve 400 missing `resolution`

### BE-4: Audit

- [ ] `@Audited(action = COMPLETED, resourceType = REDEMPTION_RETURN, description = "Manually confirmed timed-out return")` on resolve CONFIRM path
- [ ] `@Audited(action = REJECTED, resourceType = REDEMPTION_RETURN, description = "Manually rejected timed-out return")` on resolve REJECT path

---

## FE Tasks

### FE-1: Types + service

- [ ] `ResolveTimedOutReturnRequest` type (in types file): `resolution: 'CONFIRM' | 'REJECT'`, `notes?: string`
- [ ] Add `resolveTimedOut(id, dto)` to `src/services/redemption-returns.service.ts`

### FE-2: Hook

- [ ] `useResolveTimedOutReturn()` — mutation; on success: invalidates `['admin-returns', ...]` and `['return', id, true]`

### FE-3: Components + Vitest

- [ ] `ResolveTimedOutDialog.tsx` — props: `returnId`, `onSuccess`; radio group (`z.enum(['CONFIRM', 'REJECT'])`); optional notes textarea (`z.string().max(1000).optional()`); Resolve button disabled until radio selected; loading spinner on submit; inline error on failure; uses `useResolveTimedOutReturn()`
- [ ] Extend `ReturnsApprovalTab.tsx` — add "Resolve" to row kebab for `RETURN_TIMED_OUT` rows → opens `ResolveTimedOutDialog`
- [ ] Extend `ReturnDetailSheet.tsx` — add Resolve button to admin-action panel gated on `RETURN_TIMED_OUT` status + review permission → opens `ResolveTimedOutDialog`
- [ ] Vitest test: `ResolveTimedOutDialog.test.tsx` — submit disabled when no radio; enabled when radio selected; CONFIRM and REJECT radio options render

### FE-4: No new page wiring

No new routes or page-level changes needed — Resolve is an extension of existing `ReturnsApprovalTab` and `ReturnDetailSheet` (both from US-02).

---

## E2E Scenarios

| File | Scenario | AC coverage |
|---|---|---|
| `e2e/redemption-returns/resolve-timed-out.spec.ts` | Admin sees RETURN_TIMED_OUT return → clicks Resolve → selects Confirm → submits → return shows RETURN_CONFIRMED; wallet balance increased | AC-1, AC-5, AC-6 |
| `e2e/redemption-returns/resolve-timed-out.spec.ts` | Admin resolves with Reject → return shows RETURN_REJECTED; wallet balance unchanged | AC-2 |

---

## Execution Checklist

- [ ] BE-1: Write `ResolveTimedOutReturnRequest` DTO
- [ ] BE-2: Write `ReturnService.resolveTimedOut()` (CONFIRM and REJECT paths) + extend `ReturnServiceTest`
- [ ] BE-3: Add `POST /{id}/resolve` to `ReturnAdminController` + extend `ReturnAdminControllerTest`
- [ ] BE-4: Add `@Audited` on resolve CONFIRM (COMPLETED) and REJECT (REJECTED) paths
- [ ] FE-1: Add `resolveTimedOut()` to service + `ResolveTimedOutReturnRequest` type
- [ ] FE-2: Write `useResolveTimedOutReturn` mutation hook
- [ ] FE-3: Write `ResolveTimedOutDialog` + Vitest test
- [ ] FE-3: Extend `ReturnsApprovalTab` — add "Resolve" to RETURN_TIMED_OUT row kebab
- [ ] FE-3: Extend `ReturnDetailSheet` — add Resolve button in admin panel for RETURN_TIMED_OUT
- [ ] Run `./gradlew test` — all tests pass
- [ ] Run `npm run test` — all Vitest tests pass
- [ ] Update `tracker.md` — set US-04 BE and FE status to `done`

---

## Done When

- [ ] `POST /admin/returns/{id}/resolve` with CONFIRM → `RETURN_CONFIRMED` + wallet credit called; 409 for non-TIMED_OUT
- [ ] `POST /admin/returns/{id}/resolve` with REJECT → `RETURN_REJECTED` + no wallet credit
- [ ] `doReturnCreditInTx()` idempotency holds under concurrent resolve + late webhook (unit test verifies)
- [ ] `ResolveTimedOutDialog` renders radio options; Resolve disabled until selection; success closes dialog
- [ ] "Resolve" appears in `ReturnsApprovalTab` kebab and `ReturnDetailSheet` only for `RETURN_TIMED_OUT` returns
- [ ] Audit records written for CONFIRM (COMPLETED) and REJECT (REJECTED)
- [ ] `./gradlew test` passes; `npm run test` passes
