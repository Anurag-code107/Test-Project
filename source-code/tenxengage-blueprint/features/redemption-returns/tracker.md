# Tracker: Non-Cash Returns

_Source of truth for session-level execution status._
_Spec: [spec.md](spec.md) | Stories index: [stories.md](stories.md) | Foundation: [tasks/foundation.md](tasks/foundation.md)_

---

## Foundation

| # | Task | Status | Notes |
|---|---|---|---|
| F0 | `/generate-contracts redemption-returns` (in `../tenxengage-contracts/`) | done | Completed 2026-06-12 — generate-contracts session |
| F1 | Enums (`ReturnStatus`, `ReturnResolution`, `AuditResourceType.REDEMPTION_RETURN`) | done | f257176b |
| F2 | Flyway V25 — `redemption_returns` schema | done | 88055500 |
| F3 | Entity + Repository + Fixtures (`RedemptionReturn`, `RedemptionReturnRepository`, `RedemptionReturnFixtures`) | done | 484ff056 |
| F4 | Flyway V26 — permissions + feature flag seed | done | 7fb78624 |
| F5 | BE plumbing (`KafkaConfig`, `ReturnEvent`, `ReturnEventProducer`, `ReturnTimeoutScheduler`) | done | 26ed16df |

---

## Stories

| # | Title | Layers | Depends on | BE Status | FE Status | Notes |
|---|---|---|---|---|---|---|
| US-01 | Submit and manage partner returns | BE + FE | Foundation | done | done | BE: cd69642f FE: 706e68178361 active=1h 16m completed=2026-06-13T01:41:23Z |
| US-02 | Admin return review | BE + FE | US-01 | done | done | BE: b748bba06a27 active=53m completed=2026-06-13T01:18:09Z FE: 15d21c7bcb8b active=43m completed=2026-06-13T02:29:13Z |
| US-03 | Xoxoday vendor integration | BE | US-02 | done | N/A | BE: 687337ec |
| US-04 | RETURN_TIMED_OUT and manual resolution | BE + FE | F5, US-02 | done | done | BE: 315148640be7 active=41m completed=2026-06-13T06:43:18Z FE: 216a9e303b8f active=28m completed=2026-06-13T07:15:29Z [low] src/index.css — prefers-reduced-motion/animate-spin: add .animate-spin to prefers-reduced-motion block (advisory, non-blocking) |

---

## Cross-story Integration Tests

| # | Task | Status | Notes |
|---|---|---|---|
| IT | Write [test-plan.md](test-plan.md) integration tests | done | BE: 45354359 · 62 tests green · 8 classes · 3 prod fixes · FE: febbc2f · 2 specs scaffolded (8 tests test.skip — seed gap) · capture: 4d2ca6b · active=3h 37m completed=2026-06-13T11:59:43Z |

---

## Session Protocol

- **Claim:** set status to `in-progress` with your session ID before starting
- **Complete:** set status to `done` when the story's "Done when" checklist passes
- **Block:** set status to `blocked` with a reason note — do not leave a story in `in-progress` when stuck
- **One story per session:** do not claim multiple stories in the same session
- **FE sessions:** run in `../tenxengage-frontend/`; BE sessions run in `../tenxengage-backend/`
- **After all stories done:** open a feature → `main` PR in each repo

### Status values

`not-started` | `in-progress` | `done` | `blocked`
