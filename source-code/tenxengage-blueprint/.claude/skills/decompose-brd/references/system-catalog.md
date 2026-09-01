# System Catalog (canonical reference for Phase 0.5)

This file is the **single source of truth Phase 0.5 reads** when reconciling BRD personas and partner-company types against the live system. Hardcoded on purpose — Phase 0.5 does not parse Flyway migrations or Java seed constants at runtime.

**Keep this file in sync** with the upstream code/migrations. If you change roles or partner types in the backend, update this file in the same PR. The "Last verified" date at the bottom is your reminder.

---

## System roles

Four system roles exist. Stored as `ClientRole.baseRoleName` strings (no enum). The `roleType` column is `INTERNAL` (vendor-side) or `EXTERNAL` (partner-side).

| baseRoleName | roleType | Description |
|---|---|---|
| `CLIENT_ADMIN` | INTERNAL | Full access to all platform features and tenant settings. |
| `ACTIVITY_APPROVER` | INTERNAL | Reviews and approves activity submissions from partner users. |
| `PARTNER_ADMIN` | EXTERNAL | Manages partner-organization users and incentive participation on behalf of the partner company. |
| `PARTNER_SELLER` | EXTERNAL | Participates in incentives, submits claims, and qualifies deals. |

---

## Partner company types

Three partner types exist. Stored as JSONB metadata on `partner_companies` under the key `"Partner Type"`. The lookup values live in a `data_object_fields` row (no enum).

| Value | Notes |
|---|---|
| `Reseller` | Resells vendor products to end customers. |
| `Distributor` | Distributes vendor products through downstream resellers. |
| `OEM` | Original equipment manufacturer integrating vendor products. |

---

## Out of scope for Phase 0.5

Phase 0.5 does **not** check anything beyond the two tables above. The following are explicitly out of scope and belong to per-feature data-model decisions in `/create-spec`:

- Partner classification dimensions other than `Partner Type` (e.g., Partner Tier, Region, Segment).
- Custom (per-tenant) roles created beyond the four system roles.
- Permission strings, scopes, or RBAC matrix entries.

---

## Last verified

- **Date**: 2026-05-02
- **Verified against**: `tenxengage-backend@main`
- **Update protocol**: When a backend PR changes `client_roles` system rows or the `Partner Type` lookup values, update the table(s) above in the same PR (or immediately after) and bump the date.
