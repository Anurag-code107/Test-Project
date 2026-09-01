# Stories Index — redemption-approval-queue

_One row per user story. Each row maps to a `stories/US-NN-*.md` file that is the self-contained execution unit for that story._
_Every story row must produce at least one Playwright E2E test in its story file._

---

## Stories Table

| US | Title | Layers | Actor | Touches Entities | Depends on | Can Parallel With | Story File |
|---|---|---|---|---|---|---|---|
| US-01 | View approval queue | BE + FE | CLIENT_ADMIN, ACTIVITY_APPROVER | RedemptionRequest | Foundation | — | [stories/US-01-view-approval-queue.md](stories/US-01-view-approval-queue.md) |
| US-02 | Approve redemption | BE + FE | CLIENT_ADMIN, ACTIVITY_APPROVER | RedemptionRequest | Foundation, US-01 | US-03 FE only | [stories/US-02-approve-redemption.md](stories/US-02-approve-redemption.md) |
| US-03 | Reject redemption | BE + FE | CLIENT_ADMIN, ACTIVITY_APPROVER | RedemptionRequest | Foundation, US-01, US-02 BE | US-02 FE | [stories/US-03-reject-redemption.md](stories/US-03-reject-redemption.md) |

_Layers values: `BE + FE` (full stack), `BE` (no user-visible UI), `FE` (no new endpoints)._

---

## Dependency graph

```
Foundation (F1 → F2 → F3, F4)
└── US-01 (view queue — BE + FE)
    └── US-02 BE (approve endpoint + service)
        └── US-03 BE (reject endpoint + service — same controller/service file, sequential)
    US-02 FE ∥ US-03 FE  ← both start when US-01 FE is done; different components + hooks
```

---

## Parallelism notes

_US-02 BE and US-03 BE must run sequentially — both add methods to the same `RedemptionApprovalController` and `RedemptionApprovalService` created in US-01._

_US-02 FE and US-03 FE can run in parallel — `ApproveConfirmDialog` and `RejectDialog` are independent components; `useApproveRedemption` and `useRejectRedemption` are independent hooks._

---

## Story count

| Total stories | BE-only | FE-only | BE + FE |
|---|---|---|---|
| 3 | 0 | 0 | 3 |
