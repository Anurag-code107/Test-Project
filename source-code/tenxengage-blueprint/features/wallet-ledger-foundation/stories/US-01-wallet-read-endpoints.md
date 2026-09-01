---
id: US-01
title: "Wallet read endpoints"
layers: ["BE"]
seed_id: ["F-01.S-01", "F-01.S-02"]
touches_entities: ["RewardWallet"]
depends_on_stories: []
---

# US-01: Wallet read endpoints

## Description

**Actor:** PARTNER_SELLER, PARTNER_ADMIN, CLIENT_ADMIN
**Trigger:** Caller authenticates and requests their wallet balance(s) via the wallet API.

**Steps:**
1. Caller sends GET /api/v1/wallets/me (PARTNER_SELLER/PARTNER_ADMIN) or /company/{companyId} (PARTNER_ADMIN) or /users/{userId} (CLIENT_ADMIN)
2. `WalletController` resolves caller identity from `SecurityContext` and validates access rights
3. `WalletService` queries `RewardWalletRepository` by `(clientId, userId/companyId, walletType)` — tenant-filtered via `@Filter`
4. Returns `List<RewardWalletResponse>` with per-currency wallet balances

**Expected outcome:** Caller receives list of wallet objects with `id`, `walletType`, `currencyId`, `availableBalance`, `reservedBalance`. Internal fields (`client_id`, `version`, `user_id`, `partner_company_id`) are never exposed.

**Negative paths:**
- PARTNER_ADMIN requests `/company/{companyId}` where `companyId` differs from JWT claim → 403
- PARTNER_SELLER requests `/company/{companyId}` → 403 (role check before wallet lookup)
- PARTNER_ADMIN with no `partnerCompanyId` JWT claim requests `/company/{companyId}` → 403 "Caller has no associated partner company"
- CLIENT_ADMIN requests `/users/{userId}` for user in different tenant → 404
- Unauthenticated caller → 401

---

## Acceptance Criteria

- **AC-1:** `GET /api/v1/wallets/me` returns 200 with `List<RewardWalletResponse>`; each item contains `id`, `walletType`, `currencyId`, `availableBalance`, `reservedBalance`; response never includes `client_id`, `version`, `user_id`, or `partner_company_id`
- **AC-2:** `GET /api/v1/wallets/company/{companyId}` returns 200 for PARTNER_ADMIN whose JWT `partnerCompanyId` matches the path param; returns 403 if `companyId` mismatches, if caller is PARTNER_SELLER, or if caller has no `partnerCompanyId` JWT claim
- **AC-3:** `GET /api/v1/wallets/users/{userId}` returns 200 for CLIENT_ADMIN when `userId` belongs to their tenant; returns 404 if `userId` belongs to a different tenant; returns 403 for PARTNER_SELLER and PARTNER_ADMIN
- **AC-4:** `GET /api/v1/reward-balances` (deprecated endpoint) delegates to `WalletService.getMyWallets()` and returns `RewardWalletResponse` shape with `balance` kept as an alias for `availableBalance`
- **AC-5:** Unauthenticated `GET /api/v1/wallets/me` returns 401

---

## Out of Scope

- Wallet auto-creation — wallets are created by `WalletService.credit()` in US-03
- Mutation operations (reserve, debit, release, returnCredit) — US-03
- LedgerEntry paginated history endpoint — F-05
- Nav balance widget rendering — US-02
- Company wallet reads via admin path beyond CLIENT_ADMIN (no super-admin cross-tenant in F-01)

---

## Depends on

- **Foundation tasks:** F1, F2, F3, F4
- **Prior stories:** None

---

## Spec references

- `spec.md → ## Functional Requirements` — FR-1, FR-2, FR-9
- `spec.md → ## Data Model / Entities [BE]` — `RewardWallet` fields
- `spec.md → ## API Endpoints [BE + FE]` — all three GET endpoints + authorization logic
- `spec.md → ## DTOs [BE]` — `RewardWalletResponse` fields; `LedgerEntryResponse` stub
- `spec.md → ## Service Layer [BE]` — `WalletService.getMyWallets/getCompanyWallets/getUserWallets`
- `spec.md → ## Permissions & Feature Flags [BE + FE]` — `isAuthenticated()` for /me; `module.redemption_store` for /company; `action.redemption.view_all_history` for /users
- `spec.md → ## Security Design [BE]` — rate limits (60/min /me, 60/min /company, 30/min /users); IDOR mitigations
- `spec.md → ## Modified Existing Endpoints [BE + FE]` — deprecated delegation

---

## BE tasks [BE]

### BE-1: Response DTOs

**Files:**
- `src/main/java/com/tenxengage/app/dto/response/RewardWalletResponse.java` — NEW; fields: `id` (UUID), `walletType` (String), `currencyId` (String), `availableBalance` (String), `reservedBalance` (String); static factory `from(RewardWallet)`
- `src/main/java/com/tenxengage/app/dto/response/LedgerEntryResponse.java` — NEW (stubbed); fields: `id`, `entryType`, `amount`, `currencyId`, `referenceType`, `referenceId`, `note`, `createdAt`; static factory `from(LedgerEntry)`; fully used by F-05
- `src/main/java/com/tenxengage/app/dto/response/RewardBalanceResponse.java` — MODIFIED; `from()` factory delegates to `RewardWalletResponse` shape; keep `balance` field as alias for `availableBalance` for backwards compatibility

See `spec.md → ## DTOs [BE]` for field specs. Never include `client_id`, `version`, `user_id`, or `partner_company_id` in any response DTO.

### BE-2: WalletService read methods + unit tests

**Files:**
- `src/main/java/com/tenxengage/app/service/WalletService.java` — NEW; implement three read methods:
  - `getMyWallets()`: `@Transactional(readOnly=true)`; resolves `userId` from `SecurityContext`; queries `findByClientIdAndUserIdAndWalletType(clientId, userId, INDIVIDUAL)`
  - `getCompanyWallets(companyId)`: `@Transactional(readOnly=true)`; validates PARTNER_ADMIN JWT company claim matches `companyId`; CLIENT_ADMIN validates company in tenant; queries `findByClientIdAndPartnerCompanyIdAndWalletType`
  - `getUserWallets(userId)`: `@Transactional(readOnly=true)`; CLIENT_ADMIN only; validates `userId` exists in tenant via `findByClientIdAndUserId`; returns 404 if not found
- `src/main/java/com/tenxengage/app/service/RewardBalanceService.java` — mark `@Deprecated`; delegate all calls to `WalletService`
- `src/test/java/com/tenxengage/app/service/WalletServiceTest.java` — NEW; test cases:
  - `getMyWallets_returnsList_whenWalletsExist`
  - `getMyWallets_returnsEmpty_whenNoWallets`
  - `getCompanyWallets_returns403_whenCompanyMismatch`
  - `getCompanyWallets_returns403_whenCallerHasNoCompanyClaim`
  - `getUserWallets_returns404_whenUserNotInTenant`

See `spec.md → ## Service Layer [BE]` for tenant isolation contract (clientId always from `TenantContext`).

### BE-3: WalletController + @WebMvcTest

**Files:**
- `src/main/java/com/tenxengage/app/controller/WalletController.java` — NEW; tag `Wallet`; three endpoints:
  - `GET /api/v1/wallets/me` — `@PreAuthorize("isAuthenticated()")` (approved deviation — see `spec.md → ## Permissions`)
  - `GET /api/v1/wallets/company/{companyId}` — `@RequiresPermission("module.redemption_store")` + role check in service
  - `GET /api/v1/wallets/users/{userId}` — `@RequiresPermission("action.redemption.view_all_history")`
- `src/test/java/com/tenxengage/app/controller/WalletControllerTest.java` — NEW; @WebMvcTest cases:
  - `getMyWallets_returns200_whenAuthenticated` _(AC-1)_
  - `getMyWallets_returns401_whenNotAuthenticated` _(AC-5)_
  - `getCompanyWallets_returns200_forPartnerAdminOwnCompany` _(AC-2)_
  - `getCompanyWallets_returns403_forPartnerAdminWrongCompany` _(AC-2)_
  - `getCompanyWallets_returns403_forPartnerSeller` _(AC-2)_
  - `getUserWallets_returns200_forClientAdmin` _(AC-3)_
  - `getUserWallets_returns403_forPartnerSeller` _(AC-3)_
  - `getUserWallets_returns404_whenUserNotInTenant` _(AC-3)_

### BE-4: RewardBalanceController delegation

**Files:**
- `src/main/java/com/tenxengage/app/controller/RewardBalanceController.java` — MODIFIED; both `GET /api/v1/reward-balances` and `GET /api/v1/reward-balances/{userId}` delegate to `WalletService`; mark endpoints `@Deprecated`; return `RewardWalletResponse` shape _(AC-4)_

---

## E2E test

_Omitted — `layers: ["BE"]`. Coverage via @WebMvcTest cases in BE-3._

---

## Execution checklist

**BE session:**
- [ ] `RewardWalletResponse.java` DTO created with all fields + `from(RewardWallet)` factory _(AC-1)_
- [ ] `LedgerEntryResponse.java` stub DTO created with `from(LedgerEntry)` factory
- [ ] `RewardBalanceResponse.java` updated — `from()` delegates to `RewardWalletResponse` shape; `balance` alias preserved _(AC-4)_
- [ ] `WalletService.getMyWallets()` implemented — `@Transactional(readOnly=true)`, resolves userId from SecurityContext _(AC-1)_
- [ ] `WalletService.getCompanyWallets(companyId)` implemented — PARTNER_ADMIN company claim validation + CLIENT_ADMIN tenant validation _(AC-2)_
- [ ] `WalletService.getUserWallets(userId)` implemented — CLIENT_ADMIN only; 404 if userId not in tenant _(AC-3)_
- [ ] `RewardBalanceService.java` marked `@Deprecated`; delegates to `WalletService`
- [ ] `WalletServiceTest` read-path unit tests pass _(AC-1, AC-2, AC-3)_
- [ ] `WalletController` created with 3 GET endpoints and correct `@PreAuthorize`/`@RequiresPermission` annotations _(AC-1, AC-2, AC-3, AC-5)_
- [ ] `WalletControllerTest` @WebMvcTest cases pass — 200/401/403/404 paths _(AC-1, AC-2, AC-3, AC-5)_
- [ ] `RewardBalanceController` delegation updated; deprecated endpoints return `RewardWalletResponse` shape _(AC-4)_

---

## Done when

1. `./gradlew test` passes — all new `WalletServiceTest` + `WalletControllerTest` cases green
2. Every AC above is referenced by at least one passing @WebMvcTest or unit test case
