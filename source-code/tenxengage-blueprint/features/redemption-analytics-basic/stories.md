# Stories Index — redemption-analytics-basic

_One row per user story. Each row maps to a `stories/US-NN-*.md` file that is the self-contained execution unit for that story._
_Every story row must produce at least one Playwright E2E test in its story file._

---

## Stories Table

| US | Title | Layers | Actor | Touches Entities | Depends on | Can Parallel With | Story File |
|---|---|---|---|---|---|---|---|
| US-01 | View analytics dashboard | BE + FE | CLIENT_ADMIN | RewardWallet, LedgerEntry, RedemptionRequest | Foundation | — | [stories/US-01-view-analytics-dashboard.md](stories/US-01-view-analytics-dashboard.md) |
| US-02 | Export unredeemed balances CSV | BE + FE | CLIENT_ADMIN | RewardWallet | Foundation + US-01 BE (BE); US-01 FE (FE) | — | [stories/US-02-export-unredeemed-balances.md](stories/US-02-export-unredeemed-balances.md) |

_Layers values: `BE + FE` (full stack), `BE` (no user-visible UI), `FE` (no new endpoints)._

---

## Dependency graph

```
F0 (contracts — FIRST)
└── F1 → F2 → F3 (Foundation sequential)
              └── US-01 BE
                  ├── US-01 FE
                  └── US-02 BE  ← sequential: shares RedemptionAnalyticsController + Service files
                      └── US-02 FE  ← depends on US-01 FE (ExportConfirmDialog wired into RedemptionAnalyticsPage)
```

---

## Parallelism notes

_Stories that must run sequentially (share implementation files):_
- US-02 BE after US-01 BE — both write to `RedemptionAnalyticsController.java` and `RedemptionAnalyticsService.java`; parallel sub-branches would cause merge conflicts
- US-02 FE after US-01 FE — `ExportConfirmDialog` is wired into `RedemptionAnalyticsPage` built by US-01 FE

_No stories in this feature can run in parallel — the controller and page are shared across both._

---

## Story count

| Total stories | BE-only | FE-only | BE + FE |
|---|---|---|---|
| 2 | 0 | 0 | 2 |

---

## Flow-level Completeness Audit

| # | Gap | Resolution | Story / Note |
|---|---|---|---|
| 1 | `DateRangeFilter` has no error copy for > 24-month selection (FR-07.4 says "picker prevents it" but specifies no message) | Added verbatim microcopy AC to US-01 | AC-7 — "Date range cannot exceed 24 months" |

_Spec-level probe (run during `/create-spec`) already covered: lifetime vs period denominator (FR-07.1), layout decision (new page), empty state variants (FR-07.8), export delivery model (FR-07.9), scaling risk (NFR). No additional flow-level gaps found beyond item 1 above._
