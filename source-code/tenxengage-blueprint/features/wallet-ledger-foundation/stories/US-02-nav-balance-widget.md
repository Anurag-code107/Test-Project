---
id: US-02
title: "Nav balance widget"
layers: ["FE"]
seed_id: "F-01.S-04"
touches_entities: ["RewardWallet"]
depends_on_stories: ["US-01"]
---

# US-02: Nav balance widget

## Description

**Actor:** PARTNER_SELLER, PARTNER_ADMIN
**Trigger:** Any authenticated user loads any page — `AppLayout` renders and conditionally shows wallet balance in the nav header.

**Steps:**
1. `AppLayout` renders → checks `module.redemption_store` permission via `usePermissions()`
2. If permission granted: renders `<RewardBalanceWidget />` in the header
3. `RewardBalanceWidget` calls `useMyWallets()` hook → GET /api/v1/wallets/me
4. While query is in flight: skeleton loader renders (never blank)
5. On success: displays `availableBalance` per currency; currencies with zero balance collapsed by default; expand control shows all
6. On error (5xx): error fallback renders with retry option
7. Clicking widget navigates to `/redemption-store`

**Expected outcome:** PARTNER_SELLER and PARTNER_ADMIN see a persistent balance indicator in the nav header on every page. CLIENT_ADMIN sees no widget.

**Negative paths:**
- `useMyWallets()` returns 5xx → error fallback rendered; widget does not crash AppLayout
- Zero-balance wallets → expand control visible; collapsed view shows only non-zero currencies
- `module.redemption_store` not granted (CLIENT_ADMIN) → widget absent from DOM

---

## Acceptance Criteria

- **AC-1:** `RewardBalanceWidget` is visible in `AppLayout` header for authenticated PARTNER_SELLER and PARTNER_ADMIN; entirely absent (not hidden, not zero-height) for CLIENT_ADMIN
- **AC-2:** Widget renders skeleton while `useMyWallets()` query is in flight; never shows a blank space
- **AC-3:** Clicking the widget navigates to `/redemption-store`
- **AC-4:** Zero-balance currencies are collapsed by default; a visible expand control reveals all currencies
- **AC-5:** If `GET /api/v1/wallets/me` returns a 5xx, widget renders an error fallback — AppLayout remains functional

---

## Out of Scope

- Company wallet (`useCompanyWallet`) display — used by F-03 redemption flow
- `useUserWalletsAdmin` — CLIENT_ADMIN admin view, used by F-05 transaction history
- Redemption Store page rendering — F-02/F-03
- Cache invalidation on redemption submit — F-03 wires that (it invalidates `['wallets','me']`)

---

## Non-Functional Notes

- **Install Skeleton component first:** `npx shadcn-ui add skeleton` — not yet in project per `technical.md`
- **StaleTime:** `useMyWallets` must use `staleTime: 2 * 60 * 1000` (2 min) — spec-mandated for nav widget accuracy

---

## UI States

- [ ] **Loading:** Skeleton placeholder in header position while `useMyWallets()` is in flight — never blank
- [ ] **Populated:** Available balance per currency; non-zero currencies shown first; zero-balance currencies collapsed behind expand control
- [ ] **Error:** Error fallback within widget bounds (e.g., "—" with retry icon); AppLayout unaffected — widget does not throw or unmount AppLayout
- [ ] **Zero balance (all currencies):** Widget renders with "0" or collapsed view; expand shows all; does not hide the widget entirely

---

## Depends on

- **Foundation tasks:** F1, F2, F3, F4
- **Prior stories:** US-01 BE done (wallet read endpoints must exist)

---

## Spec references

- `spec.md → ## Functional Requirements` — FR-7, FR-8
- `spec.md → ## Frontend Specification [FE]` — `RewardBalanceWidget` component spec; `AppLayout.tsx` modification
- `spec.md → ## Frontend Specification [FE] → Data Flow` — `useMyWallets()` query key, staleTime, invalidation
- `spec.md → ## Permissions & Feature Flags [BE + FE]` — `module.redemption_store` gates widget visibility
- `spec.md → ## Edge Cases` — edge cases 6 (zero balance), 10 (loading state), 11 (transition note)
- `technical.md → ## Package Layout [FE]` — all file paths
- `technical.md → ## Hook Specs [FE]` — `useMyWallets()` query key + staleTime + invalidation

---

## FE tasks [FE]

### FE-1: TypeScript types + service calls

**Files:**
- `src/types/wallet.types.ts` — NEW; copy `RewardWalletResponse` interface from `../tenxengage-contracts/` after contracts generated; do not hand-write

**File:** `src/services/wallet.service.ts` — NEW; three read functions:
  - `getMyWallets(): Promise<RewardWalletResponse[]>` → `GET /api/v1/wallets/me`
  - `getCompanyWallet(companyId: string): Promise<RewardWalletResponse[]>` → `GET /api/v1/wallets/company/{companyId}`
  - `getUserWallets(userId: string): Promise<RewardWalletResponse[]>` → `GET /api/v1/wallets/users/{userId}`

See `technical.md → ## Package Layout [FE]` for paths.

### FE-2: TanStack Query hooks

**File:** `src/hooks/useWalletApi.ts` — NEW; three hooks:
  - `useMyWallets()`: queryKey `['wallets','me']`, staleTime `2 * 60 * 1000`, queryFn `walletService.getMyWallets`
  - `useCompanyWallet(companyId)`: queryKey `['wallets','company',companyId]`, staleTime `2 * 60 * 1000`, enabled `!!companyId`
  - `useUserWalletsAdmin(userId)`: queryKey `['wallets','user',userId]`, staleTime `5 * 60 * 1000`, enabled `!!userId`

See `technical.md → ## Hook Specs [FE]` for all query key and staleTime values.

### FE-3a: RewardBalanceWidget component + Vitest test

**Files:**
- `src/components/rewards/RewardBalanceWidget.tsx` — NEW; props: `className?: string`; calls `useMyWallets()`; renders:
  - Skeleton during loading (`shadcn/ui Skeleton`)
  - Balance list when resolved — non-zero currencies first; zero-balance currencies behind expand toggle
  - Error fallback on query error (no crash)
  - Entire widget clickable → `navigate('/redemption-store')`
- `src/components/rewards/__tests__/RewardBalanceWidget.test.tsx` — NEW; Vitest cases:
  - `renders skeleton while loading` _(AC-2)_
  - `renders balances when query resolves` _(AC-1)_
  - `collapses zero-balance currencies by default` _(AC-4)_
  - `renders error fallback on query failure` _(AC-5)_

### FE-3b: AppLayout modification

**File:** `src/components/layout/AppLayout.tsx` — MODIFIED; add `<RewardBalanceWidget />` to header section; conditional on `module.redemption_store` permission via `usePermissions()` _(AC-1)_

---

## E2E test [FE]

**Scenario 1:** `'balance widget visible and navigates to redemption store'` _(covers AC-1, AC-2, AC-3)_

**File:** `e2e/wallet.spec.ts`

| Field | Value |
|---|---|
| **User flow** | Log in as PARTNER_SELLER → wait for AppLayout → assert widget visible in header → assert skeleton shown briefly → assert balance rendered → click widget → assert URL is `/redemption-store` |
| **APIs to mock via `page.route()`** | `GET /api/v1/wallets/me` → 200 + `[{id, walletType:"INDIVIDUAL", currencyId:"cash", availableBalance:"150.00", reservedBalance:"0.00"}]` |
| **Visible assertion** | `expect(page.getByTestId('reward-balance-widget')).toBeVisible()` then `expect(page).toHaveURL('/redemption-store')` after click |
| **Negative case** | N/A — navigation is the key assertion |

---

**Scenario 2:** `'balance widget hidden for CLIENT_ADMIN'` _(covers AC-1)_

**File:** `e2e/wallet.spec.ts`

| Field | Value |
|---|---|
| **User flow** | Log in as CLIENT_ADMIN → wait for AppLayout → assert widget absent from DOM |
| **APIs to mock via `page.route()`** | `GET /api/v1/wallets/me` → not called (widget not rendered) |
| **Visible assertion** | `expect(page.getByTestId('reward-balance-widget')).not.toBeAttached()` |
| **Negative case** | N/A |

---

**Scenario 3:** `'balance widget shows skeleton then error fallback on API failure'` _(covers AC-2, AC-5)_

**File:** `e2e/wallet.spec.ts`

| Field | Value |
|---|---|
| **User flow** | Log in as PARTNER_SELLER → mock wallet API to return 500 → assert skeleton shown → assert error fallback visible → assert AppLayout still functional (nav links clickable) |
| **APIs to mock via `page.route()`** | `GET /api/v1/wallets/me` → 500 |
| **Visible assertion** | `expect(page.getByTestId('reward-balance-widget-error')).toBeVisible()` |
| **Negative case** | AppLayout nav links still respond after widget error |

---

## Execution checklist

**FE session:**
- [ ] `npx shadcn-ui add skeleton` run — Skeleton component available _(required for AC-2)_
- [ ] `src/types/wallet.types.ts` created from contracts — `RewardWalletResponse` interface present _(AC-1)_
- [ ] `wallet.service.ts` created — `getMyWallets`, `getCompanyWallet`, `getUserWallets` functions added
- [ ] `useWalletApi.ts` created — `useMyWallets` hook with correct queryKey `['wallets','me']` and staleTime 2 min _(AC-1, AC-2)_
- [ ] `useCompanyWallet` and `useUserWalletsAdmin` hooks added to `useWalletApi.ts`
- [ ] `RewardBalanceWidget.tsx` created — skeleton on loading, balance on success, error fallback on error _(AC-2, AC-4, AC-5)_
- [ ] Click handler navigates to `/redemption-store` _(AC-3)_
- [ ] Zero-balance collapse/expand implemented _(AC-4)_
- [ ] `RewardBalanceWidget.test.tsx` Vitest tests pass _(AC-2, AC-4, AC-5)_
- [ ] `AppLayout.tsx` modified — `<RewardBalanceWidget />` conditional on `module.redemption_store` _(AC-1)_
- [ ] E2E: `'balance widget visible and navigates'` Playwright test passes against real BE _(AC-1, AC-2, AC-3)_
- [ ] E2E: `'balance widget hidden for CLIENT_ADMIN'` Playwright test passes _(AC-1)_
- [ ] E2E: `'skeleton then error fallback'` Playwright test passes _(AC-2, AC-5)_

---

## Done when

1. `npm run test` passes — `RewardBalanceWidget.test.tsx` Vitest cases green
2. `npx playwright test e2e/wallet.spec.ts` passes against real BE — all 3 E2E scenarios green
3. Every AC above is referenced by at least one passing test
