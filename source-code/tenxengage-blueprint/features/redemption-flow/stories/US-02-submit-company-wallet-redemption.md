---
id: US-02
title: "Submit company wallet redemption"
layers: ["BE", "FE"]
seed_id: "F-03.S-02"
touches_entities: ["RedemptionRequest"]
depends_on_stories: ["US-01"]
---

# US-02: Submit company wallet redemption

## Description

**Actor:** PARTNER_ADMIN
**Trigger:** Partner Admin selects a catalog item and initiates a redemption from the partner organization's company reward wallet.

**Steps:**
1. Partner Admin opens a catalog item detail; sees "Redeem (Company)" button (visible only when `action.redemption.redeem_company` permission is present).
2. `RedemptionSubmitModal` opens — same UI as US-01 but shows company wallet balance.
3. Partner Admin confirms amount and clicks "Submit Redemption".
4. FE calls `POST /api/v1/redemption/requests/company`.
5. BE: validates permission, in-flight cap, balance from company wallet, minimum amount. Creates RESERVE ledger entry against company wallet. Sets `walletType=COMPANY`. Persists `RedemptionRequest`. Publishes `REDEMPTION_REQUESTED` event.
6. Returns 201 + Location header + `RedemptionSubmissionConfirmationResponse`.
7. FE invalidates caches, redirects to `/redemption/confirmation/:id`.

**Expected outcome:** Company wallet redemption created; company wallet available balance reduced; Partner Admin sees confirmation page.

**Negative paths:**
- PARTNER_SELLER calling `/company` endpoint → 403 (does not have `action.redemption.redeem_company`)
- Company wallet balance insufficient → 422
- Amount below minimum → 422
- In-flight cap reached → 409

---

## Acceptance Criteria

- **AC-1:** `POST /api/v1/redemption/requests/company` with valid body returns 201 + `Location` header + `RedemptionSubmissionConfirmationResponse`.
- **AC-2:** RESERVE ledger entry written against the company wallet (`walletType=COMPANY`); company wallet available balance decreases by `amount`.
- **AC-3:** Caller without `action.redemption.redeem_company` permission → 403.
- **AC-4:** PARTNER_SELLER (who has `action.redemption.redeem` but not `action.redemption.redeem_company`) calling `/company` → 403.
- **AC-5:** `SUBMITTED` audit record written with `resourceType=REDEMPTION_REQUEST` and action=`SUBMITTED`.

---

## Out of Scope

- Personal wallet redemption (US-01)
- Vendor API dispatch (US-05/US-06, BLOCKED)
- Approval queue for company redemptions (F-04)

---

## UI States

- [ ] **Loading:** Same as US-01 modal loading state.
- [ ] **Error (field-level):** 422 inline errors in modal.
- [ ] **Error (in-flight cap):** 409 → InFlightLimitBanner toast.
- [ ] **Success:** 201 → redirect to `/redemption/confirmation/:id`.

### Conditional rendering

**Input: caller permission `action.redemption.redeem_company`**
- Present (PARTNER_ADMIN): "Redeem (Company)" button visible on catalog item detail alongside or below "Redeem" button.
- Absent (PARTNER_SELLER or unauthenticated): "Redeem (Company)" button not rendered.

---

## Depends on

- **Foundation tasks:** F1, F2, F3, F4, F5
- **Prior stories:** US-01 (establishes `RedemptionRequestController` structure and `RedemptionSubmissionService`)

---

## Spec references

- `## Functional Requirements` — FR-03.2
- `## API Endpoints [BE + FE]` — `POST /api/v1/redemption/requests/company`
- `## DTOs [BE]` — `SubmitCompanyRedemptionRequest`, `RedemptionSubmissionConfirmationResponse` (reused from US-01)
- `## Service Layer [BE]` — `RedemptionSubmissionService.submitCompanyRedemption()`
- `## Permissions & Feature Flags [BE + FE]` — `action.redemption.redeem_company` (PARTNER_ADMIN)
- `## Audit Trail [BE]` — SUBMITTED / REDEMPTION_REQUEST (company endpoint)

---

## BE tasks [BE]

### BE-1: DTO

**File:** `src/main/java/com/tenxengage/app/dto/request/SubmitCompanyRedemptionRequest.java`
- Fields: `catalogItemId` (UUID, @NotNull), `walletId` (UUID, @NotNull — company wallet ID), `amount` (BigDecimal, @NotNull, @Positive), `currencyId` (String, @NotBlank, @Size(max=50))
- `RedemptionSubmissionConfirmationResponse` is reused from US-01 — no new response DTO needed.

### BE-2: Service method + unit test

**File:** `src/main/java/com/tenxengage/app/service/RedemptionSubmissionService.java` (extend existing)
- Add `submitCompanyRedemption(SubmitCompanyRedemptionRequest req, UUID userId)`:
  - Same flow as `submitPersonalRedemption` but loads company wallet (`walletType=COMPANY`)
  - Sets `wallet_type=COMPANY` on the persisted `RedemptionRequest`
  - Applies same in-flight cap check (company redemptions count toward the partner's overall cap)

**File:** `src/test/java/com/tenxengage/app/service/RedemptionSubmissionServiceTest.java` (extend existing)
- `submitCompany_happyPath` — status=RESERVED, walletType=COMPANY _(AC-1, AC-2)_
- `submitCompany_insufficientCompanyBalance_throws422`
- `submitCompany_inFlightCapReached_throws409`

### BE-3: Controller endpoint + @WebMvcTest

**File:** `src/main/java/com/tenxengage/app/controller/RedemptionRequestController.java` (extend existing)
- Add `POST /api/v1/redemption/requests/company` — `@RequiresPermission("action.redemption.redeem_company")`; 201 + Location header

**File:** `src/test/java/com/tenxengage/app/controller/RedemptionRequestControllerTest.java` (extend existing)
- `POST_company_201_happyPath` _(AC-1)_
- `POST_company_403_missingPermission` _(AC-3)_
- `POST_company_403_partnerSellerCannotUseCompanyEndpoint` _(AC-4)_

### BE-4: Audit annotation

Add `@Audited(action = AuditAction.SUBMITTED, resourceType = AuditResourceType.REDEMPTION_REQUEST, description = "Partner Admin submitted company wallet redemption")` to the `POST /api/v1/redemption/requests/company` controller method.

---

## FE tasks [FE]

### FE-1: Types + service call

**File:** `src/services/redemption-flow.service.ts` (extend existing)
- Add `submitCompanyRedemption(req: SubmitCompanyRedemptionRequest): Promise<RedemptionSubmissionConfirmationResponse>`
- Types already in `redemption-flow.types.ts` from US-01 (contracts include both request types)

### FE-2: Hook (extend existing)

**File:** `src/hooks/useRedemptionSubmit.ts` (extend)
- Add `type: 'personal' | 'company'` parameter to mutation; route to correct service method based on type.

### FE-3: Company redeem button (conditional)

No new component file needed — add "Redeem (Company)" button to catalog item detail with `action.redemption.redeem_company` permission guard. Reuses `RedemptionSubmitModal` with `type='company'` prop.

---

## E2E test [FE]

**File:** `e2e/redemption-flow.spec.ts` (extend existing)

---

**Scenario 1:** `'Company redemption — PARTNER_ADMIN happy path'` _(covers AC-1, AC-2)_

| Field | Value |
|---|---|
| **User flow** | Login as PARTNER_ADMIN → open catalog item detail → click "Redeem (Company)" → modal opens with company wallet balance → submit → redirect to confirmation |
| **APIs to mock via `page.route()`** | `POST /api/v1/redemption/requests/company` → 201 + `RedemptionSubmissionConfirmationResponse` |
| **Visible assertion** | `expect(page.getByText('Redemption Submitted')).toBeVisible()` |
| **Negative case** | — |

---

**Scenario 2:** `'Company redemption — PARTNER_SELLER cannot see company button'` _(covers AC-4)_

| Field | Value |
|---|---|
| **User flow** | Login as PARTNER_SELLER → open catalog item detail |
| **APIs to mock via `page.route()`** | `/api/v1/auth/me` → permissions with `action.redemption.redeem` only |
| **Visible assertion** | `expect(page.getByRole('button', { name: 'Redeem (Company)' })).not.toBeVisible()` |
| **Negative case** | — |

---

## Execution checklist

**BE session:**
- [ ] `SubmitCompanyRedemptionRequest.java` DTO created _(AC-1)_
- [ ] `RedemptionSubmissionService.submitCompanyRedemption()` method added — company wallet, walletType=COMPANY _(AC-1, AC-2)_
- [ ] `RedemptionSubmissionServiceTest` company tests pass _(AC-1, AC-2)_
- [ ] `POST /api/v1/redemption/requests/company` endpoint added with `@RequiresPermission("action.redemption.redeem_company")` _(AC-3, AC-4)_
- [ ] `@Audited(action=SUBMITTED, ...)` on company endpoint _(AC-5)_
- [ ] `RedemptionRequestControllerTest` company endpoint tests pass _(AC-1, AC-3, AC-4)_

**FE session:**
- [ ] `submitCompanyRedemption` service call added
- [ ] `useRedemptionSubmit` hook updated to accept `type: 'company'`
- [ ] "Redeem (Company)" button added to catalog item detail with permission guard _(AC-3, AC-4)_
- [ ] E2E S1 (PARTNER_ADMIN company redemption) passes _(AC-1, AC-2)_
- [ ] E2E S2 (PARTNER_SELLER cannot see company button) passes _(AC-4)_

---

## Done when

1. **BE:** `./gradlew test` passes — `RedemptionSubmissionServiceTest` company cases + `RedemptionRequestControllerTest` company cases green.
2. **FE:** `npm run test` passes + E2E S1–S2 pass against real BE.
3. Every AC (AC-1 through AC-5) referenced by at least one passing test.
