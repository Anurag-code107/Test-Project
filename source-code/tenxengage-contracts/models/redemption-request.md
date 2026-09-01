# RedemptionRequest

Partner's redemption submission lifecycle record. Tracks the full lifecycle from submission through vendor fulfillment or failure. Tenant-isolated via `client_id` Hibernate filter with optimistic locking.

## Fields

| Field | Type | Required | Notes |
|---|---|---|---|
| `id` | UUID | Yes | Generated |
| `walletId` | UUID | Yes | FK → reward_wallets — wallet funds reserved from |
| `userId` | UUID | Yes | FK → users — partner who submitted |
| `catalogItemId` | UUID | Yes | FK → redemption_catalog_items — selected item |
| `amount` | decimal string | Yes | Redemption amount; must be > 0 |
| `currencyId` | string (max 50) | Yes | String currency ID — "cash", "points", "credits", "tickets"; matches wallet and catalog item |
| `walletType` | WalletType | Yes | `INDIVIDUAL` or `COMPANY` |
| `status` | RedemptionStatus | Yes | State machine field; see transitions below |
| `processingMode` | RedemptionProcessingMode | Yes | Captured at submission time; immutable |
| `category` | RedemptionCategory | Yes | `CASH` or `NON_CASH`; determines vendor routing (XTRM vs Xoxoday) |
| `reserveLedgerEntryId` | UUID | No | FK → ledger_entries — RESERVE entry created at submission |
| `debitLedgerEntryId` | UUID | No | FK → ledger_entries — DEBIT entry created on vendor confirmation |
| `releaseLedgerEntryId` | UUID | No | FK → ledger_entries — RELEASE entry created on failure or cancellation |
| `scheduledBatchDate` | date | No | Next scheduled batch processing date; BATCH mode only |
| `submittedAt` | datetime | Yes | ISO 8601 timestamp of partner submission |
| `processingStartedAt` | datetime | No | Timestamp when submitted to vendor |
| `completedAt` | datetime | No | Timestamp of vendor confirmation or failure |
| `createdAt` | datetime | Yes | Inherited from BaseEntity |
| `updatedAt` | datetime | Yes | Inherited from BaseEntity |

## Fields Never Exposed in API

| Field | Reason |
|---|---|
| `clientId` | Tenant isolation — resolved server-side via Hibernate filter |
| `vendorReferenceId` | Confidential — only returned in responses when status=COMPLETED |
| `failureReason` (raw) | Internal vendor text — mapped to generic user-friendly message in responses |
| `version` | Optimistic lock counter; internal |
| `deleted` | Soft delete flag; internal |

## Business Rules

- `amount` must be ≥ catalog item's effective `minTransactionAmount`; returns 422 on violation
- Available balance must remain ≥ tenant's minimum wallet balance threshold after reservation; returns 422
- In-flight count (`PENDING_APPROVAL` + `RESERVED` + `PROCESSING` for caller) must be < `maxInFlightRedemptions`; returns 409
- RESERVE ledger entry written atomically with `RedemptionRequest` persist in a single transaction
- INSTANT mode: calls `RedemptionOrchestrationService.initiateVendorSubmission()` immediately after reservation
- BATCH mode: `scheduledBatchDate` computed from tenant's `batchCadence`; status = `RESERVED`
- APPROVAL_REQUIRED mode: status = `PENDING_APPROVAL`; not submitted to vendor until F-04 approval
- `currencyId` must match both the catalog item's `currencyId` and the wallet's `currencyId`; returns 422 on mismatch

## Status Transitions

```
[SUBMISSION] → PENDING_APPROVAL     mode=APPROVAL_REQUIRED; balance reserved
[SUBMISSION] → RESERVED             mode=BATCH; balance reserved; awaiting batch
[SUBMISSION] → RESERVED→PROCESSING  mode=INSTANT; balance reserved then vendor called
PENDING_APPROVAL → RESERVED         F-04 approver approves; vendor submission begins
RESERVED → PROCESSING               batch processor runs or INSTANT transition
PROCESSING → COMPLETED              vendor webhook confirms; DEBIT ledger entry written
PROCESSING → FAILED                 vendor webhook signals failure; RELEASE ledger entry written
PENDING_APPROVAL → CANCELLED        F-04 approver rejects; RELEASE ledger entry written
RESERVED → CANCELLED                admin/system cancels; RELEASE ledger entry written
```

`COMPLETED`, `FAILED`, and `CANCELLED` are terminal states — any incoming transition returns 409.

Concurrent transitions resolved via `@Version` (optimistic locking) — conflict returns 409.

## Multi-Tenancy

- Tenant-scoped via `client_id` (Hibernate `@Filter` — never exposed in responses)
- `clientId` resolved from `tenantValidator.getCurrentClientId()` (JWT) — never from spoofable headers

## Relationships

- `@ManyToOne` → `RewardWallet` (FK: `wallet_id`) — non-lazy for balance checks
- `@ManyToOne` → `RedemptionCatalogItem` (FK: `catalog_item_id`) — for item metadata
