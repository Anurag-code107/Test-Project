# Stories Index — reward-balance-expiration

_One row per user story. Each row maps to a `stories/US-NN-*.md` file that is the self-contained execution unit for that story._
_Every BE+FE story produces at least one Playwright E2E test in its story file; BE-only stories are verified by unit/integration tests + T1._

> **Roadmap:** redemption-store F-09 (Phase 2). Decomposed from the `/decompose-brd` feature brief — every story records its `seed_id`.

---

## Stories Table

| US | Title | Layers | Seed | Actor | Touches Entities | Depends on | Can Parallel With | Story File |
|---|---|---|---|---|---|---|---|---|
| US-01 | Configure balance expiration policy | BE + FE | F-09.S-01 | CLIENT_ADMIN | BalanceExpirationPolicy | Foundation | US-04 | [stories/US-01-configure-expiration-policy.md](stories/US-01-configure-expiration-policy.md) |
| US-02 | Advance-expiry notification engine | BE | F-09.S-02 | SYSTEM (scheduled) | BalanceExpiryNotice, BalanceExpirationPolicy, RewardWallet (r), LedgerEntry (r) | Foundation, US-01 | US-04 | [stories/US-02-advance-expiry-notification.md](stories/US-02-advance-expiry-notification.md) |
| US-03 | Balance expiry execution + policy-change cancellation | BE | F-09.S-03 | SYSTEM (scheduled) + CLIENT_ADMIN | BalanceExpiryNotice, RewardWallet, LedgerEntry | Foundation, US-01, US-02 | US-04 | [stories/US-03-expiry-execution-and-cancellation.md](stories/US-03-expiry-execution-and-cancellation.md) |
| US-04 | Breakage report + CSV export | BE + FE | F-09.S-04 | CLIENT_ADMIN | LedgerEntry (r) | Foundation | US-02, US-03 | [stories/US-04-breakage-report-and-export.md](stories/US-04-breakage-report-and-export.md) |

_Layers values: `BE + FE` (full stack), `BE` (no user-visible UI — scheduled batch), `FE` (none here)._

---

## Dependency graph

```
Foundation (F0 contracts → F1 enums → F2 migrations → F3 entities/repos/fixtures, F4 permissions/flag/notifications)
├── US-01 (configure policy, BE+FE)
│   └── US-02 (advance-notice batch, BE)            ← needs enabled policies
│       └── US-03 (expiry execution + cancel, BE)   ← needs notices; wires cancel-on-relax into US-01's PUT
└── US-04 (breakage report + export, BE+FE)         ← only needs F1 (EXPIRY type) + ledger reads
```

---

## Parallelism notes

_Can run concurrently (disjoint surfaces, shared only Foundation):_
- US-04 runs in parallel with US-01/US-02/US-03 — it only reads `ledger_entries` (`entry_type = EXPIRY`) and touches no policy/notice write path. (Its tests seed EXPIRY ledger entries directly via fixtures.)

_Must run sequentially (shared entity / forward wiring):_
- US-02 after US-01 — the warn sweep needs enabled `BalanceExpirationPolicy` rows.
- US-03 after US-02 — the expire phase consumes `NOTIFIED` notices; US-03 also wires `cancelPendingForPolicy()` into US-01's `upsertPolicy`.

---

## Story count

| Total stories | BE-only | FE-only | BE + FE |
|---|---|---|---|
| 4 | 2 | 0 | 2 |

---

## Flow-level Completeness Audit

_Records the story-level completeness probe run during Phase 1.5 of `/create-stories`. Distinct from the spec-level probe in `/create-spec`._

| # | Gap | Resolution | Story / Note |
|---|---|---|---|
| 1 | `GET /policies` returns only saved policies (≤4), but the admin must be able to enable a currently-unconfigured currency type | Added AC to US-01 | AC-6 — form enumerates all four currency types from `config/currencies.ts`; unconfigured currencies render as disabled/unconfigured defaults |
| 2 | A policy disabled/relaxed after the warn phase could still expire if the expire phase only checked policy state at warn time | Added AC to US-03 | AC-4 — expire phase re-verifies the governing policy is still enabled at execution; skips notices whose policy is no longer enabled |

_All other applicable dimensions were already covered by the spec-level Functional Completeness Audit (10 dimensions, FR-09.1–FR-09.11)._
