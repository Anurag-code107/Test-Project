# Feature Roadmap: Redemption Store Integration

> **Slug**: `redemption-store` · **BRD source**: `BRD_PDF.pdf · v1.0 Draft · April 2026`
> **Digest**: [digest.md](digest.md) · **Annex**: [digest-annex.md](digest-annex.md) · **Generated**: 2026-05-05 via `/decompose-brd`
>
> Per-feature briefs in [features/](features/). PM-friendly export at [backlog-seeds.csv](backlog-seeds.csv).

## At a glance

| F-NN | Name | Primary Persona | Phase | Blockers | Brief |
|---|---|---|---|---|---|
| F-01 | Wallet & Ledger Foundation | `PARTNER_SELLER` | 1 | — | [Brief](features/F-01-redemption-store.md) |
| F-02 | Redemption Catalog | `CLIENT_ADMIN` | 1 | F-01 | [Brief](features/F-02-redemption-store.md) |
| F-03 | Redemption Flow | `PARTNER_SELLER` | 1 | F-01, F-02 | [Brief](features/F-03-redemption-store.md) |
| F-04 | Redemption Approval Queue | `ACTIVITY_APPROVER` | 1 | F-03 | [Brief](features/F-04-redemption-store.md) |
| F-05 | Transaction History & Export | `PARTNER_SELLER` | 1 | F-03 | [Brief](features/F-05-redemption-store.md) |
| F-06 | Non-Cash Returns | `PARTNER_SELLER` | 1 | F-03, F-04, F-05 | [Brief](features/F-06-redemption-store.md) |
| F-07 | Basic Redemption Analytics | `CLIENT_ADMIN` | 1 | F-03, F-05 | [Brief](features/F-07-redemption-store.md) |
| F-08 | Advanced Redemption Analytics | `CLIENT_ADMIN` | 2 | F-07 | [Brief](features/F-08-redemption-store.md) |
| F-09 | Balance Expiration | `CLIENT_ADMIN` | 2 | F-01 | [Brief](features/F-09-redemption-store.md) |

## Recommended start: F-01

F-01 (Wallet & Ledger Foundation) is the bedrock — the dual wallet model and immutable ledger engine must exist before any other feature can be built or specced. The persistent balance nav widget in F-01 is also a high-visibility Phase 1 deliverable. Start here to unblock F-02 through F-07.

## Strategic notes

1. **⚠️ Platform Admin cross-tenant access (riskiest unknown for F-02)**: Platform Admin features are confirmed for `tenxengage-frontend` (main app), but all platform APIs are tenant-scoped via `X-Client-Subdomain`. Platform Admin must operate cross-tenant. Resolve before /create-spec for F-02: does the global catalog API live in `tenxengage-admin-backend` (called by main FE), or in main backend with a special cross-tenant permission? This decision shapes F-02 spec significantly.

2. **⚠️ EXPLICIT DEFERRAL FROM BRD v1 ACCEPTANCE: "Analytics filter by partner tier, region, and date range"** (source: "Acceptance Criteria Summary for v1 Launch") — Phase 1 F-07 delivers redemption rate by currency type + unredeemed balance + date-range filter. Tier and region breakdowns deferred to Phase 2 F-08.

3. **Return approval timeout gap**: BRD §8.5 doesn't specify a TTL for APPROVED returns awaiting Xoxoday confirmation. /create-spec for F-06 must define fallback behavior.

4. **ADR-01 open (company wallet min approvers)**: Defaulted to single approver in F-04. Escalate if product requires quorum approval.

5. **Batch scheduling boundary**: Phase 1 = `batchCadence` config field + backend batch job. Phase 2 = dedicated scheduler status UI. Phase 1 UX for batchCadence is a simple daily/weekly selector in the tenant catalog config (F-02).

6. **⚠️ PREREQUISITE: NEW ROLE 'TenXEngage Platform Admin'** — Requires Flyway migration + seed constant before F-02 can be implemented. Only role with `action.redemption.catalog.manage`.

7. **Extension not rebuild**: §4 confirms substantial existing infrastructure. Spec authors must verify whether new ledger entry types require enum additions to existing TransactionType or if a new enum is introduced.

## Open ADRs (blocking)

| ADR | Decision | Owner | By when | Blocks |
|---|---|---|---|---|
| ADR-01 | ✅ Single approver for Phase 1; quorum deferred to future phase (Pushpendra, 2026-05-26) | Product | Before F-04 spec | F-04 |
| ADR-03 | ✅ Cap at 10 per partner, configurable per client (`maxInFlightRedemptions`) | Vijay | 2026-05-21 | F-03 |

## Phase 1 features

- **F-01** Wallet & Ledger Foundation — Dual wallet model, immutable ledger, earning integrations, balance nav widget. → [Brief](features/F-01-redemption-store.md) · `/create-spec redemption-store F-01`
- **F-02** Redemption Catalog — Global catalog management (Platform Admin) + tenant + regional config (Client Admin) + currency-aware browsing. → [Brief](features/F-02-redemption-store.md) · `/create-spec redemption-store F-02`
- **F-03** Redemption Flow — Personal + company wallet redemption, XTRM + Xoxoday routing, all 3 processing modes, vendor webhooks. → [Brief](features/F-03-redemption-store.md) · `/create-spec redemption-store F-03`
- **F-04** Redemption Approval Queue — Client Admin / Approver reviews and approves/rejects APPROVAL_REQUIRED redemptions and return requests. → [Brief](features/F-04-redemption-store.md) · `/create-spec redemption-store F-04`
- **F-05** Transaction History & Export — Paginated, filterable history for users and Client Admin; CSV/XLSX export. → [Brief](features/F-05-redemption-store.md) · `/create-spec redemption-store F-05`
- **F-06** Non-Cash Returns — Partner submits return, admin approves, Xoxoday confirms, wallet credited. → [Brief](features/F-06-redemption-store.md) · `/create-spec redemption-store F-06`
- **F-07** Basic Redemption Analytics — Redemption rate by currency type, unredeemed balance report, date-range filter. → [Brief](features/F-07-redemption-store.md) · `/create-spec redemption-store F-07`

## Phase 2 features

- **F-08** Advanced Redemption Analytics — Full breakdowns by item/tier/region/cohort, time-to-first-redemption, SLA breach monitoring. → [Brief](features/F-08-redemption-store.md) · `/create-spec redemption-store F-08`
- **F-09** Balance Expiration — Configurable expiry policy per currency type, expiry notifications, breakage reporting. → [Brief](features/F-09-redemption-store.md) · `/create-spec redemption-store F-09`
