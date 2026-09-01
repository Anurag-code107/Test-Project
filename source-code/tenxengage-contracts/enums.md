# Shared Enumerations

All enum values used across backend and frontend. Backend implements as Java enums, frontend implements as TypeScript union types or enums.

---

## User & Auth Enums

## UserStatus

| Value                | Description                                  |
| -------------------- | -------------------------------------------- |
| ACTIVE               | User can log in and use the system           |
| INACTIVE             | User account is disabled                     |
| SUSPENDED            | User account is temporarily suspended        |
| PENDING_VERIFICATION | User registered but not yet verified         |
| ANONYMIZED           | User data has been anonymized (GDPR erasure) |
| RESTRICTED           | User access is restricted                    |

## SubscriptionTier

| Value        | Description                                                 |
| ------------ | ----------------------------------------------------------- |
| STARTER      | Basic tier with core features                               |
| PROFESSIONAL | Mid-tier with advanced analytics and customization          |
| ENTERPRISE   | Full-featured tier with API access, SSO, and white-labeling |

## ClientStatus

| Value     | Description                             |
| --------- | --------------------------------------- |
| ACTIVE    | Client is active and operational        |
| INACTIVE  | Client account is disabled              |
| SUSPENDED | Client account is temporarily suspended |
| TRIAL     | Client is in a trial period             |

## PartnerCompanyStatus

| Value    | Description                                 |
| -------- | ------------------------------------------- |
| ACTIVE   | Partner company is active and participating |
| INACTIVE | Partner company is disabled                 |

## ApprovalDecision

| Value    | Description                     |
| -------- | ------------------------------- |
| APPROVED | Approver approved the incentive |
| REJECTED | Approver rejected the incentive |

---

## Incentive Enums

## IncentiveType

| Value    | Description                                                              |
| -------- | ------------------------------------------------------------------------ |
| SALES    | Sales performance incentive with product requirements and payout rules   |
| TRAINING | Training completion incentive linked to LMS courses                      |
| ACTIVITY | Activity-based incentive requiring specific actions and document uploads |
| JOURNEY  | Multi-stage journey combining multiple incentives in sequence            |

## IncentiveStatus

| Value            | Description                              |
| ---------------- | ---------------------------------------- |
| DRAFT            | Incentive created but not yet submitted  |
| PENDING_APPROVAL | Submitted for review, awaiting approval  |
| DENIED           | Approval denied by one or more approvers |
| ACTIVE           | Approved and currently running           |
| INACTIVE         | Program deactivated or period ended      |

## AllocationMethod

| Value             | Description                                   |
| ----------------- | --------------------------------------------- |
| EQUAL             | Budget split equally across partners          |
| WEIGHTED          | Budget allocated by partner tier or size      |
| PERFORMANCE_BASED | Budget allocated based on performance metrics |

## BudgetMode

| Value        | Description                              |
| ------------ | ---------------------------------------- |
| GLOBAL       | Single budget pool for all locations     |
| PER_LOCATION | Separate budget pools per location level |

## AudienceRuleType

Stored as a string in the database (not a Java enum). Common values:

| Value        | Description                 |
| ------------ | --------------------------- |
| REGION       | Geographic region filter    |
| PARTNER_TYPE | Partner company type filter |
| ROLE         | User role filter            |
| COUNTRY      | Country filter              |
| LOCATION     | Location hierarchy filter   |

## EligibilityRuleType

| Value          | Description                                |
| -------------- | ------------------------------------------ |
| PRODUCTS       | Rule based on specific product SKUs        |
| BOOKING_AMOUNT | Rule based on deal/booking monetary amount |
| CUSTOMER_TYPE  | Rule based on customer classification      |

## RuleOperator

| Value                 | Description                        |
| --------------------- | ---------------------------------- |
| EQUALS                | Exact match                        |
| GREATER_THAN          | Value exceeds threshold            |
| GREATER_THAN_OR_EQUAL | Value meets or exceeds threshold   |
| LESS_THAN             | Value is below threshold           |
| BETWEEN               | Value falls within a min-max range |
| IN                    | Value is one of a set              |
| NOT_IN                | Value is not in a set              |

## PayoutType

| Value      | Description                               |
| ---------- | ----------------------------------------- |
| PERCENTAGE | Payout is a percentage of the deal amount |
| FLAT       | Payout is a fixed amount                  |

## ClaimStatus

| Value     | Description                                |
| --------- | ------------------------------------------ |
| UNCLAIMED | Deal has not been claimed by any user      |
| CLAIMED   | Deal has been claimed by at least one user |

## CourseLevel

Stored as a string (not a Java enum). Common values:

| Value        | Description         |
| ------------ | ------------------- |
| BEGINNER     | Entry-level course  |
| INTERMEDIATE | Mid-level course    |
| ADVANCED     | Expert-level course |

---

## Currency & Reward Enums

## CurrencyType

| Value        | Description                                  |
| ------------ | -------------------------------------------- |
| MONETARY     | Cash-equivalent rewards (e.g., cash, points) |
| NON_MONETARY | Non-cash rewards (e.g., credits, tickets)    |

## TransactionType

| Value      | Description                                    |
| ---------- | ---------------------------------------------- |
| REWARD     | Reward earned from incentive participation     |
| REDEMPTION | Reward redeemed/paid out                       |
| ADJUSTMENT | Manual adjustment by admin                     |
| BONUS      | Bonus reward (e.g., recommendation completion) |
| CLAWBACK   | Reversal/clawback of a previous reward         |
| TRANSFER   | Transfer between accounts                      |

## TransactionStatus

| Value      | Description                              |
| ---------- | ---------------------------------------- |
| PENDING    | Transaction created, awaiting processing |
| APPROVED   | Transaction approved for processing      |
| REJECTED   | Transaction rejected                     |
| PROCESSING | Transaction is being processed           |
| COMPLETED  | Transaction successfully completed       |
| FAILED     | Transaction failed                       |
| CANCELLED  | Transaction was cancelled                |

---

## Connector & Data Enums

## ConnectorType

| Value                  | Description                          |
| ---------------------- | ------------------------------------ |
| SALESFORCE             | Salesforce CRM connector             |
| MICROSOFT_DYNAMICS_365 | Microsoft Dynamics 365 CRM connector |
| SNOWFLAKE              | Snowflake data warehouse connector   |
| HUBSPOT                | HubSpot marketing platform connector |

## ConnectorStatus

| Value        | Description                            |
| ------------ | -------------------------------------- |
| DISCONNECTED | Connector created but not yet verified |
| CONNECTED    | Connector verified and working         |
| ERROR        | Connection test failed or sync error   |
| SYNCING      | Data sync currently in progress        |

## FieldDataType

| Value    | Description                          |
| -------- | ------------------------------------ |
| TEXT     | Free text / string values            |
| NUMBER   | Numeric values (integer or decimal)  |
| CURRENCY | Monetary amount values               |
| DATE     | Date or datetime values              |
| BOOLEAN  | True/false values                    |
| LIST     | Enumerated list of predefined values |

## DataUploadSource

| Value     | Description                            |
| --------- | -------------------------------------- |
| MANUAL    | Data uploaded via file (CSV/XLSX)      |
| CONNECTOR | Data pulled from an external connector |

## DataUploadStatus

| Value      | Description                   |
| ---------- | ----------------------------- |
| PROCESSING | Upload is being processed     |
| COMPLETED  | Upload completed successfully |
| FAILED     | Upload failed                 |

## TaggingJobStatus

| Value     | Description                        |
| --------- | ---------------------------------- |
| RUNNING   | Tagging job is in progress         |
| COMPLETED | Tagging job completed successfully |
| FAILED    | Tagging job failed                 |

## SyncCadence

| Value   | Description                     |
| ------- | ------------------------------- |
| MANUAL  | No automatic sync — manual only |
| HOURLY  | Sync every hour                 |
| DAILY   | Sync every day                  |
| WEEKLY  | Sync every week                 |
| MONTHLY | Sync every month                |

---

## Document Enums

## DocumentCategory

Stored as a string (not a Java enum). Used as the `documentType` field on IncentiveDocument.

| Value             | Description                 |
| ----------------- | --------------------------- |
| terms-conditions  | Terms & Conditions document |
| eligible-products | Eligible Products list      |
| program-rules     | Program Rules document      |
| faq               | Frequently Asked Questions  |

## AllowedFileType

Allowed file extensions for document uploads. Stored as the `fileType` field on IncentiveDocument.

| Extension | MIME Type                                                               |
| --------- | ----------------------------------------------------------------------- |
| pdf       | application/pdf                                                         |
| xlsx      | application/vnd.openxmlformats-officedocument.spreadsheetml.sheet       |
| xls       | application/vnd.ms-excel                                                |
| docx      | application/vnd.openxmlformats-officedocument.wordprocessingml.document |
| doc       | application/msword                                                      |

## DocumentSubmissionStatus

| Value    | Description                         |
| -------- | ----------------------------------- |
| PENDING  | Document submitted, awaiting review |
| APPROVED | Document reviewed and approved      |
| REJECTED | Document reviewed and rejected      |

---

## Builder & Configuration Enums

## BuilderFieldType

| Value        | Description            |
| ------------ | ---------------------- |
| TEXT_BOX     | Single-line text input |
| DROPDOWN     | Single-select dropdown |
| MULTI_SELECT | Multi-select dropdown  |
| DATE_PICKER  | Date picker input      |
| NUMBER_INPUT | Numeric input          |
| TOGGLE       | Boolean toggle switch  |
| TEXT_AREA    | Multi-line text input  |

## ValueSource

Determines where a builder field's dropdown options are sourced from.

| Value               | Description                                      |
| ------------------- | ------------------------------------------------ |
| LOCATION_HIERARCHY  | Options from the location hierarchy tree         |
| CLIENT_ROLES        | Options from the client's role definitions       |
| DATA_OBJECT_FIELD   | Options from a data object field's sample values |
| STATIC              | Hardcoded options defined in the field config    |
| ACTIVITY_CATEGORIES | Options from the client's activity categories    |

## QuarterMethod

| Value  | Description                                |
| ------ | ------------------------------------------ |
| MONTHS | Quarters defined by month boundaries       |
| WEEKS  | Quarters defined by week boundaries        |
| DAYS   | Quarters defined by exact day counts       |
| CUSTOM | Quarters defined by custom start/end dates |

---

## Home & Dashboard Enums

## HomeDateFilter

| Value        | Description                                    |
| ------------ | ---------------------------------------------- |
| LAST_30_DAYS | Last 30 days from today                        |
| THIS_QUARTER | Current fiscal quarter                         |
| THIS_YEAR    | Current fiscal year                            |
| CUSTOM       | Custom date range (requires startDate/endDate) |

## HomeIncentiveTypeFilter

| Value      | Description                      |
| ---------- | -------------------------------- |
| ALL        | All incentive types              |
| SALES      | Sales incentives only            |
| ENABLEMENT | Training and activity incentives |
| JOURNEYS   | Journey incentives only          |

---

## Notification Enums

## NotificationCategory

| Value           | Description                                                     |
| --------------- | --------------------------------------------------------------- |
| INCENTIVE       | Incentive lifecycle notifications (created, activated, expired) |
| BUDGET          | Budget threshold and utilization notifications                  |
| CLAIMS          | Claim and deal notifications                                    |
| REWARDS         | Reward earning and redemption notifications                     |
| DATA            | Data upload and sync notifications                              |
| INTEGRATION     | Connector and integration notifications                         |
| USER_MANAGEMENT | User account and access notifications                           |

---

## Recommendation Enums

## RecommendationType

| Value     | Description                      |
| --------- | -------------------------------- |
| TRAINING  | Training course recommendation   |
| INCENTIVE | Incentive program recommendation |

## RecommendationInteractionType

| Value     | Description                         |
| --------- | ----------------------------------- |
| VIEWED    | User viewed the recommendation      |
| DISMISSED | User dismissed the recommendation   |
| COMPLETED | User completed the recommended item |

---

## Audit Enums

## AuditAction

| Value         | Description                         |
| ------------- | ----------------------------------- |
| CREATED       | Resource was created                |
| EDITED        | Resource was modified               |
| DELETED       | Resource was deleted                |
| ACTIVATED     | Resource was activated              |
| DEACTIVATED   | Resource was deactivated            |
| SUBMITTED     | Resource was submitted for approval |
| APPROVED      | Resource was approved               |
| REJECTED      | Resource was rejected               |
| EXPIRED       | Resource expired                    |
| CLAIMED       | Deal was claimed                    |
| UNCLAIMED     | Deal was unclaimed                  |
| UPLOADED      | Data was uploaded                   |
| SYNCED        | Data was synced from connector      |
| LOGGED_IN     | User logged in                      |
| LOGGED_OUT    | User logged out                     |
| ANONYMIZED    | User data was anonymized            |
| DATA_EXPORTED | User data was exported              |
| COMPLETED     | Vendor confirmed fulfillment (F-03) |
| FAILED        | Vendor reported failure (F-03)      |
| CANCELLED     | Redemption cancelled (F-03)         |
| ENROLLED      | User enrolled in XTRM for payouts (F-03 XTRM payout enhancement)     |
| BANK_LINKED   | User linked a bank account for payouts (F-03 XTRM payout enhancement) |
| BANK_UNLINKED | User removed their linked bank account (F-03 XTRM payout enhancement) |

## AuditActorType

| Value  | Description                                           |
| ------ | ----------------------------------------------------- |
| USER   | Action performed by a human user                      |
| SYSTEM | Action performed by the system (scheduler, batch job) |

## AuditResourceType

| Value               | Description                |
| ------------------- | -------------------------- |
| INCENTIVE           | Incentive program          |
| USER                | User account               |
| CLAIM               | Reward claim               |
| CONNECTOR           | External connector         |
| PARTNER_COMPANY     | Partner company            |
| PRODUCT             | Product catalog entry      |
| DATA                | Data upload or operation   |
| DATA_OBJECT         | Data object configuration  |
| NOTIFICATION_CONFIG        | Notification configuration                                      |
| CLIENT                     | Client/tenant                                                   |
| AUTH                       | Authentication event                                            |
| REWARD_WALLET              | Reward wallet (F-01)                                            |
| LEDGER_ENTRY               | Ledger entry (F-01)                                             |
| REDEMPTION_CATALOG_ITEM    | Global redemption catalog item (F-02)                           |
| TENANT_CATALOG_CONFIG      | Tenant catalog item config + regional config (F-02)             |
| TENANT_REDEMPTION_SETTINGS | Tenant-wide redemption settings (F-02)                          |
| REDEMPTION_REQUEST         | Redemption request lifecycle (F-03)                             |
| REDEMPTION_WEBHOOK_EVENT   | Vendor webhook event processing (F-03)                          |
| REDEMPTION_EXPORT_JOB      | Async redemption history export job (F-05)                      |
| REDEMPTION_RETURN           | Non-cash return request lifecycle (F-06)                        |
| REDEMPTION_ANALYTICS_EXPORT | Redemption analytics CSV export download (F-07)                |
| REDEMPTION_ADVANCED_ANALYTICS_EXPORT | Advanced analytics liability trend CSV export download (F-08) |
| BALANCE_EXPIRATION_POLICY    | Per-currency balance expiration policy create/update/disable (reward-balance-expiration) |
| BALANCE_EXPIRY_BREAKAGE_EXPORT | Balance expiration breakage report CSV export download (reward-balance-expiration) |
| PARTNER_REDEMPTION              | Per-user XTRM payout profile — enrollment / bank-link / payout-method (F-03 XTRM payout enhancement) |

---

## Redemption Catalog Enums (F-02)

## RedemptionCategory

| Value    | Description                                                  |
| -------- | ------------------------------------------------------------ |
| CASH     | Cash-equivalent item — routed to XTRM; never returnable      |
| NON_CASH | Non-cash item — routed to Xoxoday; may be returnable         |

## RedemptionProcessingMode

| Value              | Description                                                       |
| ------------------ | ----------------------------------------------------------------- |
| INSTANT            | Fulfilled immediately via vendor SLA                              |
| BATCH              | Queued for next batch run per tenant batchCadence                 |
| APPROVAL_REQUIRED  | Requires admin approval before fulfillment                        |

---

## Redemption Flow Enums (F-03)

## RedemptionStatus

| Value            | Description                                                                       |
| ---------------- | --------------------------------------------------------------------------------- |
| PENDING_APPROVAL | Submitted with APPROVAL_REQUIRED mode; balance reserved; awaiting admin approval  |
| RESERVED         | Balance reserved; queued for batch processing or awaiting INSTANT vendor call     |
| PROCESSING       | Submitted to vendor; awaiting webhook confirmation                                |
| COMPLETED        | Vendor confirmed fulfillment; DEBIT ledger entry written; terminal state          |
| FAILED           | Vendor reported failure; RELEASE ledger entry written; terminal state             |
| CANCELLED        | Cancelled before vendor submission (rejection or admin action); RELEASE written; terminal state |

## WebhookStatus

| Value        | Description                                                      |
| ------------ | ---------------------------------------------------------------- |
| RECEIVED     | Webhook arrived; processing not yet attempted                    |
| PROCESSED    | Webhook successfully applied to ledger and redemption state      |
| DUPLICATE    | Idempotency key already processed; discarded without reprocessing |
| FAILED       | Processing failed; retries exhausted or non-retryable error      |
| DEAD_LETTERED | Routed to dead-letter queue after all retries exhausted         |

---

## BatchCadence

_Semantically distinct from `SyncCadence` (connector data sync). Used only for BATCH-mode redemption scheduling._

| Value  | Description                                          |
| ------ | ---------------------------------------------------- |
| DAILY  | Batch redemptions processed once per day             |
| WEEKLY | Batch redemptions processed once per week            |

---

## Redemption Payout Enums (F-03 XTRM payout enhancement)

## XtrmEnrollmentStatus

Lifecycle of a user's XTRM enrollment (stored on `partner_redemption.enrollment_status`). `ENROLLED` is terminal; enrollment is idempotent and non-blocking (a failed `CreateUser` is retried lazily before payout).

| Value        | Description                                                                 |
| ------------ | --------------------------------------------------------------------------- |
| NOT_ENROLLED | No XTRM `CreateUser` attempt yet (default)                                   |
| ENROLLED     | `CreateUser` succeeded; `recipientUserId` (PAT) stored                       |
| FAILED       | A `CreateUser` attempt errored; retryable                                    |

## RedemptionPayoutMethod

The XTRM payout rail chosen for a user's cash redemptions. Default `ANYPAY`.

| Value  | Description                                                                      |
| ------ | ------------------------------------------------------------------------------- |
| ANYPAY | XTRM AnyPay Individual (XTR94502) — credits the recipient's XTRM wallet (default) |
| BANK   | XTRM Bank / ACH (XTR94500) — requires a linked bank (`partnerLinkedBankId`)          |
| CARD   | XTRM Rapid Transfer (XTR94508) + card token — requires a linked card (`partnerLinkedCardId`) |

---

## Compliance Enums

## PolicyType

| Value               | Description                                      |
| ------------------- | ------------------------------------------------ |
| PRIVACY_NOTICE      | Privacy notice / privacy policy document         |
| TERMS_OF_SERVICE    | Terms of service / terms of use document         |
| ANTI_BRIBERY_POLICY | Anti-bribery and anti-corruption policy document |

## ConsentType

| Value              | Description                                                 |
| ------------------ | ----------------------------------------------------------- |
| AI_RECOMMENDATIONS | Consent for AI-driven incentive and content recommendations |
| MARKETING_EMAIL    | Consent for marketing and promotional emails                |
| ANALYTICS          | Consent for analytics and usage tracking                    |

## KycStatus

| Value       | Description                                   |
| ----------- | --------------------------------------------- |
| NOT_STARTED | KYC verification has not been initiated       |
| IN_PROGRESS | KYC submitted and awaiting review             |
| APPROVED    | KYC verified and approved                     |
| REJECTED    | KYC verification rejected                     |
| EXPIRED     | KYC approval has expired and requires renewal |

## ComplianceRiskLevel

| Value    | Description                                        |
| -------- | -------------------------------------------------- |
| LOW      | Minimal compliance risk                            |
| MEDIUM   | Moderate compliance risk — monitor closely         |
| HIGH     | Elevated compliance risk — action recommended      |
| CRITICAL | Severe compliance risk — immediate action required |

## ComplianceAlertType

| Value                   | Description                                                 |
| ----------------------- | ----------------------------------------------------------- |
| VALUE_CAP_APPROACHING   | Recipient approaching annual reward value cap threshold     |
| VALUE_CAP_EXCEEDED      | Recipient has exceeded annual reward value cap              |
| GOVERNMENT_DEAL_FLAGGED | Deal involving a government entity has been flagged         |
| DISPROPORTIONATE_REWARD | Single reward is disproportionately large relative to norms |
| CONCENTRATION_ALERT     | Disproportionate share of rewards going to a single partner |
| KYC_EXPIRED             | Partner company KYC approval has expired                    |

## ComplianceAlertStatus

| Value         | Description                                                    |
| ------------- | -------------------------------------------------------------- |
| NEW           | Alert generated, not yet reviewed                              |
| INVESTIGATING | Alert is being investigated                                    |
| RESOLVED      | Alert investigated and resolved                                |
| DISMISSED     | Alert reviewed and dismissed (false positive or accepted risk) |

## GovernmentDealRestrictionMode

| Value             | Description                                               |
| ----------------- | --------------------------------------------------------- |
| NONE              | No restrictions — government deals treated like any other |
| STRICT            | All rewards to government segment are blocked             |
| APPROVAL_REQUIRED | Rewards require explicit admin approval before payout     |
| AUDIT_ONLY        | Rewards are allowed but flagged for audit review          |

## WhistleblowerReportType

| Value                | Description                                            |
| -------------------- | ------------------------------------------------------ |
| SUSPICIOUS_INCENTIVE | Report of a suspicious or fraudulent incentive program |
| POTENTIAL_KICKBACK   | Report of a potential kickback or bribery arrangement  |
| POLICY_VIOLATION     | Report of a policy violation                           |
| DATA_PRIVACY_CONCERN | Report of a data privacy or data protection concern    |
| OTHER                | Other compliance concern not covered by specific types |

## WhistleblowerStatus

| Value               | Description                            |
| ------------------- | -------------------------------------- |
| NEW                 | Report submitted, not yet reviewed     |
| ACKNOWLEDGED        | Report acknowledged by compliance team |
| UNDER_INVESTIGATION | Report is being actively investigated  |
| RESOLVED            | Investigation complete, issue resolved |
| DISMISSED           | Report reviewed and dismissed          |

## BreachSeverity

| Value    | Description                                                 |
| -------- | ----------------------------------------------------------- |
| LOW      | Minor breach with limited impact                            |
| MEDIUM   | Moderate breach affecting a limited number of records       |
| HIGH     | Significant breach affecting many records or sensitive data |
| CRITICAL | Severe breach requiring immediate authority notification    |

## BreachStatus

| Value         | Description                                     |
| ------------- | ----------------------------------------------- |
| DETECTED      | Breach detected, initial assessment in progress |
| INVESTIGATING | Breach under active investigation               |
| REPORTED      | Breach reported to supervisory authority        |
| RESOLVED      | Breach contained and remediation complete       |
| CLOSED        | Breach fully resolved and case closed           |

## DpaStatus

| Value   | Description           |
| ------- | --------------------- |
| PENDING | DPA not yet signed    |
| SIGNED  | DPA signed and active |
| EXPIRED | DPA has expired       |

## SccStatus

| Value             | Description                                             |
| ----------------- | ------------------------------------------------------- |
| NOT_REQUIRED      | Standard contractual clauses not required (same region) |
| REQUIRED          | SCCs required but not yet signed                        |
| SIGNED            | SCCs signed and active                                  |
| DEPENDS_ON_REGION | SCC requirement varies by client region                 |

## DataCategory

| Value                 | Description                                                   |
| --------------------- | ------------------------------------------------------------- |
| INACTIVE_USERS        | User accounts that have been inactive beyond retention period |
| AUDIT_LOGS            | System audit and activity logs                                |
| NOTIFICATIONS         | User notification records                                     |
| REWARD_TRANSACTIONS   | Reward transaction history                                    |
| PURCHASE_ORDERS       | Purchase order records                                        |
| CONNECTOR_CREDENTIALS | External connector credentials and tokens                     |

## RetentionActionType

| Value     | Description                                                               |
| --------- | ------------------------------------------------------------------------- |
| ANONYMIZE | Replace personal data with anonymized values (preserves record structure) |
| DELETE    | Permanently delete the records                                            |

---

## Wallet & Ledger Enums (wallet-ledger-foundation)

## WalletType

| Value      | Description                                                                    |
| ---------- | ------------------------------------------------------------------------------ |
| INDIVIDUAL | Per-user reward wallet; one per (clientId, userId, currencyId)                 |
| COMPANY    | Pooled partner-company wallet; one per (clientId, partnerCompanyId, currencyId)|

## LedgerEntryType

| Value         | Description                                                                |
| ------------- | -------------------------------------------------------------------------- |
| CREDIT        | Earning event — increases availableBalance; wallet auto-created if absent  |
| RESERVE       | Redemption submitted — decreases availableBalance, increases reservedBalance |
| DEBIT         | Vendor confirmed — decreases reservedBalance; funds consumed               |
| RELEASE       | Redemption failed/cancelled — decreases reservedBalance, restores availableBalance |
| RETURN_CREDIT | Vendor confirmed return (F-06) — increases availableBalance                |
| REVERSAL      | Reverses a prior credit (e.g. claim reversal) — decreases availableBalance; reservedBalance unchanged; idempotent on (walletId, referenceType, referenceId) |
| EXPIRY        | Unused balance expired by the scheduled batch (reward-balance-expiration) — decreases availableBalance; kept distinct from redemption DEBIT for audit/compliance |

---

## Redemption Approval Queue Enums (F-04)

## RedemptionRequestType

Filter enum for the approval queue `requestType` parameter. Distinguishes standard redemption
requests from return requests. RETURN is a stub in F-04; fully implemented in F-06.

| Value      | Description                                                                         |
| ---------- | ----------------------------------------------------------------------------------- |
| REDEMPTION | Standard redemption request — partner redeeming wallet balance for a catalog item   |
| RETURN     | Return request — partner returning a previously fulfilled redemption (F-06 only)    |

---

## Redemption History Enums (F-05)

## ExportFormat

Format of a generated redemption history export file.

| Value | Description                        |
| ----- | ---------------------------------- |
| CSV   | Comma-separated values text file   |
| XLSX  | Excel Open XML spreadsheet         |

## RedemptionExportStatus

Lifecycle state of a `RedemptionExportJob`. COMPLETED and FAILED are terminal.

| Value      | Description                                                             |
| ---------- | ----------------------------------------------------------------------- |
| PENDING    | Job created; async generation task queued but not yet started           |
| PROCESSING | Async task is actively running the query and generating the file        |
| COMPLETED  | File written to object storage; fileKey, rowCount, and expiresAt set   |
| FAILED     | File generation failed; failureReason populated; no file available      |

## ExportScope

Scope of a `RedemptionExportJob` — captures which redemption records are included.
Determined server-side at trigger time; never accepted as a client parameter.

| Value      | Description                                                                      |
| ---------- | -------------------------------------------------------------------------------- |
| PERSONAL   | Export includes only the requesting user's personal wallet redemptions            |
| COMPANY    | Export includes redemptions from the requesting user's partner company wallet     |
| ALL_TENANT | Export includes all tenant redemptions; available to CLIENT_ADMIN only            |

---

## Redemption Returns Enums (F-06)

## ReturnStatus

Lifecycle state of a `RedemptionReturn`. Separate from `RedemptionStatus` — return lifecycle states differ fundamentally from redemption states. RETURN_CONFIRMED and RETURN_REJECTED are terminal. CANCELLED is non-terminal (resubmission allowed).

| Value | Description |
| --- | --- |
| PENDING_APPROVAL | Return submitted; awaiting CLIENT_ADMIN or ACTIVITY_APPROVER decision |
| APPROVED | Admin approved; Xoxoday return API call initiated asynchronously; partner wallet NOT yet credited |
| RETURN_CONFIRMED | Xoxoday confirmed the return; RETURN_CREDIT ledger entry written; partner balance restored; terminal |
| RETURN_REJECTED | Return rejected by admin or Xoxoday webhook; no wallet credit issued; terminal |
| CANCELLED | Partner cancelled own PENDING_APPROVAL return; non-terminal — resubmission allowed for the same redemption |
| RETURN_TIMED_OUT | 7 days elapsed in APPROVED state with no Xoxoday webhook response; requires admin manual resolution via POST /{id}/resolve |

## ReturnResolution

Resolution direction used in `ResolveTimedOutReturnRequest` for admin manual resolution of a `RETURN_TIMED_OUT` return.

| Value | Description |
| --- | --- |
| CONFIRM | Credit the partner wallet via RETURN_CREDIT ledger entry and transition return to RETURN_CONFIRMED |
| REJECT | Transition return to RETURN_REJECTED with no wallet credit; partner and CLIENT_ADMIN notified |

---

## Balance Expiration Enums (reward-balance-expiration / F-09)

## ExpirationMode

Drives which policy fields are required on a `BalanceExpirationPolicy` (FR-09.1).

| Value      | Description                                                                              |
| ---------- | ---------------------------------------------------------------------------------------- |
| INACTIVITY | Balance expires N days after the wallet's last activity in that currency (`inactivityDays`) |
| FIXED_DATE | Balance expires on a fixed calendar date (`fixedExpiryDate`)                              |

## ExpiryNoticeStatus

Lifecycle state of a `BalanceExpiryNotice` — one scheduled expiry event for one wallet+currency. `EXPIRED` and `CANCELLED` are terminal.

| Value     | Description                                                                                       |
| --------- | ------------------------------------------------------------------------------------------------- |
| SCHEDULED | Expiry event created; advance notice not yet sent                                                 |
| NOTIFIED  | Advance notice (`BALANCE_EXPIRING_SOON`) delivered; `notified_at` set (once-only dedup marker)    |
| EXPIRED   | Expiry executed — `EXPIRY` ledger debit written, `availableBalance` reduced; terminal             |
| CANCELLED | Cancelled by a policy disable/relax before execution; already-notified partners re-notified; terminal |
