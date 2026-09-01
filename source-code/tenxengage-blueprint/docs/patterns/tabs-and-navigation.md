# Pattern: tabs-and-navigation

## When this applies

Use this pattern when a feature introduces **tab navigation within a page**, a back-button pattern, or breadcrumbs. Signal: any spec with a "Settings > {Tab}" structure, a detail page with content tabs, or a wizard step flow without an accordion.

## Spec authoring guidance

- Choose **Tabs vs. accordion**: use Tabs when the user switches between peer views of the same entity; use accordion when the user walks through sequential steps in order (builder pattern). The two are not interchangeable.
- Specify whether tabs are URL-driven (tab state in query params) or UI-only (React state).
- Specify the back-button trigger: `PageBanner.onBack` for top-level pages; a within-page back arrow for detail-to-list navigation within a sub-panel.
- Do not use breadcrumbs and a back button at the same time for the same page — choose one.

## Implementation guidance

TBD — capture from:
- `src/pages/client-admin/ManageRewardsPage.tsx` — tabs navigation
- `src/components/PageBanner.tsx` — `onBack` back-button behavior

Sections to document:
- Tab styling: underline-style tabs (shadcn `Tabs` with default variant), not pill-style
- Tab trigger sizing: `text-sm font-medium`
- Tab content padding: `pt-4` or `pt-6` below the tab bar
- URL-driven tabs: `useSearchParams` for tab state so deep-linking works
- Active tab indicator: underline on the active tab trigger
- Back-button placement: always via `PageBanner.onBack`, never a standalone `<Button>` outside the banner
- Page-within-page back: small `<Button variant="ghost">` with `<ArrowLeft>` icon, above the sub-content

## Examples in codebase

- `../tenxengage-frontend/src/pages/client-admin/ManageRewardsPage.tsx` — tabs pattern
- `../tenxengage-frontend/src/components/PageBanner.tsx` — `onBack` renders back arrow before title

## Common gotchas

- **Consolidate per-feature nav items under one collapsible group — don't let each feature add its own top-level item.** When a multi-feature initiative (e.g. the redemption store: store, history, approvals, analytics, breakage, expiration) each registers a top-level sidebar entry, the primary nav bloats. Group them under a single `NavGroupConfig` parent. In `RoleSidebar`, a `NavSection` with **no `heading`** renders as a top-level collapsible group (the section renderer only draws `groups`), so you get "one parent + sub-items" without a section label and **without renderer changes**. Sub-items keep their own `permissionKey`/`anyPermission`, so role-awareness is automatic; the existing empty-group guard hides the parent when no sub-items are permitted. (redemption-store FE UX enhancements US-03, 2026-06-30.)
