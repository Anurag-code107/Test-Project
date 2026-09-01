---
id: US-02
title: Admin return review
layers: ["BE", "FE"]
touches_entities: ["RedemptionReturn"]
depends_on_stories: ["US-01"]
seed_id: "S-02"
---

# US-02: Admin return review

## Description

**As a** CLIENT_ADMIN or ACTIVITY_APPROVER,
**I want to** view, approve, and reject partner return requests from the admin approval queue,
**So that** valid returns can be forwarded to Xoxoday and invalid ones are closed with a clear reason.

**Flow:**
1. Admin navigates to the F-04 approval queue page. A new "Returns" tab is added alongside the existing "Redemptions" tab.
2. The "Returns" tab renders `ReturnsApprovalTab` defaulting to `status = PENDING_APPROVAL` filter.
3. Admin views the table of pending return requests (item name, partner, company, amount, status, submission date).
4. Admin can click "View Details" on any row → `ReturnDetailSheet` opens (`role = 'admin'`) showing header, timeline, return info, and admin-action buttons.
5. **Approve:** Admin clicks "Approve" (AlertDialog confirmation) → `POST /admin/returns/{id}/approve` → 200; return transitions to `APPROVED`; Xoxoday notification fires asynchronously (US-03 story).
6. **Reject:** Admin clicks "Reject" → `RejectReturnDialog` opens; admin enters mandatory rejection reason (max 1000 chars) → `POST /admin/returns/{id}/reject` → 200; return transitions to `RETURN_REJECTED`; partner notified.
7. Negative paths: 409 for wrong state; 404 for cross-tenant; 400 for blank rejection reason.

**Out of Scope for this story:** Xoxoday API call (US-03), RETURN_TIMED_OUT resolution (US-04), partner cancel (US-01).

---

## Acceptance Criteria

- **AC-1** `GET /admin/returns` returns `Page<ReturnQueueItemResponse>` scoped to the current tenant; supports `status`, `startDate`, `endDate`, `page`, `size` (max 50), `sort` (allowlist: `createdAt`, `amount`) query params; requires `action.redemption.return.review`.
- **AC-2** `POST /admin/returns/{id}/approve` transitions `PENDING_APPROVAL → APPROVED`, sets `reviewedBy` and `approvedAt`, returns `200` with `ReturnDetailResponse`, and writes `APPROVED / REDEMPTION_RETURN` audit record; returns `409` when status is not `PENDING_APPROVAL`; returns `404` for wrong tenant.
- **AC-3** `POST /admin/returns/{id}/reject` transitions `PENDING_APPROVAL → RETURN_REJECTED`, sets `rejectedAt`, `reviewedBy`, and `reviewNotes`, returns `200` with `ReturnDetailResponse`, and writes `REJECTED / REDEMPTION_RETURN` audit record; returns `400` when `rejectionReason` is blank; returns `409` when status is not `PENDING_APPROVAL`.
- **AC-4** `GET /admin/returns/{id}` returns `ReturnDetailResponse` with admin-visible fields (`reviewNotes`, `vendorReturnReference`); returns `404` for wrong tenant.
- **AC-5** FE — `ReturnsApprovalTab` defaults to `status = PENDING_APPROVAL` on initial load (status filter pre-selected). Empty state when no returns match: "No return requests to review."
- **AC-6** FE — `RejectReturnDialog`: rejection reason is required (Submit disabled if blank); shows char counter; Reject button is destructive red. On success: dialog closes, admin queue and detail cache invalidated.
- **AC-7** FE — `ReturnDetailSheet` (admin role): renders admin-action panel with "Approve" and "Reject" buttons when `status = PENDING_APPROVAL` and caller has `action.redemption.return.review`; buttons hidden for other statuses or insufficient permission; `reviewNotes` and `vendorReturnReference` sections visible only in admin role.

---

## Out of Scope

- Xoxoday vendor notification after approval — US-03
- RETURN_TIMED_OUT manual resolution — US-04
- Partner-facing return surfaces — US-01
- Bulk approval — Phase 2 per spec

---

## UI States

### `ReturnsApprovalTab`
| State | Behavior |
|---|---|
| Loading | Skeleton table (5 rows) |
| Empty | "No return requests to review." (centered, subdued text) |
| Error | Inline retry with "Failed to load return requests." |
| Approve in-flight | "Approve" button shows spinner in AlertDialog; disabled |
| Reject in-flight | "Reject" (in RejectReturnDialog) shows spinner; disabled |

### `RejectReturnDialog`
| State | Behavior |
|---|---|
| Reason empty | Submit button disabled; no escape closes without warning |
| Reason filled | Submit button enabled |
| Submitting | Submit shows spinner; disabled |
| Error | Inline error in dialog (stays open) |
| Success | Dialog closes; alert fires |

### `ReturnDetailSheet` — admin-action panel
| Return status | Action buttons shown |
|---|---|
| `PENDING_APPROVAL` + has review permission | Approve + Reject |
| `RETURN_TIMED_OUT` + has review permission | Resolve (wired in US-04) |
| All other statuses | No action buttons |

---

### Verbatim microcopy

| Surface | String |
|---|---|
| Tab label | "Returns" |
| Approve row action | "Approve" |
| Reject row action | "Reject" |
| View Details row action | "View Details" |
| AlertDialog title (approve) | "Approve this return request?" |
| AlertDialog body (approve) | "The return will be forwarded to Xoxoday. The partner's balance will be restored only after vendor confirmation." |
| AlertDialog confirm (approve) | "Approve" |
| AlertDialog dismiss (approve) | "Cancel" |
| RejectReturnDialog title | "Reject Return Request?" |
| RejectReturnDialog reason label | "Rejection reason" |
| RejectReturnDialog reason placeholder | "Explain why this return request is being rejected…" |
| RejectReturnDialog char counter | "{n}/1000" |
| RejectReturnDialog submit button | "Reject Request" |
| RejectReturnDialog cancel button | "Cancel" |
| Empty state | "No return requests to review." |

---

### Conditional rendering

| Condition | Behavior |
|---|---|
| `status = PENDING_APPROVAL` + review permission | Approve and Reject shown in row kebab and detail sheet |
| `status ≠ PENDING_APPROVAL` | Approve and Reject hidden |
| `role = 'admin'` on `ReturnDetailSheet` | `reviewNotes` section visible; `vendorReturnReference` visible |
| `role = 'partner'` on `ReturnDetailSheet` | `reviewNotes` and `vendorReturnReference` sections hidden |

---

## Depends on

- **US-01 BE done** — `ReturnController`, `ReturnService` (base), and `ReturnDetailSheet` (shell) must exist before US-02 extends them
- **Foundation (F1–F5)** — enums, entity, repository in place

---

## Spec References

- FR-06.4 (approve), FR-06.7 (reject), FR-06.11 (state machine)
- `spec.md → ## DTOs → ReturnQueueItemResponse, ReturnDetailResponse`
- `spec.md → ## API Endpoints → Admin — Return Review`
- `spec.md → ## Service Layer → ReturnService.approveReturn(), rejectReturn(), getAdminReturns()`
- `spec.md → ## Frontend Specification → ReturnsApprovalTab, RejectReturnDialog, ReturnDetailSheet`
- `spec.md → ## Edge Cases → 9, 10`
- `technical.md → ## Hook Specs → useAdminReturns`

---

## BE Tasks

### BE-1: DTOs

- [ ] `ReturnQueueItemResponse` record: `id (UUID)`, `catalogItemName (String)`, `partnerDisplayName (String)`, `partnerCompanyName (String)`, `amount (BigDecimal)`, `currencyId (String)`, `status (ReturnStatus)`, `reason (String, nullable)`, `createdAt (Instant)`; static factory `from(RedemptionReturn, String catalogItemName, String partnerDisplayName, String partnerCompanyName)`
- [ ] `RejectReturnRequest` record: `rejectionReason (String)` — `@NotBlank`, `@Size(max=1000)`

### BE-2: Service + unit test

- [ ] `ReturnService.approveReturn(id, reviewerId, clientId)` — validates `PENDING_APPROVAL` state (409 otherwise); sets `status=APPROVED`, `reviewedBy`, `approvedAt`; saves; fires `@Async ReturnVendorService.notifyXoxodayReturn()` (wired in US-03); publishes `RETURN_APPROVED` event; writes audit; `@Transactional`
- [ ] `ReturnService.rejectReturn(id, rejectionReason, reviewerId, clientId)` — validates `PENDING_APPROVAL` state (409 otherwise); transitions to `RETURN_REJECTED`; sets `rejectedAt`, `reviewedBy`, `reviewNotes`; publishes `RETURN_REJECTED` event; writes audit; `@Transactional`
- [ ] `ReturnService.getAdminReturns(clientId, filters, pageable)` — calls `findByClientId`; maps to `ReturnQueueItemResponse` with hydrated `catalogItemName`, `partnerDisplayName`, `partnerCompanyName`; `@Transactional(readOnly=true)`
- [ ] `ReturnService.getReturnById(id, userId=null, clientId, isAdmin=true)` — admin path: `findByIdAndClientId`; populates `reviewNotes` and `vendorReturnReference` in `ReturnDetailResponse.from()`; `@Transactional(readOnly=true)`
- [ ] `ReturnServiceTest` additions — approve happy path, approve 409 wrong state, reject happy path, reject 400 blank reason, reject 409 wrong state, admin list pagination, admin getById returns admin fields

### BE-3: Controller + `@WebMvcTest`

- [ ] `ReturnAdminController` at `/api/v1/redemption/admin/returns`:
  - `GET /` → `getAdminReturns()` → 200
  - `GET /{id}` → `getReturnById(isAdmin=true)` → 200
  - `POST /{id}/approve` → `approveReturn()` → 200
  - `POST /{id}/reject` → `rejectReturn()` → 200
  - All annotated `@RequiresPermission("action.redemption.return.review")`
  - Rate limit on approve+reject: 30 req/min per admin user
- [ ] `ReturnAdminControllerTest` (`@WebMvcTest`):
  - Approve 200 response shape
  - Approve 409 wrong state
  - Reject 200 with reason
  - Reject 400 blank reason
  - Admin list 200 with pagination + filter
  - `GET /{id}` includes `reviewNotes` in response

### BE-4: Audit

- [ ] `@Audited(action = APPROVED, resourceType = REDEMPTION_RETURN, description = "Approved return request")` on approve endpoint
- [ ] `@Audited(action = REJECTED, resourceType = REDEMPTION_RETURN, description = "Rejected return request")` on reject endpoint

---

## FE Tasks

### FE-1: Types + service

- [ ] `ReturnQueueItemResponse` type already in contracts (from F0) — verify it's in `src/types/redemption-returns.types.ts`
- [ ] `RejectReturnRequest` type in service layer
- [ ] Add `rejectReturn(id, dto)`, `approveReturn(id)`, `getAdminReturns(filters, pageable)`, `getAdminReturn(id)` to `src/services/redemption-returns.service.ts`

### FE-2: Hooks

- [ ] `useAdminReturns(filters)` — `queryKey: ['admin-returns', clientId, filters]`; `staleTime: 2 * 60 * 1000`; invalidated by `useApproveReturn`, `useRejectReturn`, `useResolveTimedOutReturn`
- [ ] `useApproveReturn()` — mutation; on success: invalidates `['admin-returns', ...]` and `['return', id, true]`
- [ ] `useRejectReturn()` — mutation; on success: invalidates `['admin-returns', ...]` and `['return', id, true]`

### FE-3: Components + Vitest

- [ ] `RejectReturnDialog.tsx` — props: `returnId`, `onSuccess`; required reason textarea (Zod `z.string().min(1).max(1000)`); char counter; destructive red submit; inline error on failure; uses `useRejectReturn()`
- [ ] `ReturnsApprovalTab.tsx` — props: `clientId`; status dropdown (default `PENDING_APPROVAL`) + date range pickers; table columns: Catalog Item, Partner, Company, Amount, Status badge, Submitted, Actions (kebab: View Details, Approve [PENDING_APPROVAL], Reject [PENDING_APPROVAL]); row click "View Details" → `ReturnDetailSheet role='admin'`; Approve → AlertDialog → `useApproveReturn()`; Reject → `RejectReturnDialog`; loading skeleton; empty state "No return requests to review."; error inline retry
- [ ] Extend `ReturnDetailSheet.tsx` — when `role = 'admin'`: render `reviewNotes` section (if present), `vendorReturnReference` section (if present), and admin-action panel (Approve + Reject buttons gated on `PENDING_APPROVAL` status + permission); Resolve button stub for US-04
- [ ] Vitest tests: `ReturnsApprovalTab.test.tsx` — renders with PENDING_APPROVAL default, kebab shows Approve/Reject for PENDING_APPROVAL rows; `RejectReturnDialog.test.tsx` — submit disabled when blank, enabled when filled

### FE-4: Page wiring

- [ ] Add "Returns" tab to F-04 admin approval queue page shell — render `<ReturnsApprovalTab clientId={currentClient.id} />` in new tab content

---

## E2E Scenarios

| File | Scenario | AC coverage |
|---|---|---|
| `e2e/redemption-returns/admin-approve.spec.ts` | Admin views Returns tab → clicks Approve on PENDING_APPROVAL row → AlertDialog → confirms → row status changes to APPROVED | AC-2, AC-5, AC-7 |
| `e2e/redemption-returns/admin-reject.spec.ts` | Admin rejects with reason → return shows RETURN_REJECTED; blank reason → submit disabled | AC-3, AC-6 |

---

## Execution Checklist

- [ ] BE-1: Write `ReturnQueueItemResponse` and `RejectReturnRequest` DTOs
- [ ] BE-2: Write `ReturnService.approveReturn()`, `rejectReturn()`, `getAdminReturns()`, admin `getReturnById()` + `ReturnServiceTest` additions
- [ ] BE-3: Write `ReturnAdminController` (GET /, GET /{id}, POST /{id}/approve, POST /{id}/reject) + `ReturnAdminControllerTest`
- [ ] BE-4: Add `@Audited` on approve (APPROVED) and reject (REJECTED)
- [ ] FE-1: Add admin service methods to `redemption-returns.service.ts`
- [ ] FE-2: Write `useAdminReturns`, `useApproveReturn`, `useRejectReturn` hooks
- [ ] FE-3: Write `RejectReturnDialog` + Vitest test
- [ ] FE-3: Write `ReturnsApprovalTab` + Vitest test
- [ ] FE-3: Extend `ReturnDetailSheet` with admin-role panel (Approve/Reject) + admin-only field sections
- [ ] FE-4: Wire `ReturnsApprovalTab` as "Returns" tab in F-04 admin approval queue page
- [ ] Run `./gradlew test` — all tests pass
- [ ] Run `npm run test` — all Vitest tests pass
- [ ] Update `tracker.md` — set US-02 BE and FE status to `done`

---

## Done When

- [ ] `GET /admin/returns` returns paginated `ReturnQueueItemResponse` for the tenant, with status/date filters working
- [ ] `POST /admin/returns/{id}/approve` returns 200; 409 for wrong state
- [ ] `POST /admin/returns/{id}/reject` returns 200 with reason; 400 for blank reason; 409 for wrong state
- [ ] `ReturnsApprovalTab` defaults to PENDING_APPROVAL; empty state renders; Approve and Reject flows work
- [ ] `RejectReturnDialog` enforces non-blank reason; submit disabled when blank
- [ ] `ReturnDetailSheet` admin role shows Approve/Reject buttons for PENDING_APPROVAL returns; shows reviewNotes + vendorReturnReference sections
- [ ] Audit records written for APPROVED and REJECTED
- [ ] `./gradlew test` passes; `npm run test` passes
