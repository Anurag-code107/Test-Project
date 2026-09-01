---
id: US-01
title: "Submit personal wallet redemption"
layers: ["BE", "FE"]
seed_id: "F-03.S-01"
touches_entities: ["RedemptionRequest"]
depends_on_stories: []
---

# US-01: Submit personal wallet redemption

## Description

**Actor:** PARTNER_SELLER
**Trigger:** Partner Seller selects a catalog item from the Redemption Store and initiates a redemption from their personal reward wallet.

**Steps:**
1. Partner Seller opens a catalog item detail (existing route from F-02); clicks "Redeem" button.
2. `RedemptionSubmitModal` opens — shows item name, available balance, currency, and an amount field pre-filled with the item's minimum.
3. Partner Seller confirms the amount and clicks "Submit Redemption".
4. FE calls `POST /api/v1/redemption/requests`.
5. BE: validates permission, in-flight cap, balance sufficiency, and minimum amount. Creates a RESERVE ledger entry atomically with the `RedemptionRequest` row. Sets `status` per processing mode (RESERVED for INSTANT/BATCH, PENDING_APPROVAL for APPROVAL_REQUIRED). Publishes `REDEMPTION_REQUESTED` Kafka event.
6. BE returns 201 with `RedemptionSubmissionConfirmationResponse` + `Location` header.
7. FE invalidates `['redemption-requests']` and `['wallet-balance']` query caches, then redirects to `/redemption/confirmation/:id`.
8. `RedemptionConfirmationPage` shows the estimated delivery timeline (or next batch date, or "Pending approval" message, based on `processingMode`).

**Expected outcome:** Redemption request created; wallet available balance reduced and reserved balance increased; partner sees confirmation with timeline.

**Negative paths:**
- Amount < catalog item `minimumTransactionAmount` → 422; inline error under amount field: "Amount is below the minimum allowed: {min}"
- In-flight count (PENDING_APPROVAL + RESERVED + PROCESSING) ≥ `maxInFlightRedemptions` → 409; `InFlightLimitBanner` toast: "Maximum in-flight redemptions reached"
- Available balance insufficient → 422; inline error: "Insufficient available balance"
- Caller without `action.redemption.redeem` → 403; redirect to login/unauthorized
- Cross-tenant request (tampered `catalogItemId` belonging to another tenant) → 404

---

## Acceptance Criteria

- **AC-1:** `POST /api/v1/redemption/requests` with valid body returns 201, a `Location` header pointing to the new resource, and a `RedemptionSubmissionConfirmationResponse` body with `id`, `status`, `processingMode`, `estimatedDelivery` (or `scheduledBatchDate` for BATCH mode).
- **AC-2:** A RESERVE ledger entry is written atomically with the `RedemptionRequest` row; the wallet's available balance decreases and reserved balance increases by `amount` — both are visible in a subsequent wallet balance query.
- **AC-3:** Processing mode determines initial status — INSTANT → `status=RESERVED`; BATCH → `status=RESERVED` with `scheduledBatchDate` populated; APPROVAL_REQUIRED → `status=PENDING_APPROVAL`.
- **AC-4:** `amount` below the catalog item's `minimumTransactionAmount` → 422 with message `"Amount is below the minimum allowed: {min}"`.
- **AC-5:** In-flight count (statuses: PENDING_APPROVAL, RESERVED, PROCESSING) ≥ `maxInFlightRedemptions` on the tenant → 409 with message `"Maximum in-flight redemptions reached"`.
- **AC-6:** Caller without `action.redemption.redeem` permission → 403.
- **AC-7:** A `SUBMITTED` audit record is written with `resourceType=REDEMPTION_REQUEST` and `resourceId=<new id>`.
- **AC-8:** A `REDEMPTION_REQUESTED` Kafka event is published to the `redemption-events` topic after successful DB commit.

---

## Out of Scope

- Company wallet redemption (US-02)
- XTRM / Xoxoday vendor API call — US-01 only creates the `RedemptionRequest` and reserves balance; actual vendor dispatch is US-05/US-06 (BLOCKED)
- Batch job execution (US-03, BLOCKED)
- Approval queue (F-04) — this story creates PENDING_APPROVAL status but the approval workflow itself is F-04
- Transaction history list (F-05)
- Redemption cancellation
- Webhook processing (US-07)

---

## Non-Functional Notes

- **Atomicity:** The RESERVE ledger entry and `RedemptionRequest` row MUST be written in the same transaction. A partial commit (request row without ledger entry, or vice versa) is a data corruption scenario.
- **Kafka publish timing:** `REDEMPTION_REQUESTED` event MUST be published AFTER the transaction commits — not inside the transaction. Use a `@TransactionalEventListener(phase = AFTER_COMMIT)` or equivalent.

---

## UI States

- [ ] **Loading:** Submit button shows spinner and becomes disabled while POST is in flight; modal stays open.
- [ ] **Empty/Initial:** Amount field pre-filled with item's `minimumTransactionAmount`; available balance shown beneath field as helper text.
- [ ] **Error (field-level):** 422 response → inline error text beneath amount field; submit button re-enabled.
- [ ] **Error (in-flight cap):** 409 response → `InFlightLimitBanner` toast; modal closes.
- [ ] **Error (5xx):** Generic toast "Something went wrong — please try again"; modal stays open.
- [ ] **Success:** 201 → modal closes; redirect to `/redemption/confirmation/:id`.

### Verbatim microcopy

- Modal title: `"Redeem Reward"`
- Amount field label: `"Amount"`
- Amount helper text: `"Available: {formattedBalance} {currencyLabel}"`
- Submit button (idle): `"Submit Redemption"`
- Submit button (loading): `"Submitting…"`
- Cancel button: `"Cancel"`
- Success redirect — confirmation page heading: `"Redemption Submitted"`
- Confirmation subtext (INSTANT): `"Estimated delivery: {estimatedDelivery}"`
- Confirmation subtext (BATCH): `"Scheduled for processing on {scheduledBatchDate}"`
- Confirmation subtext (PENDING_APPROVAL): `"Your redemption is pending approval"`
- Error — amount below minimum: `"Amount is below the minimum allowed: {min}"`
- Error — insufficient balance: `"Insufficient available balance"`
- Toast — in-flight cap: `"Maximum in-flight redemptions reached"`

### Conditional rendering

**Input: `processingMode` on confirmation page**
- `INSTANT`: Shows estimated delivery date from `estimatedDelivery` field.
- `BATCH`: Shows next batch run date from `scheduledBatchDate` field.
- `APPROVAL_REQUIRED`: Shows "Pending approval" message; no delivery date shown.

**Input: caller permission `action.redemption.redeem`**
- Present: "Redeem" button visible on catalog item detail.
- Absent: "Redeem" button hidden entirely (not disabled — absent).

---

## Depends on

- **Foundation tasks:** F1, F2, F3, F4, F5
- **Prior stories:** None

---

## Spec references

- `## Functional Requirements` — FR-03.1, FR-03.5, FR-03.6, FR-03.11
- `## Data Model / Entities [BE]` — `RedemptionRequest` fields and relationships
- `## API Endpoints [BE + FE]` — `POST /api/v1/redemption/requests`
- `## DTOs [BE]` — `SubmitPersonalRedemptionRequest`, `RedemptionSubmissionConfirmationResponse`
- `## Service Layer [BE]` — `RedemptionSubmissionService.submitPersonalRedemption()` business rules (in-flight check, balance check, minimum check, RESERVE ledger)
- `## Permissions & Feature Flags [BE + FE]` — `action.redemption.redeem` (PARTNER_SELLER)
- `## Security Design [BE]` — tenant isolation via `tenantValidator.getCurrentClientId()`; input validation rules
- `## Audit Trail [BE]` — SUBMITTED / REDEMPTION_REQUEST
- `## Domain Events [BE]` — `REDEMPTION_REQUESTED` event on `redemption-events` topic; Kafka prerequisite note about `KafkaConfig.java`
- `## Frontend Specification [FE]` — `RedemptionSubmitModal`, `RedemptionConfirmationCard`, `InFlightLimitBanner`, `RedemptionConfirmationPage`

---

## BE tasks [BE]

### BE-1: DTOs

**Files:**
- `src/main/java/com/tenxengage/app/dto/request/SubmitPersonalRedemptionRequest.java`
  - Fields: `catalogItemId` (UUID, @NotNull), `walletId` (UUID, @NotNull), `amount` (BigDecimal, @NotNull, @Positive), `currencyId` (String, @NotBlank, @Size(max=50))
  - See `spec.md → ## DTOs [BE] → SubmitPersonalRedemptionRequest`

- `src/main/java/com/tenxengage/app/dto/response/RedemptionSubmissionConfirmationResponse.java`
  - Record with: `id`, `status`, `processingMode`, `estimatedDelivery` (nullable String), `scheduledBatchDate` (nullable LocalDate), `submittedAt`
  - Static `from(RedemptionRequest)` factory method
  - See `spec.md → ## DTOs [BE] → RedemptionSubmissionConfirmationResponse`

- `src/main/java/com/tenxengage/app/dto/response/RedemptionRequestResponse.java`
  - List shape (lightweight) — `id`, `status`, `amount`, `currencyId`, `category`, `processingMode`, `submittedAt`
  - Static `from(RedemptionRequest)` factory

- `src/main/java/com/tenxengage/app/dto/response/RedemptionRequestDetailResponse.java`
  - Detail shape — all fields including `vendorReferenceId`, `failureReason`, `completedAt`, `scheduledBatchDate`
  - Static `from(RedemptionRequest)` factory

### BE-2: Service method + unit test

**Files:**
- `src/main/java/com/tenxengage/app/service/RedemptionSubmissionService.java`
  - `submitPersonalRedemption(SubmitPersonalRedemptionRequest req, UUID userId)` — steps:
    1. `tenantValidator.getCurrentClientId()` → `clientId`
    2. Load `TenantRedemptionSettings` → get `maxInFlightRedemptions`
    3. `countByClientIdAndUserIdAndStatusIn(clientId, userId, [PENDING_APPROVAL, RESERVED, PROCESSING])` — if ≥ cap → throw 409
    4. Load `RedemptionCatalogItem` by `catalogItemId` + `clientId` (404 if not found)
    5. Validate `amount` ≥ `catalogItem.minimumTransactionAmount` → 422 if not
    6. Load wallet by `walletId` + `clientId`; validate available balance ≥ amount → 422 if not
    7. Create RESERVE ledger entry via `LedgerService.reserve(walletId, amount, currencyId)`
    8. Persist `RedemptionRequest` (status = RESERVED or PENDING_APPROVAL per processing mode; BATCH → compute `scheduledBatchDate`)
    9. Publish `REDEMPTION_REQUESTED` via `RedemptionEventProducer` (after-commit)
    10. Return `RedemptionSubmissionConfirmationResponse.from(savedRequest)`
  - All DB writes in single `@Transactional`; Kafka publish via `@TransactionalEventListener(AFTER_COMMIT)`

- `src/test/java/com/tenxengage/app/service/RedemptionSubmissionServiceTest.java`
  - `submitPersonal_happyPath_instant` — status=RESERVED _(AC-1, AC-2, AC-3)_
  - `submitPersonal_happyPath_batch` — status=RESERVED, scheduledBatchDate populated _(AC-3)_
  - `submitPersonal_happyPath_approvalRequired` — status=PENDING_APPROVAL _(AC-3)_
  - `submitPersonal_amountBelowMinimum_throws422` _(AC-4)_
  - `submitPersonal_inFlightCapReached_throws409` _(AC-5)_
  - `submitPersonal_insufficientBalance_throws422`
  - `submitPersonal_catalogItemNotFound_throws404`
  - `submitPersonal_kafkaEventPublished_afterCommit` — Mockito verify `RedemptionEventProducer.publishRedemptionRequested()` called _(AC-8)_

### BE-3: Controller endpoint + @WebMvcTest

**Files:**
- `src/main/java/com/tenxengage/app/controller/RedemptionRequestController.java`
  - Tag: `Redemption Flow`
  - `POST /api/v1/redemption/requests` — `@RequiresPermission("action.redemption.redeem")`, returns 201 + `Location` header via `ServletUriComponentsBuilder`
  - `GET /api/v1/redemption/requests` — list (paginated, `PaginatedResponse<RedemptionRequestResponse>`)
  - `GET /api/v1/redemption/requests/{id}` — detail (`RedemptionRequestDetailResponse`)

- `src/test/java/com/tenxengage/app/controller/RedemptionRequestControllerTest.java`
  - `POST_201_personalRedemption_happyPath` _(AC-1)_
  - `POST_422_amountBelowMinimum` _(AC-4)_
  - `POST_409_inFlightCapReached` _(AC-5)_
  - `POST_403_missingPermission` _(AC-6)_
  - `POST_404_catalogItemWrongTenant`
  - `GET_200_listRedemptions_paginated`
  - `GET_200_getRedemptionById`
  - `GET_404_getRedemptionById_wrongTenant`

### BE-4: Audit annotation

Add `@Audited(action = AuditAction.SUBMITTED, resourceType = AuditResourceType.REDEMPTION_REQUEST, description = "Partner submitted personal wallet redemption")` to the `POST /api/v1/redemption/requests` controller method.

See `technical.md → ## Audit Annotations [BE]`.

---

## FE tasks [FE]

### FE-1: TypeScript types + service call

**Files:**
- `src/types/redemption-flow.types.ts` — copy from `../tenxengage-contracts/` after `/generate-contracts redemption-flow` runs. Do not hand-write. Types needed: `SubmitPersonalRedemptionRequest`, `RedemptionSubmissionConfirmationResponse`, `RedemptionRequestResponse`, `RedemptionRequestDetailResponse`.

- `src/services/redemption-flow.service.ts`
  - `submitPersonalRedemption(req: SubmitPersonalRedemptionRequest): Promise<RedemptionSubmissionConfirmationResponse>`
  - `getRedemptionRequest(id: string): Promise<RedemptionRequestDetailResponse>`
  - `getRedemptionRequests(params: RedemptionRequestListParams): Promise<PaginatedResponse<RedemptionRequestResponse>>`

### FE-2: Mutation hook

**File:** `src/hooks/useRedemptionSubmit.ts`
```ts
// mutation — calls POST /api/v1/redemption/requests or /company
// On success: invalidate ['redemption-requests'] + ['wallet-balance']
// On 409: show InFlightLimitBanner toast "Maximum in-flight redemptions reached"
// On 422: surface field-level errors inline in modal
```
See `technical.md → ## Hook Specs [FE] → useRedemptionSubmit`.

### FE-3a: RedemptionSubmitModal component + Vitest test

**Files:**
- `src/components/redemption-flow/RedemptionSubmitModal.tsx`
  - Dialog (not sheet/drawer) — triggered from catalog item detail
  - Fields: amount (number input, pre-filled with `minimumTransactionAmount`), helper text showing available balance
  - Uses `useRedemptionSubmit` mutation; shows loading state on submit button
  - Handles 422 field errors inline; 409 → closes modal + triggers InFlightLimitBanner

- `src/components/redemption-flow/__tests__/RedemptionSubmitModal.test.tsx`
  - `renders_withMinimumAmountPreFilled` _(AC-1)_
  - `showsInlineError_onAmountBelowMinimum` _(AC-4)_
  - `showsLoadingState_whileSubmitting`
  - `closesModal_onSuccess`

### FE-3b: InFlightLimitBanner component

**Files:**
- `src/components/redemption-flow/InFlightLimitBanner.tsx` — toast variant; shown when `useRedemptionSubmit` mutation returns 409. Message: `"Maximum in-flight redemptions reached"`.

### FE-4a: RedemptionConfirmationCard component + Vitest test

**Files:**
- `src/components/redemption-flow/RedemptionConfirmationCard.tsx`
  - Displays `status`, `processingMode`-based delivery text, and `scheduledBatchDate` if applicable
  - Conditional rendering per `processingMode` (see `### Conditional rendering` above)

- `src/components/redemption-flow/__tests__/RedemptionConfirmationCard.test.tsx`
  - `renders_instantMode_withDeliveryDate` _(AC-3)_
  - `renders_batchMode_withScheduledDate` _(AC-3)_
  - `renders_approvalRequired_withPendingMessage` _(AC-3)_

### FE-4b: RedemptionConfirmationPage + route wiring

**Files:**
- `src/pages/redemption-flow/RedemptionConfirmationPage.tsx`
  - Uses `useRedemptionRequest(id)` hook (detail hook, staleTime 30s)
  - Renders `RedemptionConfirmationCard`; handles loading and error states

- `src/App.tsx` — add route:
  ```tsx
  <Route path="/redemption/confirmation/:id" element={<RedemptionConfirmationPage />} />
  ```

---

## E2E test [FE]

**File:** `e2e/redemption-flow.spec.ts`

---

**Scenario 1:** `'Personal redemption — INSTANT mode happy path'` _(covers AC-1, AC-2, AC-3)_

| Field | Value |
|---|---|
| **User flow** | Navigate to catalog item detail → click "Redeem" → modal opens → confirm amount → click "Submit Redemption" → redirect to `/redemption/confirmation/:id` → see "Redemption Submitted" heading |
| **APIs to mock via `page.route()`** | `POST /api/v1/redemption/requests` → 201 + `RedemptionSubmissionConfirmationResponse` (processingMode=INSTANT); `GET /api/v1/redemption/requests/:id` → 200 + detail |
| **Visible assertion** | `expect(page.getByText('Redemption Submitted')).toBeVisible()`; confirmation card shows estimated delivery text |
| **Negative case** | — |

---

**Scenario 2:** `'Personal redemption — BATCH mode shows scheduled date'` _(covers AC-3)_

| Field | Value |
|---|---|
| **User flow** | Same as S1 but mock returns `processingMode=BATCH` with `scheduledBatchDate` |
| **APIs to mock via `page.route()`** | `POST /api/v1/redemption/requests` → 201 (processingMode=BATCH, scheduledBatchDate="2026-05-25") |
| **Visible assertion** | Confirmation page shows "Scheduled for processing on 2026-05-25" |
| **Negative case** | — |

---

**Scenario 3:** `'Personal redemption — amount below minimum shows inline error'` _(covers AC-4)_

| Field | Value |
|---|---|
| **User flow** | Open modal → enter amount below minimum → click submit → error visible in modal |
| **APIs to mock via `page.route()`** | `POST /api/v1/redemption/requests` → 422 `{"field":"amount","message":"Amount is below the minimum allowed: 10.00"}` |
| **Visible assertion** | `expect(page.getByText('Amount is below the minimum allowed: 10.00')).toBeVisible()` |
| **Negative case** | Modal remains open after error |

---

**Scenario 4:** `'Personal redemption — in-flight cap toast'` _(covers AC-5)_

| Field | Value |
|---|---|
| **User flow** | Open modal → click submit → 409 returned → toast appears |
| **APIs to mock via `page.route()`** | `POST /api/v1/redemption/requests` → 409 |
| **Visible assertion** | `expect(page.getByText('Maximum in-flight redemptions reached')).toBeVisible()` |
| **Negative case** | Modal closes after 409 |

---

**Scenario 5:** `'Personal redemption — missing permission hides Redeem button'` _(covers AC-6)_

| Field | Value |
|---|---|
| **User flow** | Load catalog item detail as user without `action.redemption.redeem` permission |
| **APIs to mock via `page.route()`** | `/api/v1/auth/me` → permissions without `action.redemption.redeem` |
| **Visible assertion** | `expect(page.getByRole('button', { name: 'Redeem' })).not.toBeVisible()` |
| **Negative case** | — |

---

## Execution checklist

**BE session:**
- [ ] `SubmitPersonalRedemptionRequest.java` DTO created with Jakarta validation annotations _(AC-1, AC-4)_
- [ ] `RedemptionSubmissionConfirmationResponse.java` record + `from()` factory created _(AC-1, AC-3)_
- [ ] `RedemptionRequestResponse.java` record + `from()` factory created
- [ ] `RedemptionRequestDetailResponse.java` record + `from()` factory created
- [ ] `RedemptionSubmissionService.submitPersonalRedemption()` implemented — in-flight check, balance check, minimum check, RESERVE ledger, status-per-mode _(AC-1, AC-2, AC-3, AC-4, AC-5)_
- [ ] Kafka publish via `@TransactionalEventListener(AFTER_COMMIT)` in `RedemptionSubmissionService` _(AC-8)_
- [ ] `RedemptionSubmissionServiceTest` all 8 test cases pass _(AC-1–AC-5, AC-8)_
- [ ] `RedemptionRequestController` `POST /api/v1/redemption/requests` endpoint with `@RequiresPermission`, 201 + Location header _(AC-1, AC-6)_
- [ ] `@Audited(action=SUBMITTED, resourceType=REDEMPTION_REQUEST)` on POST endpoint _(AC-7)_
- [ ] `GET /api/v1/redemption/requests` list + `GET /api/v1/redemption/requests/{id}` detail endpoints added
- [ ] `RedemptionRequestControllerTest` all 8 @WebMvcTest cases pass _(AC-1, AC-4, AC-5, AC-6)_

**FE session:**
- [ ] `redemption-flow.types.ts` copied from contracts (after `/generate-contracts` runs)
- [ ] `redemption-flow.service.ts` service calls added
- [ ] `useRedemptionSubmit` mutation hook created — invalidates `['redemption-requests']` + `['wallet-balance']`; handles 409 toast + 422 field errors _(AC-5)_
- [ ] `RedemptionSubmitModal.tsx` component created — dialog, amount field, loading state, error handling _(AC-4)_
- [ ] `RedemptionSubmitModal.test.tsx` Vitest tests pass _(AC-1, AC-4)_
- [ ] `InFlightLimitBanner.tsx` toast component created _(AC-5)_
- [ ] `RedemptionConfirmationCard.tsx` created — conditional rendering per `processingMode` _(AC-3)_
- [ ] `RedemptionConfirmationCard.test.tsx` Vitest tests pass (INSTANT, BATCH, PENDING_APPROVAL) _(AC-3)_
- [ ] `RedemptionConfirmationPage.tsx` page created with `useRedemptionRequest` hook
- [ ] Route `/redemption/confirmation/:id` added to `App.tsx`
- [ ] E2E S1 (INSTANT happy path) passes _(AC-1, AC-2, AC-3)_
- [ ] E2E S2 (BATCH scheduled date) passes _(AC-3)_
- [ ] E2E S3 (amount below minimum) passes _(AC-4)_
- [ ] E2E S4 (in-flight cap toast) passes _(AC-5)_
- [ ] E2E S5 (missing permission hides button) passes _(AC-6)_

---

## Done when

1. **BE:** `./gradlew test` passes — all `RedemptionSubmissionServiceTest` + `RedemptionRequestControllerTest` cases green.
2. **FE:** `npm run test` passes + all 5 Playwright E2E scenarios pass against real BE.
3. Every AC (AC-1 through AC-8) is referenced by at least one passing test.
