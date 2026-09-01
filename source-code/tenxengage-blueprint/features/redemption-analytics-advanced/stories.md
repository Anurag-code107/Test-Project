# Stories Index — redemption-analytics-advanced

_One row per user story. Each row maps to a `stories/US-NN-*.md` file that is the self-contained execution unit for that story._
_Every story row must produce at least one Playwright E2E test in its story file._

---

## Stories Table

| US | Title | Layers | Actor | Touches Entities | Depends on | Can Parallel With | Story File |
|---|---|---|---|---|---|---|---|
| US-01 | View item breakdown | BE + FE | CLIENT_ADMIN | `mv_item_redemption_breakdown` | Foundation, US-05 | US-02, US-03, US-04, US-06, US-07 | [stories/US-01-item-breakdown.md](stories/US-01-item-breakdown.md) |
| US-02 | View segment breakdown | BE + FE | CLIENT_ADMIN | `mv_segment_redemption_breakdown` | Foundation, US-05 | US-01, US-03, US-04, US-06, US-07 | [stories/US-02-segment-breakdown.md](stories/US-02-segment-breakdown.md) |
| US-03 | View time-to-first-redemption | BE + FE | CLIENT_ADMIN | `mv_time_to_first_redemption` | Foundation, US-05 | US-01, US-02, US-04, US-06, US-07 | [stories/US-03-time-to-first-redemption.md](stories/US-03-time-to-first-redemption.md) |
| US-04 | View redemption rate trend | BE + FE | CLIENT_ADMIN | `mv_redemption_rate_trend` | Foundation, US-05 | US-01, US-02, US-03, US-06, US-07 | [stories/US-04-redemption-trend.md](stories/US-04-redemption-trend.md) |
| US-05 | Advanced tab shell, filter bar, refresh status | BE + FE | CLIENT_ADMIN | `analytics_mv_refresh_log` | Foundation, F5 | — | [stories/US-05-tab-shell-filter-refresh.md](stories/US-05-tab-shell-filter-refresh.md) |
| US-06 | Liability trend chart and CSV export | BE + FE | CLIENT_ADMIN | `mv_liability_trend` | Foundation, US-05 | US-01, US-02, US-03, US-04, US-07 | [stories/US-06-liability-trend-export.md](stories/US-06-liability-trend-export.md) |
| US-07 | Failure breakdown | BE + FE | CLIENT_ADMIN | `mv_failure_mode_breakdown` | Foundation, US-05 | US-01, US-02, US-03, US-04, US-06 | [stories/US-07-failure-breakdown.md](stories/US-07-failure-breakdown.md) |

_Layers values: `BE + FE` (full stack), `BE` (no user-visible UI), `FE` (no new endpoints)._

_"Touches Entities" determines sequential vs parallel: two stories touching the same MV table must run sequential for BE (same controller + service file). Two stories touching different MV tables can run in parallel for FE once their deps are met._

> **BE sessions for US-01 through US-04, US-06, US-07 are sequential** — all add methods to the same `RedemptionAdvancedAnalyticsController` and `RedemptionAdvancedAnalyticsService`. FE sessions are independently parallelizable (different hooks and components per story).

---

## Dependency graph

```
Foundation (F0 → F1 → F2 → F3, F4 → F5)
└── US-05 (tab shell + filter bar + refresh status)
    ├── US-01 (item breakdown)
    ├── US-02 (segment breakdown)
    ├── US-03 (time-to-first-redemption)
    ├── US-04 (redemption rate trend)
    ├── US-06 (liability trend + CSV export)
    └── US-07 (failure breakdown)
```

---

## Parallelism notes

_Stories that can run concurrently for FE sessions (each owns a separate hook + component):_
- US-01, US-02, US-03, US-04, US-06, US-07 — disjoint FE components once US-05 tab shell is done

_Stories that must run sequentially for BE sessions (add methods to the same controller + service class):_
- US-05 first (creates `RedemptionAdvancedAnalyticsController` class and `RedemptionAdvancedAnalyticsService` class)
- US-01 through US-04, US-06, US-07 each add methods to those existing classes — one BE session at a time

---

## Story count

| Total stories | BE-only | FE-only | BE + FE |
|---|---|---|---|
| 7 | 0 | 0 | 7 |

---

## Flow-level Completeness Audit

_Records the story-level completeness probe run during Phase 1.5 of `/create-stories`._

| # | Gap | Resolution | Story / Note |
|---|---|---|---|
| 1 | Tab default state not specified — which tab is active on initial page load | Added AC to US-05 | AC-2: Overview tab active by default on initial load |
| 2 | Section loading and error states not defined per analytics section | Added AC to each section story | AC-6 (US-01), AC-4 (US-02), AC-4 (US-03), AC-4 (US-04), AC-6 (US-06), AC-4 (US-07): loading skeleton + error + Retry |
| 3 | Tier/region filter dropdowns not defined for when tenant has no redemption data | Added AC to US-05 | AC-4: dropdowns disabled with "No data available" when segment breakdown returns empty |
