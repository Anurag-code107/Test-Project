# Stories Index — redemption-history

_One row per user story. Each row maps to a `stories/US-NN-*.md` file that is the self-contained execution unit for that story._
_Every story row must produce at least one Playwright E2E test in its story file._

---

## Stories Table

| US | Title | Seed | Layers | Actor | Touches Entities | Depends on | Can Parallel With | Story File |
|---|---|---|---|---|---|---|---|---|
| US-01 | View personal redemption history | F-05.S-01 | BE + FE | PARTNER_SELLER, PARTNER_ADMIN | RedemptionRequest | Foundation | US-02 BE, US-03 BE, US-04 BE | [stories/US-01-view-personal-history.md](stories/US-01-view-personal-history.md) |
| US-02 | View company redemption history | F-05.S-02 | BE + FE | PARTNER_ADMIN | RedemptionRequest | Foundation, US-01 FE | US-01 BE, US-03 BE, US-04 BE | [stories/US-02-view-company-history.md](stories/US-02-view-company-history.md) |
| US-03 | Export personal transaction data | F-05.S-03 | BE + FE | PARTNER_SELLER, PARTNER_ADMIN | RedemptionExportJob | Foundation, US-01 FE | US-01 BE, US-02 BE, US-04 BE | [stories/US-03-export-transactions.md](stories/US-03-export-transactions.md) |
| US-04 | View and export tenant-wide history | F-05.S-04 | BE + FE | CLIENT_ADMIN | RedemptionRequest, RedemptionExportJob | Foundation, US-03 FE | US-01 BE, US-02 BE, US-03 BE | [stories/US-04-tenant-history.md](stories/US-04-tenant-history.md) |

_Layers values: `BE + FE` (full stack), `BE` (no user-visible UI), `FE` (no new endpoints)._

---

## Dependency graph

```
Foundation (F1 → F2 → F3, F4)
├── US-01 BE  (enhance RedemptionRequestController + RedemptionHistoryService.getPersonalHistory)
│   └── US-01 FE  (TransactionHistoryPage + Table + FilterBar + DetailSheet)
│       ├── US-02 FE  (company tab + useCompanyRedemptions)
│       └── US-03 FE  (ExportDialog + useTriggerExport + useExportJob)
│           └── US-04 FE  (TenantTransactionHistoryPage + useTenantRedemptions + reuse ExportDialog)
├── US-02 BE  (RedemptionHistoryController /company — parallel with US-01 BE)
├── US-03 BE  (RedemptionExportController + RedemptionExportService — parallel with US-01 BE)
└── US-04 BE  (RedemptionHistoryController /all + RedemptionAdminHistoryResponse — parallel with US-01 BE)
```

---

## Parallelism notes

_BE sessions that can run concurrently (all touch different controllers/services):_
- US-01 BE, US-02 BE, US-03 BE, US-04 BE — independent; run in parallel after Foundation

_FE sessions that must run sequentially:_
- US-01 FE first (creates `TransactionHistoryPage`)
- US-02 FE + US-03 FE in parallel after US-01 FE (add company tab and ExportDialog to the existing page)
- US-04 FE after US-03 FE (reuses `ExportDialog` from US-03)

---

## Story count

| Total stories | BE-only | FE-only | BE + FE |
|---|---|---|---|
| 4 | 0 | 0 | 4 |
