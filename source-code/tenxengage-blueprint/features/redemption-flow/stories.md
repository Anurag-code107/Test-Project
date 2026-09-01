# Stories Index — redemption-flow

_One row per user story. Each row maps to a `stories/US-NN-*.md` file that is the self-contained execution unit for that story._
_Every story row must produce at least one Playwright E2E test in its story file (BE-only stories use unit/integration tests instead)._

---

## Stories Table

| US | Title | Layers | Actor | Touches Entities | Seed | Depends on | Can Parallel With | Story File |
|---|---|---|---|---|---|---|---|---|
| US-01 | Submit personal wallet redemption | BE + FE | PARTNER_SELLER | RedemptionRequest | F-03.S-01 | Foundation | — | [stories/US-01-submit-personal-wallet-redemption.md](stories/US-01-submit-personal-wallet-redemption.md) |
| US-02 | Submit company wallet redemption | BE + FE | PARTNER_ADMIN | RedemptionRequest | F-03.S-02 | Foundation, US-01 | — | [stories/US-02-submit-company-wallet-redemption.md](stories/US-02-submit-company-wallet-redemption.md) |
| US-03 | Batch redemption processor | BE | Scheduler | RedemptionRequest | F-03.S-05 | Foundation, US-01 | — | [stories/US-03-batch-redemption-processor.md](stories/US-03-batch-redemption-processor.md) |
| US-04 | Partner redemption notifications | BE | Kafka consumer | RedemptionRequest | F-03.S-07 | Foundation, US-01 | US-02 | [stories/US-04-partner-redemption-notifications.md](stories/US-04-partner-redemption-notifications.md) |
| US-05 | Route cash redemptions to XTRM | BE | RedemptionOrchestrationService | RedemptionRequest | F-03.S-03 | Foundation, US-01 | US-06 | [stories/US-05-route-cash-redemptions-xtrm.md](stories/US-05-route-cash-redemptions-xtrm.md) |
| US-06 | Route non-cash redemptions to Xoxoday | BE | RedemptionOrchestrationService | RedemptionRequest | F-03.S-04 | Foundation, US-01 | US-05 | [stories/US-06-route-noncash-redemptions-xoxoday.md](stories/US-06-route-noncash-redemptions-xoxoday.md) |
| US-07 | Process vendor webhooks idempotently | BE | XTRM / Xoxoday (inbound) | RedemptionRequest, RedemptionWebhookEvent | F-03.S-06 | Foundation, US-05, US-06 | — | [stories/US-07-process-vendor-webhooks.md](stories/US-07-process-vendor-webhooks.md) |

_Layers values: `BE + FE` (full stack), `BE` (no user-visible UI — scheduled job, webhook, Kafka consumer), `FE` (no new endpoints)._

---

## Dependency graph

```
Foundation (F0 → F1 → F2 → F3 → F4 → F5)
└── US-01 (personal submit — BE+FE) ← done
    ├── US-02 (company submit — BE+FE)      ← done
    ├── US-03 (batch processor — BE done)
    ├── US-04 (notifications — BE done)
    ├── US-05 [BLOCKED: XTRM profile incomplete]  ──┐
    └── US-06 [BLOCKED: Xoxoday creds missing]     ┤
                                                     └── US-07 [BLOCKED: needs US-05 + US-06]
```

---

## Parallelism notes

_Stories that can run concurrently (once their deps are met):_
- US-04 and US-02 — disjoint actors (Kafka consumer vs. controller endpoint)
- US-05 and US-06 — disjoint vendors (XTRM vs. Xoxoday), parallel when both unblocked

_Stories that must run sequentially:_
- US-02 after US-01 — same `RedemptionRequestController`; US-01 establishes the controller structure
- US-07 after US-05 + US-06 — webhook shape is vendor-specific; needs vendor API confirmed

---

## Story count

| Total stories | BE-only | FE-only | BE + FE |
|---|---|---|---|
| 7 | 5 | 0 | 2 |

_Done: US-01, US-02, US-03, US-04_
_Blocked (XTRM profile + Xoxoday credentials): US-05, US-06, US-07_
