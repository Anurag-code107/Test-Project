# Spec / Plan — Redemption FE UX Enhancements

> **Status:** ✅ implemented — US-01–04 merged to `features/redemption-fe-ux-enhancements` (FE + blueprint); all unit tests + `tsc -b` green; **R1 real-stack permission smoke PASSED 2026-06-30** (Client Admin: no storefront in nav + `/redemption-store` blocked; partner: storefront present). MRs open → `roadmaps/redemption-store`.
> **Date:** 2026-06-30 · **Author:** Pushpendra (relaying FE team) + Claude
> **Base branch:** `roadmaps/redemption-store` · **Repos:** frontend only (BE / contracts: **no change**)
> **Intake source:** [2026-06-30-fe-change-requests-redemption-store.md](2026-06-30-fe-change-requests-redemption-store.md) (CR-01 … CR-04)

---

## 1. Overview

Four post-merge UX refinements to the already-shipped redemption features, raised by the FE team. All four are **frontend-only** — verified against the code on `roadmaps/redemption-store`:

| Story | CR | Change | Layer | Risk |
|---|---|---|---|---|
| US-01 | CR-01 | "Cancel Return" → real button affordance | FE | Very low (styling only) |
| US-02 | CR-02 | Analytics: per-currency grid → one section + currency dropdown | FE | Low–medium (layout/state) |
| US-03 | CR-03 | Consolidate 7 redemption nav items under one collapsible parent | FE | Medium (shared sidebar / IA) |
| US-04 | CR-04 | Re-gate "Redemption Store" to redeem capability (hide from Client Admin) | FE | Low (permission gate; bundled with US-03) |

**No backend or contract changes required** (confirmed):
- Analytics already returns per-currency arrays from `GET /redemption/analytics` → dropdown is a client-side filter.
- Cancel Return mutation (`useCancelReturn`) + confirm dialog already wired.
- Sidebar already supports collapsible groups (`NavGroupConfig`) — used today by Reporting & Settings.

---

## 2. Branch & delivery strategy

- **One FE feature branch** cut from the roadmap branch:
  `features/redemption-fe-ux-enhancements`  ←  `roadmaps/redemption-store`
- Four stories (US-01/02/03/04; US-04 is small and bundles into the US-03 nav work since they share the same files), each on a local `work/redemption-fe-ux-enhancements-US-0x` sub-branch, squash-merged into the feature branch after approval — same model as prior features.
- Tests authored per story (Vitest component + Playwright E2E, mocked) — **mirrors the existing redemption FE test pattern**; `tsc -b` + lint clean before each merge.
- Final integration check (T1) across all four, then one FE MR: `features/redemption-fe-ux-enhancements → roadmaps/redemption-store`.
- **BE branch: not needed.** (Section 6 lists the one scenario that *would* require BE, deferred.)
- Small blueprint doc-sync (Section 7) so the source-of-truth specs reflect the new UX.

---

## 3. US-01 — "Cancel Return" interactive button (CR-01)

**Current state**
- `src/components/redemption-returns/MyReturnsTab.tsx` (~L201–210) renders Cancel Return as
  `<Button variant="ghost" className="h-7 text-xs text-destructive hover:text-destructive hover:bg-destructive/10">` — functional (onClick → `setCancelTargetId`, confirm `AlertDialog`, `useCancelReturn` mutation all wired) but visually reads as plain red text.
- The detail panel `ReturnDetailSheet.tsx` (~L176) already uses the correct `variant="destructive"`.
- Button primitive: `@/components/ui/button` (variants: `default | destructive | outline | secondary | ghost | link`).

**Target**
- Give the table-cell action a clear button affordance. **Recommended:** `variant="outline"` + `text-destructive` (bordered, compact, reads as a button without shouting in a dense table) — or `variant="destructive"` solid if the FE team prefers it to match the detail sheet. Keep `size="sm"`, the spinner, `disabled` state, and `aria-label`.

**Files touched**
- `src/components/redemption-returns/MyReturnsTab.tsx` (variant + className only).

**Acceptance criteria**
- AC-1: Cancel Return renders with a visible button boundary (border or filled bg), pointer cursor, and hover state — recognizably a control, not text.
- AC-2: Existing behavior unchanged — click still opens the confirm dialog; confirming still calls the cancel mutation, shows the success toast, and refreshes the list; 409 still shows "This return can no longer be cancelled".
- AC-3: Loading (`isCancelling`) shows the spinner and disables the control; `aria-label` preserved.
- AC-4: Both the table action and the detail-sheet action clearly read as buttons — they need not use the identical variant (the table may use `outline` for density while the sheet keeps solid `destructive`; final variant is §8 #1). *(R5)*

**Tests**
- **Vitest** (`MyReturnsTab` test): asserts the action is a `role="button"` with the new variant class, has the `aria-label`, is disabled + shows spinner while cancelling, and that clicking opens the confirm dialog. Extend the existing returns component test if present.
- **Playwright (mocked)**: in the existing redemption-returns spec, assert Cancel Return is clickable as a button and the cancel happy-path + 409 path still pass.

---

## 4. US-02 — Analytics single-currency section with dropdown (CR-02)

> **Scope:** the Analytics page has two tabs — **Overview** (Basic, F-07, the per-currency grid in the screenshot) and **Advanced** (F-08, `AdvancedAnalyticsTab` with its own filter bar + breakdown tables). CR-02 restructures the **Overview tab only**. The Advanced tab is **not** in scope (its layout isn't a per-currency card grid). Confirm in §8.

**Current state**
- Page: `src/pages/redemption/analytics/RedemptionAnalyticsPage.tsx` (route `/redemption/admin/analytics`, App.tsx L229).
- Cards: `src/components/redemption-analytics/{RedemptionRateCard,UnredeemedBalanceCard,FailedCancelledRateCard,TotalCountCard,DateRangeFilter,ExportConfirmDialog}.tsx`.
- Hook: `useRedemptionAnalytics(dateFrom, dateTo)` → `getSummary` → `GET /redemption/analytics`.
- Response is **already per-currency**:
  ```
  { dateWindow, redemptionRates: CurrencyTypeRateDto[], unredeemedBalances: CurrencyTypeBalanceDto[],
    failedCancelledRates: CurrencyTypeRateDto[], totalRedemptionCount: RedemptionCountDto }
  ```
  `currencyId` ∈ {CASH, POINTS, CREDITS, TICKETS}. Today the page maps all currencies × 3 bands = 12 cards.
- Reusable `Select` primitive exists at `@/components/ui/select`; currency config/labels at `src/config/currencies.ts` (`getCurrency`).

**Target**
- Replace the all-currencies grid with **one currency section** controlled by a **currency `Select`**:
  - Dropdown options = currencies present in the response, derived from the **union** of `redemptionRates` + `unredeemedBalances` + `failedCancelledRates` `currencyId`s (a currency may appear in one array but not another), labeled via `getCurrency()`. *(R4)*
  - Selecting a currency shows that currency's three metric cards together: Redemption Rate, Outstanding Liability, Failed & Cancelled Rate.
  - **Total Redemptions** card stays **global** and always visible (it's currency-agnostic — `totalRedemptionCount`).
  - Preserve the existing date-range filter and Export button.

**Recommended decisions** (confirm in §8)
- **Default currency:** first currency in display order `[points, cash, credits, tickets]` that has `hasActivity`, else the first present. (Request literally referenced cash, but defaulting to the most-active currency is friendlier — happy to pin to cash.)
- **Export:** stays **global / all-currency** for now (current `/redemption/analytics/export` is not currency-scoped). Per-currency export is the one item that *would* need BE work — deferred (§6).
- Persist selection in URL query (`?currency=`) so it survives refresh/sharing (optional, recommended).

**Files touched**
- `RedemptionAnalyticsPage.tsx` (add currency state + Select, restructure layout).
- Possibly a small new `CurrencySelect` wrapper in `src/components/redemption-analytics/` (optional).
- The three currency cards likely need no change (they already take a single currency's DTO); the page just stops looping over all currencies.

**Acceptance criteria**
- AC-1: A currency dropdown renders; options = currencies present in the response, human-labeled.
- AC-2: On load, a sensible default currency is selected and its 3 metric cards render.
- AC-3: Changing the dropdown swaps all 3 metric cards to the selected currency without a refetch (client-side).
- AC-4: Total Redemptions remains visible and unchanged across currency switches.
- AC-5: `hasActivity = false` for the selected currency renders the existing "No redemptions in this period" empty state per card.
- AC-6: Date-range filter and Export continue to work; export still downloads all-currency CSV.
- AC-7: **Export is clearly labeled as all-currency** (e.g. "Export all balances" or a tooltip) so it isn't mistaken for exporting only the selected currency. *(R3)*
- AC-8: When the response has **no currency data at all** (e.g. a brand-new tenant), the section renders a clear empty state rather than an empty dropdown. *(R4)*
- AC-9: No console errors; `tsc -b` clean.

**Tests**
- **Vitest** (`RedemptionAnalyticsPage` test, mocked hook): default currency selected on load; switching currency updates the 3 cards (assert values from the mock DTOs); Total Redemptions invariant; empty-state when `hasActivity=false`; **no-data empty state** (all arrays empty); **dropdown options derived from the union** of the three arrays; Export labeled all-currency.
- **Playwright (mocked via `page.route`)**: dashboard loads with default currency; switch via dropdown → cards update; Total card unchanged; Export dialog still opens and triggers download. Follows the existing analytics E2E pattern.

---

## 5. US-03 — Consolidate redemption nav under one parent (CR-03)

**Current state**
- Config: `src/components/layout/sidebars/sidebarConfigs.ts` (`primaryItems` flat array + `sections` with groups).
- Renderer: `RoleSidebar.tsx` (types `NavItem`, `NavGroupConfig`, `NavSection` at L37–74), `SidebarNavItem.tsx`, `SidebarFlyout.tsx`.
- **Collapsible groups already supported** (Reporting, Settings) — animated expand/collapse + collapsed-state flyout.
- Seven redemption items currently sit at **top level** in `primaryItems`, each permission-gated:

  | Label | Route | Icon | permissionKey |
  |---|---|---|---|
  | Redemption Store | `/redemption-store` | ShoppingBag | `module.redemption_store` |
  | Approval Queue | `/redemption/approval-queue` | ClipboardList | `action.redemption.approve` |
  | Transaction History | `/redemption/history` | History | `action.redemption.view_history` |
  | Tenant History | `/redemption/admin/history` | Building2 | `action.redemption.view_all_history` |
  | Analytics | `/redemption/admin/analytics` | LineChart | `action.redemption.view_analytics` |
  | Balance Expiration | `/settings/redemption/balance-expiration` | Timer | `action.redemption.expiration.configure` |
  | Breakage | `/redemption/breakage` | FileBarChart | `action.redemption.expiration.view_breakage` |

- Gating: `filterByPermission()` keeps an item only if `(!permissionKey || can(permissionKey)) && (!featureKey || has(featureKey))`. Portal label derived from module permissions.

**Target**
- Collapse the 7 items into **one `NavGroupConfig`** parent — label **"Redemption"**, icon `ShoppingBag` — with the 7 as sub-items. Role-awareness is automatic: each sub-item keeps its `permissionKey`, so a partner sees only Redemption Store + Transaction History; a client admin sees the admin set. **The group must not render if no sub-items survive permission filtering** (add/confirm this guard).

**Recommended decisions** (confirm in §8)
- **Placement:** keep it at the **top level** (where the items are today) by extending `primaryItems` to accept `NavGroupConfig` and teaching the `primaryItems.map()` renderer to render a group (model after the existing section-group renderer). Alternative (lower effort, slightly different look): move it into a new `sections` entry titled "Redemption". Recommend the top-level group to preserve prominence and match "one navigation item with sub-items".
  - ⚠️ **Critical-path risk (R2):** the top-level-group path needs renderer changes in `RoleSidebar` **and** for `SidebarFlyout` + active-state logic to handle a group at the `primaryItems` level (today groups live only in `sections.groups`). **Spike this first**; if it balloons, fall back to the `sections`-entry alternative — same end-user result, much smaller blast radius. This is the gating risk for the US-03 estimate.
- **Parent click:** expand/collapse only (no navigation), matching Reporting/Settings.
- **`activePrefixes`:** `["/redemption", "/redemption-store", "/settings/redemption"]` so the parent highlights on any redemption route.
- **Sub-item order:** Redemption Store → Transaction History → Tenant History → Approval Queue → Analytics → Breakage → Balance Expiration (storefront/history first, admin/ops next, config last). Tweakable.
- **Balance Expiration** (route under `/settings/...`) is included in the Redemption group for cohesion; confirm it shouldn't instead stay under Settings.

**Files touched**
- `src/components/layout/sidebars/sidebarConfigs.ts` (move items into a group).
- `src/components/layout/sidebars/RoleSidebar.tsx` (if extending `primaryItems` to accept a group + empty-group guard).
- `SidebarFlyout.tsx` / `SidebarNavItem.tsx` only if needed to render a top-level group consistently.

**Acceptance criteria**
- AC-1: A single "Redemption" parent appears in the sidebar; the 7 former top-level items are now its sub-items.
- AC-2: Sub-items are permission-gated exactly as before (partner vs client-admin vs approver each see only their permitted subset).
- AC-3: The Redemption parent is hidden entirely when the user has none of the sub-item permissions.
- AC-4: Expanding shows sub-items; the parent highlights as active when on any redemption route; the active sub-item is highlighted.
- AC-5: Collapsed sidebar shows the group's flyout with the same sub-items.
- AC-6: All existing redemption routes remain reachable and unchanged; no route changes.
- AC-7: `tsc -b` clean; no other nav items affected (Reporting/Settings/etc. unchanged).

**Tests**
- **Vitest** (sidebar tests, mocking `can()`/`has()`): renders one Redemption group; sub-item set varies correctly for partner vs client-admin permission mocks; group hidden when no permitted sub-items; active highlighting on a redemption route; flyout renders sub-items in collapsed mode. **Regression (R6):** assert the full nav for at least one role (Home / Incentives / Reporting / Settings render unchanged) so the shared sidebar isn't disturbed.
- **Playwright (mocked auth/permissions)**: as partner → Redemption group shows Store + Transaction History; as client admin → shows admin sub-items; clicking a sub-item navigates to the right route; top-level nav count is reduced.

---

## 5b. US-04 — Re-gate "Redemption Store" to the redeem capability (CR-04)

**Problem (verified in BE seeds)**
- The `Redemption Store` nav item and the `/redemption-store` route are both gated on `module.redemption_store` — a coarse **MODULE_ACCESS umbrella** that V8 (F-01) grants to PARTNER_SELLER, PARTNER_ADMIN **and CLIENT_ADMIN**.
- The real redeem capability is a separate ACTION permission: `action.redemption.redeem` (PARTNER_SELLER) / `action.redemption.redeem_company` (PARTNER_ADMIN) — **never granted to CLIENT_ADMIN** (V17).
- So CLIENT_ADMIN sees + can open the storefront despite being unable to redeem. It is the **only** one of the 7 redemption nav items gated on the umbrella instead of a specific capability (the other six are correctly action-gated).

**Target (FE-only — no BE/migration change)**
- Gate the storefront on the redeem capability (either personal or company wallet):
  1. **`NavItem` type** (`RoleSidebar.tsx`): add `anyPermission?: string[]`; `filterByPermission()` shows the item if the user holds the existing `permissionKey` **and** (no `anyPermission` **or** `can()` is true for at least one entry).
  2. **`sidebarConfigs.ts`** Redemption Store item: replace `permissionKey: "module.redemption_store"` with `anyPermission: ["action.redemption.redeem", "action.redemption.redeem_company"]`.
  3. **`App.tsx`** `/redemption-store` route: change `<ProtectedRoute permission="module.redemption_store">` → `anyPermission={["action.redemption.redeem","action.redemption.redeem_company"]}` (ProtectedRoute already supports `anyPermission`). `/redemption/confirmation/:id` shares this route block — it is the redeem-result page, so it correctly inherits the same gate.
- Admins keep `module.redemption_store` for the module endpoints that legitimately use it (catalog browse, wallet reads) — only the storefront surface is removed for them.
- **Reconciliation with US-03:** this **supersedes US-03's "keeps its `permissionKey`"** for the Store item specifically — the Store sub-item is gated by `anyPermission`, not `permissionKey`.

**Acceptance criteria**
- AC-1: CLIENT_ADMIN no longer sees "Redemption Store" in the nav, and `/redemption-store` (and `/redemption/confirmation/:id`) are blocked/redirected for them.
- AC-2: PARTNER_SELLER (personal redeem) and PARTNER_ADMIN (company redeem) still see and can open the storefront.
- AC-3: No change to any backend permission, role seed, or API.
- AC-4: The new `anyPermission` field doesn't affect other nav items (those without it behave exactly as before).

**Tests**
- **Vitest** (sidebar, mocking `can()`): Redemption Store hidden for a CLIENT_ADMIN permission set; shown for PARTNER_SELLER (redeem) and for PARTNER_ADMIN (redeem_company only); items lacking `anyPermission` unaffected.
- **Route guard / E2E (mocked)**: admin navigating to `/redemption-store` is blocked/redirected; partner reaches the storefront.
- **Real-stack smoke (REQUIRED — R1, see §9):** against the local stack, Client Admin login shows **no** Redemption Store in nav and `/redemption-store` blocked; partner login shows it. Mocked tests prove FE filter logic only — the gate's correctness depends on the real 5-layer permission resolution (cf. the F-08 V31 Layer-0 surprise that mocks missed).

---

## 6. Out of scope / deferred

**Non-nav surfaces the feature set introduced — disposition (none get consolidated by CR-03):**

| Surface | Feature | Disposition |
|---|---|---|
| Redemption Catalog config tab (Settings → Platform, `?tab=redemption-catalog`) | F-02 | **No change** — not a nav item, not in any CR. |
| "My Returns" tab (within Transaction History) | F-06 | **Modified in place by CR-01 only** (button restyle); tab not moved/restructured. |
| "Advanced" analytics tab (`AdvancedAnalyticsTab`) | F-08 | **Out of CR-02 scope** — only the Overview tab is restructured. |
| Redemption Confirmation page (`/redemption/confirmation/:id`) | F-03 | **No change** — flow result page, no nav entry. |
| Rewards Balances home-dashboard widget | F-01 | **No change** — home widget, not a sidebar item. |

**Deferred:**
- **Per-currency analytics export** (CSV scoped to selected currency) — would require a BE change to `/redemption/analytics/export` (add a `currencyType` param). Deferred; export stays all-currency. Revisit only if the FE team confirms (then a BE branch + contract bump is required).
- **Advanced analytics tab rework** — only revisit if the FE team asks (§8 decision 8).
- **Catalog nav discoverability** — optionally add a "Catalog" deep-link sub-item to the new Redemption group (§8 decision 9); default is to leave catalog access under Settings.

**Untouched:**
- No changes to redemption business logic, routes, permissions, or APIs.
- F-03 vendor-routing (US-05/06/07) remains separately blocked — unrelated to these CRs.

---

## 7. Blueprint doc-sync (source-of-truth upkeep)

So specs stay authoritative (small, after FE approval):
- `features/redemption-analytics-basic/spec.md` — note the dashboard's single-currency + dropdown presentation (US-02).
- `features/redemption-returns/spec.md` — note Cancel Return button affordance (US-01).
- Add a short IA note (nav consolidation) — likely `PROJECT-CONTEXT.md` or a `docs/patterns/` entry, since nav grouping is cross-feature.

---

## 8. Decisions to confirm before implementation

1. **CR-01 variant:** outline + red text *(recommended)* vs solid `destructive`.
2. **CR-02 default currency:** most-active *(recommended)* vs pinned to cash.
3. **CR-02 export:** keep global *(recommended)* vs per-currency (pulls in BE — §6).
4. **CR-02 URL persistence** of selected currency: yes *(recommended)* / no.
5. **CR-03 placement:** top-level group *(recommended)* vs new "Redemption" section.
6. **CR-03 parent label** "Redemption" and **sub-item order** as proposed.
7. **CR-03 Balance Expiration** under Redemption *(recommended)* vs left under Settings.
8. **CR-02 Advanced tab:** exclude — restructure Overview tab only *(recommended)* vs also rework the Advanced tab.
9. **CR-03 Catalog discoverability:** leave catalog under Settings *(recommended)* vs add a "Catalog" deep-link sub-item to the new Redemption group.

---

## 9. Sequencing

1. Confirm §8 decisions.
2. Cut `features/redemption-fe-ux-enhancements` from `roadmaps/redemption-store`.
3. US-01 (smallest, fastest) → US-02 → US-03 **+ US-04 together** (same nav files); each: implement → Vitest + Playwright → `tsc`/lint → approve → squash-merge.
4. T1 integration sanity (all four together; nav + analytics + returns smoke) **+ a real-stack permission smoke (R1)**: verify against the local stack that Client Admin loses the storefront (nav + route) while partner roles keep it — mocked tests can't prove the live 5-layer gate.
5. Blueprint doc-sync (§7).
6. FE MR → `roadmaps/redemption-store`.
