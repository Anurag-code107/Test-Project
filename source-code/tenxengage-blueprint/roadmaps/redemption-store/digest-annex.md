---
slug: redemption-store
---

# BRD Digest Annex: Redemption Store Integration

> **Advisory only.** This file preserves BRD-stated technical artifacts (entity names, event names, API operations, RBAC matrix, error codes, recommended modules). These are **not** authoritative — they reflect what the BRD writer specified about implementation. `/create-spec` reconciles them against the actual codebase (contracts repo, existing entities, platform conventions).
>
> Spec authors: read this for context, not for naming. If the codebase already has a different name, use the codebase's name.

---

## Event vocabulary (advisory)

*(Source: "Event Architecture")*

| Event name | Trigger | Consumers (BRD-stated) |
|---|---|---|
| `redemption_requested` | Partner submits a redemption request | Notifications, audit, PAS pipeline (Phase 2) |
| `redemption_approved` | Approver approves APPROVAL_REQUIRED redemption | Notifications |
| `redemption_rejected` | Approver rejects pending redemption | Notifications |
| `redemption_processing` | Redemption sent to vendor | Audit |
| `redemption_completed` | Vendor confirms fulfillment | Notifications, audit, PAS pipeline (Phase 2) |
| `redemption_failed` | Vendor reports failure | Notifications, audit |
| `redemption_cancelled` | Redemption cancelled | Notifications, audit |
| `return_requested` | Partner submits return request | Notifications (to admin/approver) |
| `return_approved` | Client Admin approves return | Notifications (to partner) |
| `return_confirmed` | Xoxoday confirms return | Notifications (to partner), audit |
| `return_rejected` | Admin or vendor rejects return | Notifications (to partner), audit |
| `return_cancelled` | Partner cancels pending return | Audit |
| `wallet_balance_updated` | Any balance mutation | Analytics, persistent nav widget |
| `wallet_below_minimum_threshold` | Available balance drops below client-configured minimum | Notifications (to partner) |
| `vendor_webhook_received` | Inbound webhook from XTRM or Xoxoday | Audit |
| `vendor_webhook_failed` | Webhook processing failure | Alerts, dead-letter queue |

---

## Data-model entity inventory (advisory)

*(Source: "Data Model")*

| Entity | BRD description | Owns / belongs to feature |
|---|---|---|
| `RewardWallet` | Per-currency balance record for an individual user or partner company | F-01 |
| `LedgerEntry` | Immutable record of a single balance movement | F-01 |
| `RedemptionCatalogItem` | Global catalog item managed by Platform Admin | F-02 |
| `ClientRedemptionConfig` | Tenant-level override of catalog item settings, thresholds, and return windows | F-02 |
| `RedemptionTransaction` | A single redemption request lifecycle record | F-03 |
| `RedemptionReturn` | A return request linked to a completed RedemptionTransaction | F-06 |

---

## API surface (advisory)

*(Source: "API Surface" §12)*

**Wallet APIs**
| Operation | HTTP | BRD description |
|---|---|---|
| Get current user wallets | `GET /api/v1/wallets/me` | Get current user's wallet balances per currency type |
| Get company wallets | `GET /api/v1/wallets/company/{companyId}` | Get company wallet balances per currency type |

**Catalog APIs**
| Operation | HTTP | BRD description |
|---|---|---|
| Browse catalog | `GET /api/v1/redemption/catalog` | Tenant-enabled catalog items filtered by user's held currencies |
| Get tenant config | `GET /api/v1/redemption/config` | Get tenant redemption configuration |
| Update tenant config | `PUT /api/v1/redemption/config` | Update tenant redemption configuration |

**Redemption APIs**
| Operation | HTTP | BRD description |
|---|---|---|
| Submit personal redemption | `POST /api/v1/redemption/requests` | Submit redemption from personal wallet |
| Submit company redemption | `POST /api/v1/redemption/requests/company` | Submit redemption from company wallet |
| List own transactions | `GET /api/v1/redemption/requests` | Paginated own transaction history |
| Get transaction detail | `GET /api/v1/redemption/requests/{id}` | Single transaction detail |
| List all tenant transactions | `GET /api/v1/redemption/requests/all` | Client Admin all-tenant view |
| Approve redemption | `POST /api/v1/redemption/requests/{id}/approve` | Approve pending request |
| Reject redemption | `POST /api/v1/redemption/requests/{id}/reject` | Reject pending request |
| Export transactions | `GET /api/v1/redemption/requests/export` | CSV or XLSX export |

**Returns APIs**
| Operation | HTTP | BRD description |
|---|---|---|
| Submit return | `POST /api/v1/redemption/requests/{id}/returns` | Submit return for completed non-cash redemption |
| List own returns | `GET /api/v1/redemption/returns` | Paginated own return requests |
| Approve return | `POST /api/v1/redemption/returns/{id}/approve` | Client Admin approves return |
| Reject return | `POST /api/v1/redemption/returns/{id}/reject` | Client Admin rejects return |
| Cancel return | `DELETE /api/v1/redemption/returns/{id}` | Partner cancels own pending return |

**Webhook APIs**
| Operation | HTTP / Auth | BRD description |
|---|---|---|
| XTRM webhook | `POST /api/v1/redemption/webhook/xtrm` · HMAC-SHA256 | Cash payout status updates |
| Xoxoday webhook | `POST /api/v1/redemption/webhook/xoxoday` · HMAC-SHA256 | Non-cash order status updates |

---

## RBAC permission matrix (advisory)

*(Source: "Permissions and Feature Flags" §10.2)*

| Permission Key | Description | BRD-stated default roles |
|---|---|---|
| `module.redemption_store` | Access to Redemption Store module | Partner Seller, Partner Admin, Client Admin |
| `action.redemption.redeem` | Initiate redemption from personal wallet | Partner Seller |
| `action.redemption.redeem_company` | Initiate redemption from company wallet | Partner Admin |
| `action.redemption.view_history` | View own redemption transaction history | Partner Seller, Partner Admin |
| `action.redemption.view_all_history` | View all tenant redemption history | Client Admin |
| `action.redemption.export` | Export redemption transaction data | Partner Seller, Partner Admin, Client Admin |
| `action.redemption.configure` | Configure tenant catalog and thresholds | Client Admin |
| `action.redemption.approve` | Approve or reject pending redemption requests | Client Admin, Approver |
| `action.redemption.return.request` | Submit a return request | Partner Seller, Partner Admin |
| `action.redemption.return.review` | Approve or reject return requests | Client Admin, Approver |
| `action.redemption.catalog.manage` | Manage the global redemption catalog | TenXEngage Platform Admin |

**Feature flag:**
| Flag key | Starter | Professional | Enterprise |
|---|---|---|---|
| `redemption_store` | Enabled | Enabled | Enabled |

---

## Recommended backend modules (advisory)

*(Source: "Recommended Backend Modules" §23)*

- `reward_wallet` — wallet model, per-currency balance management, balance eligibility checks
- `ledger_engine` — LedgerEntry persistence, balance mutation logic, idempotency enforcement
- `redemption_catalog` — global catalog management, client configuration, currency-aware catalog API
- `redemption_orchestration` — request submission, vendor routing, processing mode handling, balance reservation
- `xtrm_integration` — XTRM API client, cash payout submission, webhook handler
- `xoxoday_integration` — Xoxoday API client, non-cash order placement, catalog sync, webhook handler, return handoff
- `returns_engine` — return request lifecycle, Client Admin approval flow, Xoxoday confirmation handling
- `redemption_reporting` — transaction history, export generation, Client Admin reporting

---

## Technical reliability claims (advisory)

- All balance mutations must be idempotent and transactional; LedgerEntry records are immutable once written; wallet totals must always be consistent with the ledger sum.
- Webhook processing must be idempotent — duplicate delivery must not double-process a ledger mutation.
- Background job retries with exponential backoff for transient vendor API failures.
- Dead-letter queue for unprocessable webhook events.
- Optimistic locking on wallet balance fields to prevent concurrent-update inconsistency.
- No payment credentials, bank account numbers, or card details stored in TenXEngage systems.
- All vendor communication over TLS 1.2+.
- HMAC-SHA256 signature verification on all inbound webhook requests; unauthenticated requests rejected with 401.
