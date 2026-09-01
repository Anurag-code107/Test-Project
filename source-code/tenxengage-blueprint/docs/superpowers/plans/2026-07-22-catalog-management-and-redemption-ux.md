# Catalog Management & Redemption UX — Enhancement Plan

**Created:** 2026-07-22 · **Branch:** `features/redemption-xtrm-payout-enhancement`
**Status:** PLAN ONLY — not yet implemented (author requested "plan first").
**Scope:** an enhancement to F-02 (redemption catalog) + F-05 (redemption history). No new spec — plan doc per the enhancement workflow.

---

## Decisions locked (2026-07-22, @pushpendra)

- **A — currency by category (catalog create/edit):** CASH → **only `cash`**; NON_CASH → **the other three** (`points`, `credits`, `tickets`).
- **B — "All Redemptions" name filter:** **backend search** (LIKE across all rows / all pages), not client-side current-page filtering.
- **C — currency uppercase:** **display/output only** — uppercase in FE render + BE-generated output (export). **Stored `currency_id` keys stay lowercase** (no data migration).
- **Catalog ownership — CONFIRMED (final):** **client admins own catalog management.** (Was provisional "yes for now"; now confirmed.) Unblocks Part 2 (permission migration + client-owned catalog).
- **Catalog model — LOCKED: Model 2 (pure client-owned).** No shared global/platform catalog; every item belongs to exactly one client; reads = own-client only; visibility = the item's own `isActive` toggle (labeled "Active/Inactive"); no per-tenant enable step, no platform Activate/Deactivate button in the client view.
- **Additional answers (2026-07-22):**
  - **Redemption-tab hide** works via the `module.redemption_store` group gate + the existing override system: **user-level** override → hidden for that user; **company-level** override → hidden for all that company's partner admins + sellers.
  - **Currency uppercase = everywhere, labels included** ("Cash" → "CASH").
  - **Existing catalog rows** → assigned to **pushpendra@genicommunity.com's client** (resolve `client_id` at build).
  - **Single catalog UI (already merged):** the client admin manages catalogs at one screen — `/settings/platform?tab=redemption-catalog` → `RedemptionCatalogTab` → `GlobalCatalogAdminPage`. All catalog routes redirect there. `CatalogConfigPage` / `TenantCatalogConfigTable` exist as files but are **not routed** (orphaned/dead) — safe to ignore; optionally delete as cleanup. So Part 2b's FE work targets **only** `GlobalCatalogAdminPage`.
  - **TENX-admin catalog view left as-is code-wise (legacy)** — not reworked/removed; its reads simply become owner-scoped (empty for a platform caller). (Xoxoday `/sync` is DISABLED — separate line below.)
  - **Redeem-time owner check = YES** — enforce item-owner == buyer's client at redemption submit.
  - **Overrides — keep `ClientCatalogItemConfig` for overrides only** (don't fold onto the item); its per-tenant *enable* role is **removed** — the current Enable/Disable toggle goes away.
  - **Single gate = `isActive` (Option Y):** one "Active/Inactive" toggle per item; created inactive by default; browse **and** submission both gate on `owner AND isActive`; drop the `config.enabled` check from submission (config = optional overrides only). No two-gate drift.
  - **Create owner = caller's `clientId`** (from request `TenantContext`) on the manual create path. **Xoxoday `/sync` is DISABLED** (option a) — it's an `@Async` global job with no client context that would crash on the NOT NULL insert; not the demo path.
  - **⚠️ Build-time verifications (before shipping):** (1) confirm `pushpendra@genicommunity.com`'s `client_id` **is the same client the demo runs on** (`a0000…001`) — else backfilling existing items to a different client makes the demo's CASH catalog vanish; (2) confirm client admins hold `module.redemption_store` **by default** — else item 3's group gate hides the whole Redemption tab (incl. Approval Queue / Analytics) for them, not just for overridden sellers.

---

## Part 1 — UX batch (6 items)

### 1. Newest catalog on top (client-admin catalog list) — BE
- **Now:** `RedemptionCatalogAdminService.listCatalogItems` sorts by name (`findAllByOrderByNameAsc`).
- **Change:** sort by **`createdAt DESC`**. The current `findAllByOrderByNameAsc` hardcodes the order in the method name — replace with `findAll(pageable)` (JpaRepository) built from `Sort.by(DESC,"createdAt")` in the service; the category path (`findAllByCategoryAndIsActive`) already takes `Pageable`, so pass the same Sort. Verify `createdAt` exists on `BaseEntity`.
- **Files:** `service/RedemptionCatalogAdminService.java`, `repository/RedemptionCatalogItemRepository.java`.
- **Scope:** admin list only — the seller browse keeps its currency/category ordering.

### 2. Currency options filtered by category (catalog create/edit form) — FE
- **Now:** `GlobalCatalogItemForm` currency dropdown lists all currencies regardless of category.
- **Change (Decision A):** CASH → only `cash` (auto-selected, single option); NON_CASH → all currencies except `cash`. On category switch, reset the selected currency if it's no longer valid.
- **Files:** `components/redemption-catalog/GlobalCatalogItemForm.tsx` (+ `GlobalCatalogItemForm.test.tsx`).

### 3. Removing `module.redemption_store` hides the WHOLE Redemption group — FE
- **Now:** `NavGroupConfig` has no group-level gate; the "Redemption" group shows if any sub-item is permitted (`filteredGroupItems.length > 0`), so removing the store permission only hides the store item.
- **Change:** add optional `permissionKey?` to `NavGroupConfig`; in `RoleSidebar` group render, `if (group.permissionKey && !can(group.permissionKey)) return null`. Set the Redemption group's gate to `module.redemption_store` **in every sidebar config that has a Redemption group** (partner sidebar for sellers/partner-admins **and** the client-admin sidebar) — otherwise the company-level "hide for all its partner admins + sellers" case won't fully trigger. Works with the deny-only company/user override system.
- **Files:** `components/layout/sidebars/RoleSidebar.tsx`, `sidebarConfigs.ts` (all relevant configs) (+ `RoleSidebar.render.test.tsx` — whole group hidden when the perm is absent).

### 4. "All Redemptions" — filter by user name & company name (backend search) — BE + contract + FE
- **Now:** `TenantTransactionHistoryPage` has two **UUID** inputs; `findTenantHistory` filters by `userId`/`companyId`.
- **Change (Decision B):**
  - **BE:** `RedemptionAdminHistoryFilters` add `userName`, `companyName` (String); `RedemptionHistoryRepository.findTenantHistory` filters `LOWER(...) LIKE` on `user.firstName`/`user.lastName` (concatenated) + `user.partnerCompany.name`; add controller `@RequestParam`s.
  - **Export parity (LOCKED — include):** the tenant page's Export forwards the same filters — extend `TriggerExportRequest` + `RedemptionExportService` to accept `userName`/`companyName` **only on the `ALL_TENANT` scope query** (PERSONAL/COMPANY exports don't use them) so exported rows match the filtered view.
  - **Contract:** `endpoints/redemption-history.yaml` — new params on the tenant list + export request schema.
  - **FE:** `TenantTransactionHistoryPage.tsx` — replace the two UUID inputs with **User name** / **Company name** search inputs (+ update `redemption-history.types.ts`).

### 5. Currency shown in CAPITALS (display/output only, everywhere) — FE + BE
- **Change (Decision C — uppercase EVERYWHERE, labels included):**
  - **FE:** uppercase **both** the currency **code** (catalog table `item.currencyId` → `CASH`) **and** the friendly **label** (`getCurrency(id).label` "Cash" → "CASH") at every render site. ⚠️ **Scope is broad** — requires an enumeration pass, not a single helper: catalog table, transaction history, All-Redemptions, balance cards, withdrawal dialog, analytics, redeem modal, currency dropdowns. Simplest robust approach: uppercase in the shared render helpers (`getCurrency().label` consumers) + a `.toUpperCase()` on raw-code sites, or `text-transform: uppercase` on currency cells.
  - **BE:** uppercase currency in **generated output** — export CSV/XLSX currency column (`RedemptionExportService.generateCsv/generateXlsx`) + any BE-built label strings.
  - **Stored `currency_id` keys stay lowercase** — no migration; FE `getCurrency("cash")` lookups unaffected.
- **Files:** FE — enumerate currency-render sites (broad); BE `service/redemption/RedemptionExportService.java`.

### 6. Catalog pagination = 10 per page (client admin) — FE
- **Change:** `GlobalCatalogAdminPage.tsx` `pageSize: 20 → 10` (BE already allows ≤50).

---

## Part 2 — Client-owned catalog management (NOW CONFIRMED)

The ownership decision is final, so these two workstreams — previously deferred — are unblocked. They should ship **together** (enabling client-authored catalogs without isolation causes cross-tenant leakage).

### 2a. Permission migration — `catalog.manage` → CLIENT_ADMIN
- **Now:** `action.redemption.catalog.manage` is granted to Client Admin **only in the dev DB, not in any migration** (V12 seeds it PLATFORM/TENX-admin only). A fresh DB / `flyway clean` leaves Client Admin 403'd on the catalog UI.
- **Change:** new migration seeding `catalog.manage` for base role `CLIENT_ADMIN` in **BOTH** `client_role_permissions` AND `client_permission_grants` (mirror V12's `action.redemption.configure` pattern — seeding one table lets Layer-0 strip it). Correct V12's "PLATFORM scope only" comment to reflect the new intent.
- **Files:** new `V4x__seed_client_admin_catalog_manage.sql`; comment fix in `V12__seed_redemption_catalog_permissions.sql`.

### 2b. Client-owned catalog — `owner_client_id` (MODEL 2, pure client-owned — LOCKED 2026-07-22)
- **Model decision (LOCKED):** **Model 2 — pure client-owned.** No shared global/platform catalog. **Every catalog item belongs to exactly one client.** A client sees only its own items; there is no "enable a shared item for my tenant" step.
- **Now:** `RedemptionCatalogItem` has **no `client_id`** — one global/shared pool; seller visibility needs `isActive` (global) AND `ClientCatalogItemConfig.enabled` (per-tenant).
- **Requirement:** a client's catalog items are private to that client (its admin + partner admins + sellers only); no cross-tenant visibility at any layer.
- **Change — ownership + collapse the shared-catalog machinery:**
  - **Migration:** add **`owner_client_id UUID NOT NULL`** to `redemption_catalog_items` (every item owned by one client). **Existing rows** → backfill `owner_client_id` = **pushpendra@genicommunity.com's `client_id`** (LOCKED), then add the NOT NULL constraint. No `null`/global concept remains.
  - **Create:** stamp `owner_client_id = caller's clientId` (client admin creating their own item).
  - **Reads ("own-client only"):** seller browse (`RedemptionCatalogBrowseService`) and admin list (`RedemptionCatalogAdminController`/`Service`) filter `WHERE owner_client_id = :callerClientId`. Nothing else is ever visible.
  - **Writes ("own-client only"):** create / update / delete / toggle / image scoped to `owner_client_id = caller` — reject touching any other client's item (404/403).
  - **Redeem-time owner check (LOCKED — Q5 yes):** `RedemptionSubmissionService` fetches the item by id — add a guard that its `owner_client_id == the buyer's clientId`, else reject (prevents redeeming another client's item by guessing the id). This is the money-path isolation guard.
  - **Xoxoday `/sync` DISABLED (LOCKED — option a):** `XoxodaySyncJobService.submitSyncJob()` runs `@Async` (background thread — request `TenantContext`/`client_id` does NOT propagate) and writes items **globally** with no owner → under `owner_client_id NOT NULL` its `saveAll` would crash. It's a platform-wide concept that doesn't fit pure client-owned, and NON_CASH/Xoxoday is not the demo path. **Disable it** (gate the `/sync` endpoint + the async job off, or make it a no-op) — no per-client rework. Client admins create items manually instead. **FE:** also hide the Sync button / `SyncStatusBanner` trigger on `GlobalCatalogAdminPage` so it can't call a disabled endpoint.
  - **TENX-admin catalog view left as-is code-wise (legacy):** `GlobalCatalogAdminPage` / "Global" endpoints are **not** reworked or removed, but their *behavior* changes because the shared `listCatalogItems` query is now owner-scoped — a platform caller with no `client_id` gets an empty list. Acceptable; not invested in.
  - **Tests:** tenant-isolation — Client A's item never appears to Client B (browse + admin list); Client B cannot read/update/toggle/**redeem** it.

**Visibility control + activate/deactivate rework (DECIDED 2026-07-22, adjusted for Model 2):**
- **One visibility toggle per item, labeled "Active / Inactive"** (= `isActive`), controls whether that client's sellers see the item. **Created `isActive=false` (inactive/hidden) by default** — the admin flips it Active to show it. **The current Enable/Disable toggle is removed** — this Active/Inactive toggle is the *only* per-item control.
- **Under Model 2 the toggle maps to the item's own `isActive` flag** (the owning client controls it directly) — **not** `ClientCatalogItemConfig.enabled`. Since every item is owned by one client, the per-tenant enable gate is redundant: **browse visibility = `owner_client_id = caller AND isActive = true`** (drop the `config.enabled` requirement from browse).
- **Single gate = `isActive` everywhere (Option Y — LOCKED):** browse **and** `RedemptionSubmissionService` gate on `owner_client_id == caller AND isActive == true`. **Drop the `config.enabled` requirement from submission** (currently `config.filter(isEnabled).orElseThrow`) so an Active item is redeemable and an Inactive one is not — no two-gate drift. `RedemptionSubmissionService` still reads `ClientCatalogItemConfig` for **overrides** (`effectiveMin` / min-wallet-balance / processing mode) **if present** — overrides become optional, not a gate. Plus the new redeem-time owner guard.
- **`ClientCatalogItemConfig` KEPT for overrides only (Q6):** the table stays as a 1:1 owner-scoped extension for `processing-mode / min-transaction / min-wallet-balance / return-window` — **do NOT fold onto the item**. Its per-tenant *enable* role is **removed** (superseded by `isActive`). The **current Enable/Disable toggle** in the UI is **removed**. No auto-create-enabled dance needed (config is optional; absence = item defaults). The **regional matrix / geo** stays hidden — unaffected.
- **Remove the platform "Activate / Deactivate" button** from the client-admin view entirely — replaced by the single "Active / Inactive" toggle (= `isActive`). There is no separate global switch in Model 2.
- **FE:** `GlobalCatalogAdminPage.tsx` — client-admin catalog view shows one "Active/Inactive" toggle per row (= `isActive`), no Activate/Deactivate button, no per-tenant enable column.
- **Naming note:** UI label = "Active/Inactive"; underlying field = `RedemptionCatalogItem.isActive` (now the owning client's control).

---

## Suggested execution order (per-repo, tests included)

1. **Part 1 quick wins first** (unblocked, low-risk): items 1, 3, 6, then 2, 5 → FE + small BE commits.
2. **Item 4** (name search + export parity): BE + contract + FE.
3. **Part 2b** (owner_client_id + Model 2) — the largest slice: migration (backfill owner → genicommunity client), owner-scoped reads/writes, browse change (drop config.enabled), redeem-time owner guard in `RedemptionSubmissionService`, the "Active/Inactive" toggle + remove client-view Activate/Deactivate, isolation tests.
4. **Part 2a** (permission migration) — **must land together with 2b** (granting client admins `catalog.manage` before isolation exists = cross-tenant leak). Sequence it in the same merge as 2b, not before.

Group commits per repo (backend / contracts / frontend), explicit CRLF-safe paths. Naming caveat: the `Global*` names (`GlobalCatalogAdminPage`, `useGlobalCatalogItems`, `/catalog` "global" endpoints) become misleading under Model 2 — rename opportunistically or leave a note.

## Test plan

Conventions: **BE-unit** = JUnit + Mockito; **BE-int** = integration test against the real stack (DB); **FE** = Vitest + Testing Library. Each item lists the cases that must pass. Isolation cases (2b) are the critical ones — they gate the money path and cross-tenant privacy.

### Part 1

**Item 1 — newest catalog on top (BE)**
- T1.1 `listCatalogItems` returns items ordered by `createdAt DESC` (seed 3 items with distinct timestamps → assert newest first).
- T1.2 ordering holds with a category filter applied.
- T1.3 pagination: page 0 = newest 10, page 1 = older (no overlap, no gap).

**Item 2 — currency by category (FE)**
- T2.1 category=CASH → currency dropdown shows only `cash`, auto-selected.
- T2.2 category=NON_CASH → dropdown shows `points`/`credits`/`tickets`, **not** `cash`.
- T2.3 switch CASH→NON_CASH with `cash` selected → currency reset (invalid) ; switch NON_CASH→CASH → `cash` auto-selected.
- T2.4 submit a CASH item → payload `currencyId === "cash"`.

**Item 3 — hide whole Redemption group (FE)**
- T3.1 user WITH `module.redemption_store` → Redemption group visible with its permitted sub-items.
- T3.2 user WITHOUT `module.redemption_store` → **entire** Redemption group absent, even when they still hold `view_history` / `view_all_history` / `approve` / `view_analytics`.
- T3.3 gate present in **every** sidebar config that has a Redemption group (partner + client-admin).

**Item 4 — name filter + export parity (BE + FE)**
- T4.1 BE `findTenantHistory(userName="ali")` → only rows whose user first/last name matches (case-insensitive, partial); non-matches excluded.
- T4.2 BE `companyName="acme"` → only rows in the matching partner company.
- T4.3 BE both filters combined = AND; no filters = all tenant rows.
- T4.4 BE export ALL_TENANT with `userName`/`companyName` → exported rows match the filtered list exactly.
- T4.5 BE export PERSONAL/COMPANY scope **ignores** name filters (they don't apply there).
- T4.6 FE typing user name → request carries `userName`; company name → `companyName`; the Export dialog forwards the same name filters. **Update the existing `TenantTransactionHistoryPage.test`** — it asserts the UUID inputs being replaced.
- T4.7 BE full-name search: `userName="alice smith"` matches `CONCAT(firstName,' ',lastName)`; a user with no partner company still matches a `userName` filter and is excluded by any `companyName` filter.

**Item 5 — currency uppercase (FE + BE)**
- T5.1 FE currency **code** renders uppercase (`cash`→`CASH`) in catalog table + at least one history/analytics site.
- T5.2 FE currency **label** renders uppercase (`Cash`→`CASH`).
- T5.3 BE export CSV/XLSX currency column is uppercase.
- T5.4 stored-key lookup unaffected: `getCurrency("cash")` (lowercase) still resolves (no regression).

**Item 6 — pagination 10 (FE)**
- T6.1 catalog admin list requests `pageSize=10`.
- T6.2 with >10 items: exactly 10 shown, pagination control enabled; page 2 shows the remainder.

### Part 2a — permission migration
- T7.1 BE-int: fresh DB (`flyway clean` + migrate) → `CLIENT_ADMIN` role holds `catalog.manage` in **both** `client_role_permissions` AND `client_permission_grants`.
- T7.2 BE-int: a Client-Admin JWT reaches the catalog admin endpoints with **no 403** (regression: seeding only one table → Layer-0 strips it → 403).

### Part 2b — Model 2 client-owned + isolation (CRITICAL)
- T8.1 migration: existing rows backfilled `owner_client_id = genicommunity's client`; NOT NULL constraint added; no null rows remain.
- T8.2 create: Client A admin creates an item → `owner_client_id == A`; item created **hidden** (`isActive=false`).
- T8.3 **browse isolation:** Client A seller sees only A's items; Client B's items **never** appear. (+ the reverse.)
- T8.4 **admin-list isolation:** Client A admin sees only A's items.
- T8.5 **write isolation:** Client B admin cannot update / delete / toggle / image Client A's item → 404/403.
- T8.6 **redeem isolation (money path):** Client B user submitting a redemption for Client A's item id → rejected (owner-guard); reservation/ledger untouched.
- T8.7 visibility toggle: item hidden until "Active" (`isActive=true`) → then visible to that client's sellers; browse rule = `owner==caller AND isActive` (shows **without** any `config.enabled` row).
- T8.8 overrides optional: `RedemptionSubmissionService` applies `effectiveMin` / min-wallet-balance / processing-mode from `ClientCatalogItemConfig` **when present**; when absent, item defaults apply (no throw) — config is **not** a gate.
- **T8.9 (money path): an Active item IS redeemable** — owned + `isActive=true`, no `config.enabled` needed → submission succeeds (proves Option Y single-gate).
- **T8.10 (money path): an Inactive item is NOT redeemable** — owned + `isActive=false` → submission rejects (proves the "Active" toggle blocks redeem, not just browse; catches the two-gate drift).
- T8.11 sync disabled: `POST /sync` is a no-op/disabled (no catalog rows created, no NOT NULL crash); async job not triggered; FE Sync trigger hidden.
- T8.12 FE: client catalog view (`GlobalCatalogAdminPage`) shows one "Active/Inactive" toggle per row, **no** Activate/Deactivate button, **no** Enable/Disable toggle.
- T8.13 note: "created inactive" is **already** the current `createCatalogItem` behavior (`isActive=false`) — verify, don't re-implement.

### Regression guard (existing suites must stay green)
- BE `./gradlew test` (catalog/browse/submission/export/history) + the existing `RedemptionExportServiceTest`, `RoleSidebar.render.test.tsx`, `GlobalCatalogItemForm.test.tsx`, `GlobalCatalogAdminPage.test.tsx`, `TenantTransactionHistoryPage.test.tsx`, `AddCardForm.test.tsx`.

## Done when
- Part 1: each item verified (tsc + affected vitest green; BE `./gradlew test` green for 1/4/5).
- Part 2a: fresh-DB migrate grants Client Admin catalog access (no 403); both grant tables seeded.
- Part 2b (Model 2): a client's catalog items are invisible/unmodifiable/un-redeemable to other clients (isolation tests green); client admin creates/edits/toggles only their own items; a client-created item is hidden until its "Active" toggle is on; existing rows backfilled to genicommunity's client; Xoxoday `/sync` disabled (no crash); no platform Activate/Deactivate button in the client view.
