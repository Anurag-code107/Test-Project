# PartnerRedemption

Per-user XTRM payout profile (XTRM payout & enrollment enhancement to F-03). Maps a platform user to their XTRM recipient id (`PAT`) and payout configuration, and stores the address XTRM `CreateUser` requires. One row per user (1:1 system record, no soft-delete). Tenant-isolated via `client_id` Hibernate filter with optimistic locking. Table: `partner_redemption` (migration V34).

## Fields

| Field | Type | Required | Notes |
|---|---|---|---|
| `id` | UUID | Yes | Generated |
| `userId` | UUID | Yes | FK → users, UNIQUE — one profile per user |
| `recipientUserId` | string (max 50) | No | XTRM `PAT` from `CreateUser`; the payout `RecipientUserID`. Null until enrolled |
| `enrollmentStatus` | XtrmEnrollmentStatus | Yes | `NOT_ENROLLED` (default) / `ENROLLED` / `FAILED` |
| `enrollmentError` | string (max 500) | No | Sanitized last enrollment error (no PII); retry diagnostics |
| `identityLevel` | string (max 30) | No | XTRM `AccountIdentityLevel` (informational for limit UX) |
| `addressLine1` | string (max 255) | No | **PII** — required before enrollment can succeed |
| `addressLine2` | string (max 255) | No | PII |
| `city` | string (max 120) | No | PII |
| `region` | string (max 120) | No | PII |
| `postalCode` | string (max 20) | No | PII |
| `countryIso2` | string (max 2) | No | 2-letter ISO; required before enrollment can succeed |
| `payoutMethod` | RedemptionPayoutMethod | Yes | `ANYPAY` (default) / `BANK` / `CARD` |
| `partnerLinkedBankId` | string (max 100) | No | XTRM `UserLinkedBankID` (a reference, NOT the bank account number) |
| `linkedBankLabel` | string (max 100) | No | Masked display-only label (e.g. `"Wells Fargo ••1898"`) |
| `partnerLinkedCardId` | string (max 100) | No | Default XTRM `CardToken` (a reference, NOT the card number) |
| `linkedCardLabel` | string (max 100) | No | Masked display-only label (e.g. `"Visa ••1111"`) |
| `enrolledAt` | datetime | No | When enrollment succeeded |
| `createdAt` | datetime | Yes | Inherited from BaseEntity |
| `updatedAt` | datetime | Yes | Inherited from BaseEntity |

## Fields Never Exposed in API

| Field | Reason |
|---|---|
| `clientId` | Tenant isolation — resolved server-side via Hibernate filter |
| `recipientUserId` | Confidential pseudonymous XTRM reference (PAT) — never returned or logged |
| `partnerLinkedBankId` | Confidential XTRM bank reference — only the masked `linkedBankLabel` is exposed |
| `partnerLinkedCardId` | Confidential XTRM card token — only the masked `linkedCardLabel` is exposed |
| `enrollmentError` | Internal diagnostics; not user-facing |
| `version` | Optimistic lock counter; internal |

`RedemptionProfileResponse` exposes only: `enrollmentStatus`, `payoutMethod`, `bankLinked` (derived), `linkedBankLabel`, `cardLinked` (derived), `linkedCardLabel`, `identityLevel`, and the saved address (self-only).

## Data Sensitivity (PII)

- Address fields (line1/2, city, region, postalCode, countryIso2) are **bounded PII** stored here because XTRM `CreateUser` requires them; reused for `LinkBankBeneficiary`. Subject to GDPR erasure with the user.
- `recipientUserId` / `partnerLinkedBankId` are pseudonymous XTRM references.
- **No** SSN/DOB and **no** bank/card numbers are ever stored — raw bank details are pass-through to XTRM at call time only.

## Business Rules

- Enrollment (`CreateUser`) requires `addressLine1` + `countryIso2`; a payee without them cannot enroll (prompted to complete their payout profile before first payout).
- Enrollment is idempotent — `ENROLLED` is terminal; re-enroll while `ENROLLED` is a no-op.
- Enrollment scope is payee roles only (PARTNER_SELLER / PARTNER_ADMIN) — enforced by the redeem / redeem_company permission on the profile endpoints.
- Selecting `payoutMethod = BANK` requires a non-null `partnerLinkedBankId`, else `BANK_NOT_LINKED` (422).
- Selecting `payoutMethod = CARD` requires a non-null `partnerLinkedCardId`, else `CARD_NOT_LINKED` (422).
- Removing a linked bank nulls `partnerLinkedBankId` + `linkedBankLabel`; if `payoutMethod` was `BANK` it resets to `ANYPAY`. Removing a linked card behaves the same for the card fields + `CARD` rail.
- Linked banks live in `partner_linked_bank`, linked cards in `partner_linked_card` (each many-per-user, soft-deleted); the profile holds only the **default** reference for each. Withdrawals are recorded in `partner_withdrawal`.
- The XTRM HTTP call runs outside the DB transaction; the result is persisted in a short follow-up transaction.

## Enrollment Status Transitions (`enrollmentStatus`)

```
NOT_ENROLLED → ENROLLED   CreateUser succeeds (address-save hook or lazy pre-payout)
NOT_ENROLLED → FAILED     CreateUser errors (non-blocking — profile save still succeeds)
FAILED       → ENROLLED   retry succeeds (next login / pre-payout)
FAILED       → FAILED     retry errors (remains retryable)
```

`ENROLLED` is terminal (no un-enroll in v1). Concurrent profile edits resolve via `@Version` (409 on conflict).

## Multi-Tenancy

- Tenant-scoped via `client_id` (Hibernate `@Filter` — never exposed in responses).
- All repository queries additionally filter by `clientId` (defense-in-depth); cross-tenant access returns 404/not-found semantics.
- Profile endpoints are self-only: the row is resolved from the JWT user, never a client-supplied id (IDOR guard).

## Relationships

- `@OneToOne` (logical) → `User` (FK: `user_id`, UNIQUE).
