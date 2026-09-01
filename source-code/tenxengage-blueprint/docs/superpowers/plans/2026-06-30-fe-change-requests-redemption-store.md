# FE Team Change Requests — redemption-store roadmap

> **Captured:** 2026-06-30 · **Branch:** `roadmaps/redemption-store` · **Status:** ✅ intake closed (4 CRs) → plan drafted
>
> Source: changes suggested by the FE team, relayed by Pushpendra one at a time.
> **Plan:** [2026-06-30-redemption-fe-ux-enhancements-spec.md](2026-06-30-redemption-fe-ux-enhancements-spec.md) — spec-style, grounded in the actual FE code on `roadmaps/redemption-store`. All three CRs are **FE-only** (no BE/contract change).

## Context snapshot (as of capture)

- All 9 features (F-01→F-09) are merged into `roadmaps/redemption-store` across backend / frontend / contracts / blueprint (verified 2026-06-30).
- The roadmap branch is **not** yet merged into `main`; main is ahead by 25–74 unrelated commits per repo.
- F-03 vendor-routing stories (US-05 XTRM, US-06 Xoxoday, US-07 webhooks) remain `blocked` on external credentials.

---

## Change requests

### CR-01 — "Cancel Return" action should look/behave like an interactive button
- **Raw request:** "can the 'cancel return' look like an actually button or something that's interactive"
- **Screen:** Partner Portal → **Transaction History** → **My Returns** tab (`localhost:3000/redemption/history`), `Actions` column.
- **Current state (from screenshot):** "Cancel Return" renders as plain red text — reads as a static label, not obviously clickable (no button affordance, hover, or cursor cue).
- **Desired:** render it as an actual interactive control — button styling (or at minimum a clear link/hover/cursor affordance) so users recognize it as an action. Likely a destructive/secondary button variant given it's a cancel.
- **Area:** FE
- **Feature(s) touched:** F-06 redemption-returns (My Returns tab / Cancel Return action, US-01 manage partner returns) — surfaced inside the F-05 Transaction History page.
- **Open questions:** confirm desired variant (e.g. outline/ghost destructive button vs solid) and whether a confirm dialog is wanted on click (separate concern if not already present).

### CR-02 — Collapse the per-currency analytics grid into one currency-selectable section
- **Raw request:** "i have a lot of comments about this. we should combine it so it just shows all the cash numbers in one section, with a dropdown so they can choose a different currency to see all the numbers for that currency instead of showing everything for every currency all at once"
- **Screen:** Client Admin Portal → **Redemption Analytics** (`localhost:3000/redemption/admin/analytics`).
- **Current state (from screenshot):** the dashboard renders a 4-column grid (credits / points / cash / tickets) repeated across each metric band — **Redemption Rate**, **Outstanding Liability**, **Failed & Cancelled Rate** — i.e. every currency's numbers shown for every metric simultaneously (12 cards), plus a single global **Total Redemptions** card.
- **Desired:** combine into **one section per currency**. Add a **currency dropdown** (credits / points / cash / tickets); selecting a currency shows *all* its metrics (Redemption Rate + Outstanding Liability + Failed & Cancelled Rate) together, instead of the all-currencies-at-once grid. FE team has "a lot of comments" → expect follow-up sub-points / refinements on this one.
- **Area:** FE (layout/UX restructure — per-currency data already exists; this is a presentation change). May touch the **Export** behavior if export is currency-scoped.
- **Feature(s) touched:** F-07 redemption-analytics-basic (US-01 "View analytics dashboard").
- **Open questions:** (a) default currency on load? (b) does **Total Redemptions** stay global or also become per-currency? (c) does the **Export** follow the selected currency or stay all-currency? (d) FE flagged "a lot of comments" — gather the rest before finalizing.

### CR-03 — Consolidate all redemption nav items under one parent with sub-items
- **Raw request:** "we need to find a way to combine the left hand navigation items for all your redemption features into one navigation item with sub-items, we have too many main items in the navigation bar now"
- **Problem:** the redemption features each added their own **top-level** left-nav entry, so the sidebar now has too many main items. Observed across the two portals:
  - **Partner Portal** (e.g. Bob Partner): `Redemption Store`, `Transaction History` as separate top-level items.
  - **Client Admin Portal** (e.g. Alice Admin): `Redemption Store`, `Approval Queue`, `Tenant History`, `Analytics`, `Reporting` as separate top-level items.
- **Desired:** collapse these into a **single parent nav item** (e.g. "Redemption") with the individual screens as **sub-items** (collapsible/expandable group), reducing the number of main nav entries. Applies to both portals (role-aware: each role sees only its permitted sub-items).
- **Area:** FE (shared sidebar / nav config — information-architecture change; no BE/contract impact).
- **Feature(s) touched:** cross-cutting — F-02 (Redemption Store), F-04 (Approval Queue), F-05 (Transaction History / Tenant History), F-07 + F-08 (Analytics / Reporting). Each registered a nav entry that needs to move under the new parent.
- **Open questions:** (a) parent label — "Redemption" vs "Redemption Store"? (b) exact sub-item grouping & order per portal? (c) does the parent route anywhere on click or only expand? (d) keep role/permission gating so empty groups don't render? (e) any items that should stay top-level (e.g. is "Redemption Store" the partner storefront distinct enough to keep prominent)?

### CR-04 — Client Admin should not see/open "Redemption Store" (gating mismatch)
- **Raw request:** "client admin does not need to see Redemption Store, because he cant redeem … why is he seeing this and what should we do?"
- **Root cause (verified in BE seeds):** the nav item + route are gated on `module.redemption_store` — a coarse **MODULE_ACCESS umbrella** granted to PARTNER_SELLER, PARTNER_ADMIN **and CLIENT_ADMIN** (V8 / F-01). The actual redeem capability is a separate ACTION permission, `action.redemption.redeem` (PARTNER_SELLER) / `action.redemption.redeem_company` (PARTNER_ADMIN), **never granted to CLIENT_ADMIN** (V17). So the admin holds the umbrella → sees the link, but can't redeem. It's the **only** one of the 7 redemption nav items gated on the umbrella rather than a specific capability.
- **Decision:** **FE-only re-gate** the storefront to the redeem capability (no BE/migration change; admin keeps the umbrella for legit module endpoints).
- **Area:** FE.
- **Feature(s) touched:** F-02/F-03 storefront nav item + route; implemented alongside the CR-03 nav work (shares `NavItem` / `RoleSidebar` / `sidebarConfigs.ts`).
- **Open questions:** confirmed intent — admin should neither see nor open the storefront. (Resolved.)

<!--
Template per change — fill as each one arrives:

### CR-01 — <short title>
- **Raw request:** <verbatim what the FE team said>
- **Area:** <FE | BE | contracts | spec/docs | unclear>
- **Feature(s) touched:** <F-0x / story / N-A>
- **Clarifications / notes:** <anything I asked or inferred>
- **Open questions:** <if any>
-->

---

## Plan

_(to be drafted after intake is closed)_
