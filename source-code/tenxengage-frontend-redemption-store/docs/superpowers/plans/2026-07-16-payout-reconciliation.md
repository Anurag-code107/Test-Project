# Payout reconciliation (missed-webhook recovery) + BatchTransfer realignment

**Status:** EXECUTED (2026-07-16) — Option B built (reconciliation cron + real BatchTransfer). Backend
compiles; full unit suite green. Not yet run against live XTRM; V39 not yet applied to the dev DB; uncommitted.
**Branch:** `features/redemption-xtrm-payout-enhancement`
**Enhancement to:** F-03 redemption payout.

## Problem

CASH payouts finalize **only** via the XTRM webhook (`RedemptionWebhookService.applyCompletion`).
There is **no reconciliation and no status-poll fallback** today, so if the webhook is missed (our
server down when XTRM fires, and XTRM doesn't re-deliver), the redemption is stuck in PROCESSING
forever, funds stay RESERVED (never debited/released), and only a manual fix recovers it.

## Approach

A `@Scheduled` reconciliation cron polls XTRM for the status of in-flight CASH payouts and settles them
through the **existing webhook settlement path** (idempotent, row-locked), so a late webhook and the cron
can never double-settle. **3-day cap** from dispatch; past that, stop polling + alert for manual review
(never auto-release — the money may actually have moved).

## Identifying in-flight ("initiated but not complete") rows

Not a single status — mode-dependent:
- INSTANT / BATCH dispatched → `PROCESSING`
- APPROVAL_REQUIRED dispatched → `RESERVED` (it never flips to PROCESSING)

Robust predicate (mirrors the webhook's own `dispatchable` guard):

```sql
WHERE dispatch_attempted_at IS NOT NULL                 -- vendor was actually called
  AND status IN ('PROCESSING','RESERVED')               -- non-terminal
  AND category = 'CASH'                                  -- XTRM only (NON_CASH = Xoxoday, separate)
  AND wallet_type = 'INDIVIDUAL'                         -- seller/admin; COMPANY deferred
  AND dispatch_attempted_at >= now() - interval '3 days' -- within the cap window
```

Rows past the 3-day window that are still non-terminal are counted + logged/alerted (manual review), not polled.

## Status vocabulary → outcome (config-driven, so unknown vendor strings never block)

- **Success set** (→ COMPLETE): `Success`, `Completed`, `Released`. *(confirmed)*
- **Failed set** (→ FAIL + release the reservation): `Failed`. *(confirmed)*
- **Everything else** (`Pending`, `Processing`, `Accepted`, `Queued`, or any unknown string) → still in-flight →
  keep polling → 3-day cap → manual review. We act only on the known success/failed strings; nothing is guessed.
- Sets are config properties: `redemption.reconciliation.success-statuses` (default the three above),
  `.failed-statuses` (default `Failed`) — tunable without a code change.

## Phase 1 — single-mode (INSTANT / APPROVAL)  ·  no dispatch changes needed

**API:** `POST /API/v4/Wallet/GetUserWalletTransactionDetails`
```json
{ "GetUserTransactionDetails": { "Request": {
    "IssuerAccountNumber": "<issuer>", "TransactionID": "<vendorReferenceId>", "UserID": "<recipient PAT>" } } }
```
- `TransactionID` = our stored **`vendorReferenceId`** (the `PaymentTransactionId` TransferFund returned). **ASSUMPTION — confirm this is the id this API accepts.**
- `UserID` = payee PAT (`partner_redemption.recipientUserId`, keyed by userId+clientId).
- **Parse:** `...Result.Field[0][]` → find `{"Name":"Transaction Status"}` → `Value` → map via the status sets.
- **Limitation:** rows with `vendorReferenceId IS NULL` (XTRM-unreachable-at-dispatch case) have no TransactionID to query → they fall to the cap → manual. Poll query for single-mode therefore also requires `vendor_reference_id IS NOT NULL`.

## Phase 2 — batch  ·  requires realigning the batch dispatch first

Our current `XtrmApiClientImpl.batchTransfer` does **not** match the live API (wrong envelope, field names,
single-rail assumption, no `CustomerBatchId`) — batch payouts would be rejected by real XTRM. So Phase 2 is
**(2a) fix dispatch + capture the batch id**, then **(2b) reconcile**.

### 2a — realign `BatchTransfer` dispatch to the real contract
**Request** `POST /API/v4/Fund/BatchTransfer` (flat, we supply the ids):
```json
{ "IssuerAccountNumber":"<issuer>", "SourceWalletId":"<walletId>",
  "CustomerBatchId":"<we generate, one per run>", "EmailNotification":"true",
  "Items":[ { "CustomerTransactionId":"<our redemption id>", "RecipientId":"<PAT>",
              "Amount":"1.00", "SendMethodId":"<rail>", "Destination":{ … per rail … },
              "Description":"…" } ] }
```
Per-item **rail → `SendMethodId` + `Destination`** (from each item's `partner_redemption` profile — same
resolution as the single dispatch branch in `XtrmVendorService`):

| Rail (payoutMethod) | SendMethodId | Destination |
|---|---|---|
| BANK | `XTR94500` | `{ "BeneficiaryBankID": <partnerLinkedBankId>, "BeneficiaryBankPaymentMethod": "ACH" }` |
| ANYPAY | `XTR94502` | `{ "WalletId": <recipient's USD wallet id, resolved via GetBeneficiaryWallets> }` — plus `RecipientId` at item level (both, as in the curl) |
| CARD | `XTR94508` | `{ "CardToken": <partnerLinkedCardId> }` — **ASSUMPTION**; confirm |

**Response:** `{ "BatchTransferResponse": { "CustomerBatchId", "Status":"Accepted",
"ItemsCount":{Total,Accepted,Rejected}, "Accepted":[{CustomerTransactionId,…}], "Rejected":[…] } }`
- `Status:"Accepted"` = queued (NOT completed). Capture `CustomerBatchId`; mark each `Accepted` item as
  dispatched, each `Rejected` item as failed (release).
- **Persist `customer_batch_id`** on each accepted `RedemptionRequest`. `CustomerTransactionId` = redemption
  id (we control it — no extra storage).
- Revisit `BatchRedemptionProcessor` grouping: it currently assumes one rail/currency per batch; the real
  API is per-item rail, so items only need to share a `SourceWalletId` + currency (rails can differ).

### 2b — batch reconciliation (whole-batch list API — 1 call per batch, not per item)
**API:** `GET /API/v4/Fund/BatchTransfer/{customer_batch_id}?recordsToSkip={n}&recordsToTake=50&history=false`
**Response:**
```json
{ "CustomerBatchId":"…", "Status":"CompletedWithFailures", "Currency":"USD", "ItemsCount":27,
  "Items":[ { "CustomerTransactionId":"<redemption id>", "Status":"Success"|"Failed"|…,
              "RecipientId":"…", "SendMethodId":"…", "Amount":"…", "LastUpdatedAt":"…", "Attempts":[] } ],
  "Pagination": { "HasMore": false, "NextRecordsToSkip": 0 } }
```
- The cron **groups in-flight batch items by `customer_batch_id`**, calls this **once per batch** (paginating
  via `recordsToSkip`/`recordsToTake` until `Pagination.HasMore=false`), builds a `CustomerTransactionId → Status`
  map, and settles each of our in-flight items by matching its redemption id. Far fewer calls than per-item.
- Item `Status` maps via the same status sets (`Success`→complete, `Failed`→fail, else wait). The batch-level
  `Status` (e.g. `CompletedWithFailures`) is informational only. An item **missing** from the returned list →
  treat as still-pending (wait). Poll query requires `customer_batch_id IS NOT NULL`.
- (The per-item endpoint `…/Items/{CustomerTransactionId}` also exists as a fallback for a single lookup.)

## Data model

Migration V39 on `redemption_requests` (both nullable; set only for BATCH items at dispatch):
- `customer_batch_id VARCHAR(100)` — the batch id we generate + query the status API by.
- `customer_transaction_id VARCHAR(50)` — a compact per-item id we generate + send as XTRM's
  `CustomerTransactionId`, stored so reconciliation can match `Items[].CustomerTransactionId` → our redemption.
  (Chosen over sending the raw 36-char redemption UUID, to avoid any dependency on XTRM's id length/format limit.)

## Shared settlement (idempotency)

Extract from `RedemptionWebhookService` a shared core `finalize(request, completed, failureReason)` that
does the row-lock (`findByIdForUpdate`), terminal-state guard, ledger DEBIT/RELEASE, status update, and
event publish. Both the webhook and the reconciliation cron call it → a late webhook or a second cron run
hits the terminal guard and no-ops. No parallel settlement logic.

## Cron

- `@Scheduled(cron = "${redemption.reconciliation.cron:0 */30 * * * *}")` — 30 min default; local demo
  overrides to every minute via property/profile.
- Per run: page the pickup query; for each row resolve identifiers → call the right status API → map →
  `finalize()` on COMPLETE/FAIL, skip on still-pending. Separate count+alert for past-cap rows.
- Overlap guard (short runtime + a simple in-progress flag, or DB advisory lock) so a 1-min cadence can't
  double-run the same set.

## XtrmApiClient additions

- `getTransactionDetails(GetTransactionDetailsCommand{issuer, transactionId, recipientUserId})` → single status.
- `getBatchStatus(GetBatchStatusCommand{customerBatchId, recordsToSkip, recordsToTake})` → whole-batch item
  list + pagination (the cron pages until `HasMore=false`, then maps each `CustomerTransactionId`→`Status`).
- Realign `batchTransfer` + `BatchTransferCommand` (add `customerBatchId`; per-item `customerTransactionId`,
  `sendMethodId`, `destination`).
- Impl (real HTTP + parsing) + Stub (deterministic).

## Open confirmations

1. ✅ CONFIRMED (from code): `GetUserWalletTransactionDetails.TransactionID` = our `vendorReferenceId`
   (= `TransferFund` response `PaymentTransactionId`, e.g. `879264`).
2. ✅ CONFIRMED: success = `Success`/`Completed`/`Released` → COMPLETE; `Failed` → FAIL+release; any other
   string (pending/unknown) → wait → cap → manual.
3. Batch `Destination` per rail (Phase 2a dispatch) — **decisions taken, override anytime**:
   - BANK → `{ BeneficiaryBankID, BeneficiaryBankPaymentMethod:"ACH" }` ✅ (from curl)
   - ANYPAY → ✅ DECIDED: send both `RecipientId` (item level) + `Destination.WalletId` = the recipient's USD
     wallet id via `GetBeneficiaryWallets` (resolved once per recipient per run), exactly as the curl.
   - CARD (`XTR94508`) → **DEFERRED**: batch ships with BANK + ANYPAY; CARD added once a card-item `Destination`
     example is available.
   - Item id → generate + store `customer_transaction_id` (see Data model) — removes the UUID-length dependency.

## Test plan (JUnit, against the stub)

- Reconciliation service: complete / fail / still-pending / not-found / past-cap; single + batch.
- Idempotency: cron settles a row already settled by webhook → no-op (terminal guard).
- BatchTransfer realignment: request shape per rail; response Accepted/Rejected handling; `customer_batch_id` persisted.
- Status mapping from config sets.

## Review findings (fold into execution)

**Correctness / completeness — must handle during build:**
1. **`finalize()` must decouple from `RedemptionWebhookEvent`.** Today `applyCompletion`/`applyFailure` take a
   webhook event and set its status (and dead-letter it on insufficient-reserved). The cron has no such event,
   so extract *pure* settlement (row-lock, terminal guard, ledger DEBIT/RELEASE, status, event publish); the
   insufficient-reserved branch must **log/alert**, not NPE on a null event. Caller (webhook vs cron) owns its
   own audit record.
2. **Ambiguous rows** (`dispatchAttemptedAt` set, `vendorReferenceId` NULL, non-batch) can't be polled — route
   them explicitly to **alert + cap→manual**, never silently skip.
3. **Cron writes its own audit trail** — a reconciliation record (source=RECONCILIATION, polled status, ts), the
   analogue of the webhook's `RedemptionWebhookEvent`, so we can trace how each redemption was settled.
6. **Add a `get()` helper** on `XtrmApiClientImpl` (RestClient.get) — the batch status endpoints are GET.

**Risk / prod:**
4. **Batch dispatch is the largest/riskiest piece.** ANYPAY items resolve the recipient's USD wallet id via
   `GetBeneficiaryWallets` (once per recipient per run) for `Destination.WalletId` (decided). **Failure mode:**
   if the lookup fails or the recipient has no USD wallet, **hold that item out of the batch** (leave it RESERVED,
   retry next run) rather than dispatch it malformed. Also revisit `BatchRedemptionProcessor` grouping (currently
   one rail/currency per batch; real API is per-item rail → items only need a shared `SourceWalletId` + currency).
5. **Multi-instance:** no ShedLock in the codebase, so >1 app instance would double-poll XTRM (row-lock keeps
   settlement *correct*, just wasteful / possible rate-limit). Fine for single-instance demo; for prod, add a
   lightweight DB lock or match whatever the project settles on. Also cap calls-per-run + page the pickup query.

**Verify at test / first live run:**
7. Confirm `GetUserWalletTransactionDetails` returns a `"Transaction Status"` field for **CASH** (AnyPay/Bank)
   transfers — the sample response was a gift card. Parser scans `Field[0]` for that name.
8. ⚠️ Terminology trap: XTRM **`Released` = success** (funds sent) vs our **release = refund the reservation**
   (failure). Add a clear comment where we map it so no one inverts it.

## Execution order (one flow)

1. V39 migration + `customer_batch_id`.
2. Extract shared `finalize()` from `RedemptionWebhookService`.
3. `XtrmApiClient`: single + batch status methods; realign `batchTransfer`; Impl + Stub.
4. Reconciliation cron service (pickup query, mapping, cap/alert).
5. Wire config (cadence, status sets).
6. Tests. Contracts doc. (No FE — this is backend/ops only.)

## Done when

- A missed-webhook INSTANT/APPROVAL payout is auto-completed/failed by the cron (idempotent vs a late webhook).
- Batch payouts dispatch in the real XTRM shape and their items are reconciled the same way.
- 3-day cap enforced; past-cap rows alerted, never auto-released. Backend unit tests green.
