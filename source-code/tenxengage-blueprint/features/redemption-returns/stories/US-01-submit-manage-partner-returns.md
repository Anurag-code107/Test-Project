---
id: US-01
title: Submit and manage partner returns
layers: ["BE", "FE"]
touches_entities: ["RedemptionReturn", "RedemptionRequest"]
depends_on_stories: []
seed_id: "S-01"
---

# US-01: Submit and manage partner returns

## Description

**As a** partner (PARTNER_SELLER or PARTNER_ADMIN),
**I want to** request a return for a completed non-cash (Xoxoday) redemption and manage my open return requests,
**So that** I can recover the balance for a reward that didn't meet expectations without contacting support.

**Flow:**
1. Partner views their redemption history (F-05). For each completed, non-cash, within-window, returnable redemption with no active return, `isReturnEligible = true` appears in the response.
2. FE renders a "Request Return" action in the row kebab for eligible rows.
3. Partner clicks "Request Return" → `RequestReturnDialog` opens pre-populated with item name, amount, and currency.
4. Partner optionally enters a reason (max 500 chars) → clicks Submit.
5. `POST /api/v1/redemption/returns` returns 201 with `ReturnDetailResponse`; return status is `PENDING_APPROVAL`.
6. Partner can view all their returns in the "My Returns" tab of the F-05 history page (`MyReturnsTab`).
7. From the returns list or detail sheet, partner can cancel a `PENDING_APPROVAL` return via `AlertDialog` confirmation → `DELETE /returns/{id}` → 204.
8. Negative paths: 422 for ineligible redemptions; 409 for duplicate active returns; 404 for cross-tenant access.

**Out of Scope for this story:** Admin review actions (US-02), Xoxoday notification (US-03), timeout resolution (US-04).

---

## Acceptance Criteria

- **AC-1** `POST /returns` with a valid `redemptionId` for an eligible completed non-cash redemption returns `201` with `ReturnDetailResponse`; `status` is `PENDING_APPROVAL`; `amount` is copied from the originating redemption (caller's amount value ignored per FR-06.10).
- **AC-2** `POST /returns` returns `422` when: redemption is XTRM/cash; redemption is not in `COMPLETED` status; catalog item has `isReturnable = false`; or the return window has expired. Returns `409` when a `PENDING_APPROVAL` or `APPROVED` return already exists for the same `redemptionId`.
- **AC-3** `GET /returns` returns `Page<ReturnSummaryResponse>` scoped to the calling partner user; returns from other partner users under the same tenant are NOT included. Supports `status`, `page`, `size` (max 50), `sort` (allowlist: `createdAt`, `amount`) query params.
- **AC-4** `GET /returns/{id}` returns `ReturnDetailResponse` for the caller's own return; returns `404` when the return belongs to a different partner user or a different tenant (never `403`).
- **AC-5** `DELETE /returns/{id}` transitions a `PENDING_APPROVAL` return to `CANCELLED` and returns `204`; returns `409` when status is not `PENDING_APPROVAL`; returns `404` when the return is not owned by the caller.
- **AC-6** F-05 `GET /api/v1/redemption/requests/personal` response includes `isReturnEligible (boolean)` field. The field is `true` only when all hold: redemption is `COMPLETED`, vendor is Xoxoday (non-cash), `isReturnable = true`, within the client-configured return window, and no active (`PENDING_APPROVAL` or `APPROVED`) return exists for that redemption.
- **AC-7** FE — `useReturn(id, isAdmin)`: `isAdmin = false` calls `GET /redemption/returns/{id}`; `isAdmin = true` calls `GET /redemption/admin/returns/{id}`; query key is `['return', id, isAdmin]` — cross-role cache collisions prevented.
- **AC-8** FE — `RequestReturnDialog`: "Request Return" appears in the actions/kebab of each redemption history row where `isReturnEligible = true`; rows with `isReturnEligible = false` do not show this action. Dialog validates reason max 500 chars; shows character counter from 400/500. Inline API error displayed inside dialog (dialog stays open on failure). On success: dialog closes and F-05 redemption history list invalidated.
- **AC-9** FE — `MyReturnsTab`: renders a table with columns Catalog Item, Amount, Status badge, Submitted, Actions. Empty state: "You have no return requests yet." PENDING_APPROVAL rows show "Cancel" in the row actions; clicking Cancel shows `AlertDialog` confirmation before calling `useCancelReturn()`.
- **AC-10** Audit — `SUBMITTED / REDEMPTION_RETURN` record written on successful `POST /returns`. `CANCELLED / REDEMPTION_RETURN` record written on successful `DELETE /returns/{id}`.

---

## Out of Scope

- Admin approve / reject — US-02
- Xoxoday notification and webhook processing — US-03
- RETURN_TIMED_OUT scheduler and resolve — US-04 (scheduler in Foundation F5)
- `ReturnDetailSheet` admin-action panel (Approve/Reject/Resolve buttons) — US-02 and US-04 extend the sheet
- Cash (XTRM) redemption returns — excluded from the platform entirely (FR-06.1)
- Partial returns — Phase 2 per spec Out of Scope

---

## Non-Functional Notes

`POST /returns` is gated at **5 req/min per tenant** (rate limit from spec Security Design). The service enforces `PESSIMISTIC_WRITE` locking on the originating `RedemptionRequest` during amount copy to prevent concurrent submit + cancel races.

---

## UI States

### `RequestReturnDialog`
| State | Behavior |
|---|---|
| Loading (submit in-flight) | Submit button shows spinner; disabled; Cancel enabled |
| Error | Inline error message below form (dialog stays open — no toast) |
| Success | Dialog closes; parent calls `onSuccess()` to invalidate F-05 list |

### `MyReturnsTab`
| State | Behavior |
|---|---|
| Loading | Skeleton table (5 rows) |
| Empty | "You have no return requests yet." (centered, subdued text) |
| Error | Inline retry button with "Failed to load return requests." message |
| Partial (cancel in-flight) | Row "Cancel" button shows spinner; disabled |

### `ReturnDetailSheet` (partner-role view)
| State | Behavior |
|---|---|
| Loading | Skeleton for header + timeline sections |
| Error | "Unable to load return details." with retry |
| PENDING_APPROVAL | Shows Cancel button in sheet footer |
| All other statuses | No action buttons in partner view |

---

### Verbatim microcopy

| Surface | String |
|---|---|
| Dialog title | "Request Return" |
| Dialog reason label | "Reason (optional)" |
| Dialog reason placeholder | "Describe why you're returning this item…" |
| Dialog character counter | "{n}/500" |
| Dialog submit button | "Submit Return Request" |
| Dialog cancel button | "Cancel" |
| Cancel row action label | "Cancel Return" |
| AlertDialog title | "Cancel this return request?" |
| AlertDialog body | "This return request will be cancelled. You can submit a new request for the same redemption later." |
| AlertDialog confirm button | "Yes, cancel it" |
| AlertDialog dismiss button | "Keep request" |
| MyReturnsTab empty state | "You have no return requests yet." |
| MyReturnsTab tab label | "My Returns" |
| Sheet cancel footer button | "Cancel Return" |

---

### Conditional rendering

| Condition | Behavior |
|---|---|
| `isReturnEligible = true` on redemption row | "Request Return" in kebab/actions; missing for `false` |
| `status = PENDING_APPROVAL` on MyReturnsTab row | "Cancel" action visible |
| `status ≠ PENDING_APPROVAL` on MyReturnsTab row | "Cancel" action hidden |
| `role = 'partner'` on `ReturnDetailSheet` | Admin action panel (Approve/Reject/Resolve) not rendered |
| `reason` is null or blank | Reason row shows "—" in detail sheet |

---

## Depends on

- **Foundation (all F1–F5)** — entity, repository, enums, and fixtures must be in place
- **F-03 merged** — `RedemptionRequest.COMPLETED` status, `WalletMutationDelegate`, and `LedgerEntryType.RETURN_CREDIT` must exist
- **F-05 merged** — `GET /api/v1/redemption/requests/personal` endpoint exists to add `isReturnEligible` field
- **Contracts generated** — F0 must be complete; `ReturnSummaryResponse`, `ReturnDetailResponse` types available in `../tenxengage-contracts/`

---

## Spec References

- FR-06.1 (eligibility), FR-06.2 (CTA visibility), FR-06.3 (submit + PENDING_APPROVAL), FR-06.8 (cancel), FR-06.10 (full amount), FR-06.11 (state machine)
- `spec.md → ## DTOs → ReturnSummaryResponse, ReturnDetailResponse`
- `spec.md → ## API Endpoints → Partner — Return Submission`
- `spec.md → ## Service Layer → ReturnService.submitReturn(), cancelReturn(), getPartnerReturns(), getReturnById()`
- `spec.md → ## Frontend Specification → RequestReturnDialog, MyReturnsTab, ReturnDetailSheet`
- `spec.md → ## Edge Cases → 1, 2, 3, 4, 11`
- `technical.md → ## Hook Specs → useMyReturns, useReturn`

---

## BE Tasks

### BE-1: DTOs

- [ ] `SubmitReturnRequest` record: `redemptionId (UUID)`, `reason (String, nullable)` — `@NotNull` on redemptionId, `@Size(max=500)` on reason
- [ ] `ReturnSummaryResponse` record: all rendered fields listed in `spec.md → ## DTOs`; static factory `from(RedemptionReturn, String catalogItemName)`
- [ ] `ReturnDetailResponse` record: all rendered fields; static factory `from(RedemptionReturn, String catalogItemName, String partnerDisplayName)`; `reviewNotes` and `vendorReturnReference` populated only when caller is admin — pass `null` for partner calls
- [ ] Modify `RedemptionRequestResponse` — add `isReturnEligible (boolean)` field; update `from()` factory signature to accept the computed boolean

### BE-2: Service + unit test

- [ ] `ReturnService.submitReturn(SubmitReturnRequest, userId, clientId)` — eligibility checks (422), duplicate check (409), amount copy, persist, publish `RETURN_REQUESTED` event via `ReturnEventProducer`, write audit; `@Transactional`
- [ ] `ReturnService.getPartnerReturns(userId, clientId, filters, pageable)` — calls `findByClientIdAndPartnerUserId`; maps to `ReturnSummaryResponse` with hydrated `catalogItemName`; `@Transactional(readOnly=true)`
- [ ] `ReturnService.getReturnById(id, userId, clientId, isAdmin)` — partner path: `findByIdAndClientIdAndPartnerUserId`; passes `null` for admin-only fields in factory call; `@Transactional(readOnly=true)`
- [ ] `ReturnService.cancelReturn(id, userId, clientId)` — ownership check via `findByIdAndClientIdAndPartnerUserId`; validates `PENDING_APPROVAL` state; transitions to `CANCELLED`; sets `cancelledAt`; publish `RETURN_CANCELLED` event; write audit; `@Transactional`
- [ ] `RedemptionRequestService` (or wherever `personal` list is built) — compute `isReturnEligible` per entry: `COMPLETED` + Xoxoday + `isReturnable=true` + within window + no active return; pass to `RedemptionRequestResponse.from()`
- [ ] `ReturnServiceTest` — unit tests for: submit happy path, XTRM 422, non-COMPLETED 422, non-returnable 422, expired window 422, duplicate active 409, cancel happy path, cancel wrong state 409, cancel wrong owner 404

### BE-3: Controller + `@WebMvcTest`

- [ ] `ReturnController` at `/api/v1/redemption/returns`:
  - `POST /` → `submitReturn()` → 201
  - `GET /` → `getPartnerReturns()` → 200 with `Page<ReturnSummaryResponse>`
  - `GET /{id}` → `getReturnById(isAdmin=false)` → 200
  - `DELETE /{id}` → `cancelReturn()` → 204
  - All endpoints annotated `@RequiresPermission("action.redemption.return.request")`
  - Rate limit: 5 req/min per tenant on `POST /`
- [ ] `ReturnControllerTest` (`@WebMvcTest`):
  - Submit 201 response shape
  - Submit 422 ineligible
  - Submit 409 duplicate
  - Cancel 204
  - Cancel 409 wrong state
  - List 200 with pagination
  - `@WithMockUser` + `@WithMockTenant` for each

### BE-4: Audit

- [ ] `@Audited(action = SUBMITTED, resourceType = REDEMPTION_RETURN, description = "Partner submitted return request")` on controller POST
- [ ] `@Audited(action = CANCELLED, resourceType = REDEMPTION_RETURN, description = "Partner cancelled return request")` on controller DELETE

---

## FE Tasks

### FE-1: Types + API service

- [ ] Copy `ReturnSummaryResponse`, `ReturnDetailResponse`, `SubmitReturnRequest` types from `../tenxengage-contracts/` into `src/types/redemption-returns.types.ts` — do NOT hand-write
- [ ] `src/services/redemption-returns.service.ts` — functions: `submitReturn(dto)`, `getMyReturns(filters, pageable)`, `getReturn(id, isAdmin)`, `cancelReturn(id)`

### FE-2: Query + mutation hooks

- [ ] `useMyReturns(filters)` — `queryKey: ['my-returns', userId, filters]`; `staleTime: 2 * 60 * 1000`; invalidated by `useSubmitReturn` and `useCancelReturn`
- [ ] `useReturn(id, isAdmin)` — `queryKey: ['return', id, isAdmin]`; `staleTime: 2 * 60 * 1000`; calls partner endpoint when `isAdmin=false`, admin endpoint when `isAdmin=true`; invalidated by `useCancelReturn`, `useApproveReturn`, `useRejectReturn`, `useResolveTimedOutReturn` for that `id`
- [ ] `useSubmitReturn()` — mutation; on success: invalidates `['my-returns', ...]` and parent F-05 history query (via `onSuccess` callback passed from parent)
- [ ] `useCancelReturn()` — mutation; on success: invalidates `['my-returns', ...]` and `['return', id, false]`

### FE-3: Components + Vitest

- [ ] `ReturnStatusBadge.tsx` — renders all 6 `ReturnStatus` values with correct Badge variants:
  - `PENDING_APPROVAL` → muted yellow
  - `APPROVED` → blue
  - `RETURN_CONFIRMED` → green
  - `RETURN_REJECTED` → red
  - `CANCELLED` → muted gray
  - `RETURN_TIMED_OUT` → orange/warning
- [ ] `RequestReturnDialog.tsx` — props: `redemptionId`, `amount`, `currencyId`, `catalogItemName`, `onSuccess`; reason textarea with `@size(max=500)` Zod; character counter from 400; inline error on failure; loading spinner on submit; uses `useSubmitReturn()`
- [ ] `MyReturnsTab.tsx` — status filter popover + date range; table columns: Catalog Item, Amount (formatted), Status badge, Submitted (relative time), Actions; row click → `ReturnDetailSheet`; PENDING_APPROVAL rows: Cancel action → AlertDialog → `useCancelReturn()`; loading skeleton; empty state; error inline retry
- [ ] `ReturnDetailSheet.tsx` — props: `returnId`, `role: 'partner' | 'admin'`; uses `useReturn(returnId, role === 'admin')`; sections: header (item name + status badge + amount), timeline (status timestamps with labels), return info (reason, admin notes if admin role, vendor ref if admin role); partner role: Cancel button for PENDING_APPROVAL; admin-action panel rendered only by US-02 extension; loading + error states
- [ ] Vitest tests: `RequestReturnDialog.test.tsx` — reason validation, character counter, submit success/error; `MyReturnsTab.test.tsx` (stub: renders table, empty state); `ReturnDetailSheet.test.tsx` — partner view hides admin panel, timeline renders status correctly

### FE-4: Page wiring

- [ ] Add "My Returns" tab to F-05 history page shell — render `<MyReturnsTab userId={currentUser.id} />` in new tab content
- [ ] In the F-05 redemption history list component: where `isReturnEligible = true`, add "Request Return" to the row kebab/actions dropdown → opens `<RequestReturnDialog redemptionId={...} amount={...} currencyId={...} catalogItemName={...} onSuccess={invalidateHistoryList} />`

---

## E2E Scenarios

| File | Scenario | AC coverage |
|---|---|---|
| `e2e/redemption-returns/submit-return.spec.ts` | Partner clicks "Request Return" on eligible row → fills reason → submits → sees return in My Returns tab as PENDING_APPROVAL | AC-1, AC-8, AC-9 |
| `e2e/redemption-returns/submit-return.spec.ts` | Partner tries "Request Return" on ineligible row → option not shown | AC-6 |
| `e2e/redemption-returns/cancel-return.spec.ts` | Partner cancels a PENDING_APPROVAL return → AlertDialog confirmation → return disappears from active view | AC-5, AC-9 |

---

## Execution Checklist

- [ ] BE-1: Write `SubmitReturnRequest`, `ReturnSummaryResponse`, `ReturnDetailResponse` DTOs; modify `RedemptionRequestResponse` to add `isReturnEligible`
- [ ] BE-2: Write `ReturnService` (submit, cancel, list, getById) + compute `isReturnEligible` in F-05 service + `ReturnServiceTest`
- [ ] BE-3: Write `ReturnController` (POST, GET /, GET /{id}, DELETE /{id}) + `ReturnControllerTest`
- [ ] BE-4: Add `@Audited` on POST (SUBMITTED) and DELETE (CANCELLED)
- [ ] FE-1: Copy types from contracts; write `redemption-returns.service.ts`
- [ ] FE-2: Write `useMyReturns`, `useReturn`, `useSubmitReturn`, `useCancelReturn` hooks
- [ ] FE-3: Write `ReturnStatusBadge` component
- [ ] FE-3: Write `RequestReturnDialog` + Vitest test
- [ ] FE-3: Write `MyReturnsTab` + Vitest test (stub)
- [ ] FE-3: Write `ReturnDetailSheet` (partner-role shell) + Vitest test
- [ ] FE-4: Wire `MyReturnsTab` as new "My Returns" tab in F-05 history page
- [ ] FE-4: Wire "Request Return" into F-05 redemption row kebab for `isReturnEligible = true` rows
- [ ] Run `./gradlew test` — all BE tests pass
- [ ] Run `npm run test` — all FE Vitest tests pass
- [ ] Update `tracker.md` — set US-01 BE and FE status to `done`

---

## Done When

- [ ] `POST /returns` returns 201 for eligible redemption; 422 for ineligible; 409 for duplicate active
- [ ] `GET /returns` returns only the calling partner's returns, paginated
- [ ] `GET /returns/{id}` returns 404 for another tenant's return
- [ ] `DELETE /returns/{id}` returns 204 for PENDING_APPROVAL; 409 for wrong state
- [ ] F-05 endpoint returns `isReturnEligible` on each redemption entry
- [ ] "Request Return" appears only for eligible rows in F-05 UI
- [ ] `RequestReturnDialog` submits and closes on success; stays open on error
- [ ] `MyReturnsTab` renders, filters, and cancel flow works end-to-end
- [ ] `ReturnStatusBadge` renders all 6 statuses
- [ ] `ReturnDetailSheet` (partner view) shows header, timeline, return info; hides admin panel
- [ ] Audit records written for SUBMITTED and CANCELLED
- [ ] `./gradlew test` passes; `npm run test` passes
