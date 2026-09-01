---
id: US-01
title: "Configure balance expiration policy"
layers: ["BE", "FE"]
touches_entities: ["BalanceExpirationPolicy"]
depends_on_stories: []
seed_id: "F-09.S-01"
---

# US-01: Configure balance expiration policy

## Description

**Actor:** CLIENT_ADMIN
**Trigger:** Admin opens **Redemption Settings → Balance Expiration**.

**Steps:**
1. Page loads policies for all four currency types (cash, points, credits, tickets).
2. Admin toggles `enabled` for a currency, picks a mode (`INACTIVITY` or `FIXED_DATE`), sets `inactivityDays` **or** `fixedExpiryDate`, and a `leadTimeDays`.
3. Admin saves → `PUT /policies/{currencyId}` upserts the policy.
4. The row shows an "active since" caption; the `ExpiringSoonPreviewCard` updates with at-risk totals.

**Expected outcome:** Policy persisted; `enabled_at` set on enable/material change; toast "Expiration policy saved".

**Negative paths:**
- `leadTimeDays ≥ inactivityDays` → inline field error, 422 from server.
- `fixedExpiryDate` in the past → inline field error, 422.
- Missing `action.redemption.expiration.configure` → no access (403 / route guard).

---

## Acceptance Criteria

- **AC-1:** `GET /api/v1/redemption/expiration/policies` returns the tenant's saved policies (≤4) as `BalanceExpirationPolicyResponse[]` (no `client_id`).
- **AC-2:** `PUT /api/v1/redemption/expiration/policies/{currencyId}` with a valid body upserts and returns `200` + `BalanceExpirationPolicyResponse`; `enabled_at` is set when the policy is enabled or materially changed (FR-09.1, FR-09.3).
- **AC-3:** Invalid config → `422` with `errorCode` (service-layer): `leadTimeDays ≥ inactivityDays`, past `fixedExpiryDate`, `inactivityDays` outside `[30, 1825]`, or mode/field mismatch (FR-09.9).
- **AC-4:** Audit row written on upsert: `action=EDITED`, `resourceType=BALANCE_EXPIRATION_POLICY`.
- **AC-5:** Request lacking `action.redemption.expiration.configure` → `403`; cross-tenant access isolated (a tenant never sees another's policy).
- **AC-6 (flow-gap):** The config surface presents **all four currency types** sourced from `config/currencies.ts`; a currency with no saved policy renders as a **disabled/unconfigured default** the admin can enable (not hidden).
- **AC-7:** `GET /api/v1/redemption/expiration/expiring-soon` returns aggregate `ExpiringBalancePreviewResponse[]` (per-currency `affectedWalletCount` + `totalAmountAtRisk`) with **no per-wallet identity**.

---

## Out of Scope

- Cancellation of pending expirations when a policy is disabled/relaxed — **US-03** (this story's PUT does the upsert; US-03 wires the cancellation side-effect into it).
- The scheduled batch that consumes the policy (warn → **US-02**, expire → **US-03**).
- Breakage reporting and CSV export — **US-04**.

---

## UI States

- [ ] **Loading:** skeleton rows for the four currency policy cards while `GET /policies` is in flight.
- [ ] **Empty / default:** currencies with no saved policy render as disabled "Not configured" defaults with an enable toggle (AC-6) — never a blank list.
- [ ] **Error:** load failure → ErrorState + retry; toast "Could not load expiration policies".
- [ ] **Partial / Optimistic:** on save, the saved row reflects the new values immediately while the PUT is in flight.

### Verbatim microcopy

- Button labels: "Save", "Cancel"
- Success toast: "Expiration policy saved"
- Section caption (enabled): "Active since {date}" · (disabled): "Not configured"
- Field errors: "Lead time must be at least 1 day and less than the inactivity period"; "Fixed expiry date must be in the future"; "Inactivity period must be between 30 and 1825 days"
- Mode labels: "Inactivity period", "Fixed calendar date"
- Helper text under lead time: "How many days before expiry the partner is notified"

### Conditional rendering

**Input: `expirationMode`**
- `INACTIVITY`: show `inactivityDays` numeric field; hide `fixedExpiryDate`.
- `FIXED_DATE`: show `fixedExpiryDate` date-picker; hide `inactivityDays`.

**Input: `enabled`**
- `false`: mode/params/lead-time fields disabled; caption "Not configured".
- `true`: fields enabled; caption "Active since {enabledAt}".

---

## Depends on

- **Foundation tasks:** F1 (enums), F2 (V32 tables), F3 (entity + repo + fixtures), F4 (permissions + flag)
- **Prior stories:** None

---

## Spec references

- `## Functional Requirements` — FR-09.1, FR-09.2, FR-09.3, FR-09.9
- `## Data Model / Entities [BE]` — `BalanceExpirationPolicy` fields, unique `(client_id, currency_id)`
- `## API Endpoints [BE + FE]` — `GET /policies`, `PUT /policies/{currencyId}`, `GET /expiring-soon`
- `## DTOs [BE]` — `UpsertBalanceExpirationPolicyRequest`, `BalanceExpirationPolicyResponse`, `ExpiringBalancePreviewResponse`
- `## Service Layer [BE]` — `BalanceExpirationPolicyService` validation rules (422)
- `## Permissions & Feature Flags [BE + FE]` — `action.redemption.expiration.configure`
- `## Security Design [BE]` — input validation table; `PUT /policies` rate-limit note
- `## Audit Trail [BE]` — policy create/update audit
- `## Frontend Specification [FE]` — `BalanceExpirationSettingsPage`, `BalanceExpirationPolicyForm`, `ExpiringSoonPreviewCard`
- `technical.md → ## Package Layout [BE]/[FE]`, `## Repository Queries`, `## Hook Specs`

---

## BE tasks [BE]

### BE-1: DTOs
**Files:** `dto/request/UpsertBalanceExpirationPolicyRequest.java`, `dto/response/BalanceExpirationPolicyResponse.java`, `dto/response/ExpiringBalancePreviewResponse.java`

Structural validation only (`@NotNull enabled`, `@NotNull expirationMode`, `@NotNull leadTimeDays`); domain bounds enforced in the service so the `errorCode`/422 shape isn't masked. `BalanceExpirationPolicyResponse` exposes `currencyId, currencyDisplayName, enabled, expirationMode, inactivityDays, fixedExpiryDate, leadTimeDays, enabledAt, updatedAt` — never `client_id`. See `spec.md → ## DTOs [BE]`.

### BE-2: Service methods + unit test
**Files:** `service/redemption/BalanceExpirationPolicyService.java`, `test/.../service/redemption/BalanceExpirationPolicyServiceTest.java`

`getPolicies()`, `upsertPolicy(currencyId, request)` (validate → 422; set `enabled_at` on enable/material change), `getExpiringSoon(withinDays, currencyId)` (aggregate). All queries tenant-scoped via `@Filter`. Unit test covers: happy upsert, each validation failure (lead≥inactivity, past date, out-of-bounds, mode/field mismatch), enabled_at set, expiring-soon aggregate.

### BE-3: Controller endpoints + @WebMvcTest
**Files:** `controller/BalanceExpirationController.java`, `test/.../controller/BalanceExpirationControllerTest.java`

`@RequestMapping("/api/v1/redemption/expiration")`. GET `/policies`, PUT `/policies/{currencyId}`, GET `/expiring-soon` — each `@RequiresPermission("action.redemption.expiration.configure")`. @WebMvcTest covers: 200 GET, 200 PUT, 422 invalid, 403 missing permission.

### BE-4: Audit annotation
`@Audited(action="EDITED", resourceType="BALANCE_EXPIRATION_POLICY", description="Configured balance expiration policy")` on `upsertPolicy`. See `spec.md → ## Audit Trail`.

---

## FE tasks [FE]

### FE-1: TypeScript types + service call
**Files:** `src/types/balanceExpiration.types.ts`, `src/services/balanceExpiration.service.ts`

Copy types from `../tenxengage-contracts/` — do not hand-write. Service: `getPolicies()`, `upsertPolicy(currencyId, body)`, `getExpiringSoon(params)`.

### FE-2: Hooks
**Files:** `src/hooks/useBalanceExpirationPolicies.ts`, `src/hooks/useExpiringSoon.ts`, `src/hooks/useUpsertBalanceExpirationPolicy.ts`

Query keys + staleTime per `technical.md → ## Hook Specs [FE]`. Upsert mutation invalidates `['balance-expiration-policies', clientId]` and `['balance-expiring-soon', clientId]`; maps 422 `errorCode` → field errors.

### FE-3a: Policy form component + Vitest
**Files:** `src/components/balanceExpiration/BalanceExpirationPolicyForm.tsx`, `src/components/balanceExpiration/balanceExpirationPolicySchema.ts`, `src/components/balanceExpiration/__tests__/BalanceExpirationPolicyForm.test.tsx`

Mode-conditional fields (AC conditional rendering); zod schema mirrors service rules; all four currency types presented (AC-6). Renders labels via `getCurrency(currencyId.toLowerCase()).label`.

### FE-3b: Expiring-soon preview component + Vitest
**Files:** `src/components/balanceExpiration/ExpiringSoonPreviewCard.tsx`, `src/components/balanceExpiration/__tests__/ExpiringSoonPreviewCard.test.tsx`

Read-only per-currency at-risk totals; refetches on policy save.

### FE-4: Page wiring
**Files:** `src/pages/balanceExpiration/BalanceExpirationSettingsPage.tsx`, `src/App.tsx`

Route `/settings/redemption/balance-expiration` wrapped in `<ProtectedRoute permission="action.redemption.expiration.configure">`; sidebar entry under "Redemption Settings".

---

## E2E test [FE]

**Scenario 1:** `'configure points inactivity policy — happy path'` _(covers AC-1, AC-2, AC-6)_
**File:** `e2e/balance-expiration.spec.ts`

| Field | Value |
|---|---|
| **User flow** | Navigate to settings → all 4 currencies shown (Points unconfigured) → enable Points, mode INACTIVITY, 90 days, lead 30 → Save |
| **APIs to mock** | `GET /api/v1/redemption/expiration/policies` → 200 `[]`; `PUT .../policies/points` → 200 `BalanceExpirationPolicyResponse` |
| **Visible assertion** | `expect(page.getByText('Expiration policy saved')).toBeVisible()`; Points card shows "Active since" |
| **Negative case** | — |

**Scenario 2:** `'invalid lead time shows field error'` _(covers AC-3)_
**File:** `e2e/balance-expiration.spec.ts`

| Field | Value |
|---|---|
| **User flow** | Enable Points, inactivity 90, lead 120 → Save |
| **APIs to mock** | `PUT .../policies/points` → 422 with `errorCode` body |
| **Visible assertion** | `expect(page.getByText('Lead time must be at least 1 day and less than the inactivity period')).toBeVisible()` |

**Scenario 3:** `'expiring-soon preview lists at-risk totals'` _(covers AC-7)_
**File:** `e2e/balance-expiration.spec.ts`

| Field | Value |
|---|---|
| **User flow** | Load settings with a configured policy → preview card renders |
| **APIs to mock** | `GET .../expiring-soon` → 200 `[{currencyId:'points', affectedWalletCount:12, totalAmountAtRisk:'3400.00', ...}]` |
| **Visible assertion** | `expect(page.getByText('12')).toBeVisible()` (affected wallets) |

---

## Execution checklist

**BE session:**
- [ ] `UpsertBalanceExpirationPolicyRequest.java` DTO created _(AC-2, AC-3)_
- [ ] `BalanceExpirationPolicyResponse.java` DTO created (no `client_id`) _(AC-1, AC-2)_
- [ ] `ExpiringBalancePreviewResponse.java` DTO created (aggregate-only) _(AC-7)_
- [ ] `BalanceExpirationPolicyService.{getPolicies,upsertPolicy,getExpiringSoon}` added _(AC-1, AC-2, AC-3, AC-7)_
- [ ] `BalanceExpirationPolicyServiceTest` passes (happy + all validation branches + enabled_at) _(AC-2, AC-3)_
- [ ] `BalanceExpirationController` GET `/policies`, PUT `/policies/{currencyId}`, GET `/expiring-soon` with `@RequiresPermission` _(AC-1, AC-2, AC-5, AC-7)_
- [ ] `@Audited` on `upsertPolicy` _(AC-4)_
- [ ] `BalanceExpirationControllerTest` @WebMvcTest passes (200/422/403) _(AC-2, AC-3, AC-5)_

**FE session:**
- [ ] `balanceExpiration.types.ts` types added from contracts
- [ ] `balanceExpiration.service.ts` calls added
- [ ] `useBalanceExpirationPolicies` / `useExpiringSoon` / `useUpsertBalanceExpirationPolicy` hooks created _(AC-1, AC-7)_
- [ ] `BalanceExpirationPolicyForm` + zod schema (all 4 currencies, mode-conditional) _(AC-3, AC-6)_
- [ ] `ExpiringSoonPreviewCard` created _(AC-7)_
- [ ] `*.test.tsx` Vitest passes _(AC-3, AC-6)_
- [ ] UI states implemented: loading, default/unconfigured, error _(AC-6)_
- [ ] Page wired to real API + ProtectedRoute + sidebar _(AC-5)_
- [ ] E2E `balance-expiration.spec.ts` scenarios pass _(AC-1, AC-2, AC-3, AC-6, AC-7)_

---

## Done when

1. **BE:** `./gradlew test` passes — `BalanceExpirationPolicyServiceTest` + `BalanceExpirationControllerTest` green.
2. **FE:** `npm run test` + `npx playwright test e2e/balance-expiration.spec.ts` pass against real BE.
3. Every AC is referenced by at least one passing test.
