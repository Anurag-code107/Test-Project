# Enum Index

Compact index of all enums defined in `enums.md`. Skills must read this index to check enum existence; deep-read the relevant `enums.md` section only when reusing or extending a specific enum.

**Update rule:** whenever an enum is added, removed, or renamed in `enums.md`, update this index in the same commit.

| Enum name | Description | Value count | Section in enums.md |
|---|---|---|---|
| UserStatus | Lifecycle state of a user account (ACTIVE / INACTIVE / SUSPENDED / PENDING_VERIFICATION / ANONYMIZED / RESTRICTED) | 6 | enums.md §UserStatus |
| SubscriptionTier | Subscription plan tier for a client (Starter / Professional / Enterprise) | 3 | enums.md §SubscriptionTier |
| ClientStatus | Operational state of a client account (ACTIVE / INACTIVE / SUSPENDED / TRIAL) | 4 | enums.md §ClientStatus |
| PartnerCompanyStatus | Participation state of a partner company (ACTIVE / INACTIVE) | 2 | enums.md §PartnerCompanyStatus |
| ApprovalDecision | Binary outcome of an incentive approval review (APPROVED / REJECTED) | 2 | enums.md §ApprovalDecision |
| IncentiveType | Category of incentive program (SALES / TRAINING / ACTIVITY / JOURNEY) | 4 | enums.md §IncentiveType |
| IncentiveStatus | Lifecycle stage of an incentive program from draft to active (DRAFT / PENDING_APPROVAL / ACTIVE) | 5 | enums.md §IncentiveStatus |
| AllocationMethod | How a budget pool is distributed across partners (EQUAL / WEIGHTED / PERFORMANCE_BASED) | 3 | enums.md §AllocationMethod |
| BudgetMode | Whether a single global budget or per-location budgets apply (GLOBAL / PER_LOCATION) | 2 | enums.md §BudgetMode |
| AudienceRuleType | Dimension used to filter the target audience for an incentive; stored as string, not Java enum (REGION / PARTNER_TYPE / ROLE) | 5 | enums.md §AudienceRuleType |
| EligibilityRuleType | Dimension used to qualify deal eligibility (PRODUCTS / BOOKING_AMOUNT / CUSTOMER_TYPE) | 3 | enums.md §EligibilityRuleType |
| RuleOperator | Comparison operator applied in audience or eligibility rules (EQUALS / BETWEEN / IN) | 7 | enums.md §RuleOperator |
| PayoutType | Whether an incentive payout is a percentage of the deal or a fixed amount (PERCENTAGE / FLAT) | 2 | enums.md §PayoutType |
| ClaimStatus | Whether a deal has been claimed by a user (UNCLAIMED / CLAIMED) | 2 | enums.md §ClaimStatus |
| CourseLevel | Difficulty level of a training course; stored as string, not Java enum (BEGINNER / INTERMEDIATE / ADVANCED) | 3 | enums.md §CourseLevel |
| CurrencyType | Whether a reward is monetary cash-equivalent or non-monetary (MONETARY / NON_MONETARY) | 2 | enums.md §CurrencyType |
| TransactionType | Nature of a wallet transaction (REWARD / REDEMPTION / CLAWBACK / TRANSFER) | 6 | enums.md §TransactionType |
| TransactionStatus | Processing state of a wallet transaction (PENDING / PROCESSING / COMPLETED / FAILED) | 7 | enums.md §TransactionStatus |
| ConnectorType | External CRM or data warehouse system being integrated (SALESFORCE / HUBSPOT / SNOWFLAKE) | 4 | enums.md §ConnectorType |
| ConnectorStatus | Connectivity state of an external connector (DISCONNECTED / CONNECTED / ERROR / SYNCING) | 4 | enums.md §ConnectorStatus |
| FieldDataType | Data type of a mapped or custom field (TEXT / NUMBER / CURRENCY / DATE / BOOLEAN / LIST) | 6 | enums.md §FieldDataType |
| DataUploadSource | Origin of an uploaded data batch (MANUAL / CONNECTOR) | 2 | enums.md §DataUploadSource |
| DataUploadStatus | Processing outcome of a data upload job (PROCESSING / COMPLETED / FAILED) | 3 | enums.md §DataUploadStatus |
| TaggingJobStatus | Processing state of an AI tagging job (RUNNING / COMPLETED / FAILED) | 3 | enums.md §TaggingJobStatus |
| SyncCadence | Frequency at which a connector automatically syncs data (MANUAL / HOURLY / DAILY / WEEKLY / MONTHLY) | 5 | enums.md §SyncCadence |
| DocumentCategory | Category of a supporting document attached to an incentive; stored as string, not Java enum (terms-conditions / program-rules / faq) | 4 | enums.md §DocumentCategory |
| AllowedFileType | Permitted file extensions for incentive document uploads (pdf / xlsx / docx) | 5 | enums.md §AllowedFileType |
| DocumentSubmissionStatus | Review state of a user-submitted activity document (PENDING / APPROVED / REJECTED) | 3 | enums.md §DocumentSubmissionStatus |
| BuilderFieldType | Input widget type for a configurable builder field (TEXT_BOX / DROPDOWN / DATE_PICKER / TOGGLE) | 7 | enums.md §BuilderFieldType |
| ValueSource | Where a builder field's dropdown options are sourced from (LOCATION_HIERARCHY / STATIC / DATA_OBJECT_FIELD) | 5 | enums.md §ValueSource |
| QuarterMethod | Method used to divide a period into fiscal quarters (MONTHS / WEEKS / DAYS / CUSTOM) | 4 | enums.md §QuarterMethod |
| HomeDateFilter | Preset date range used to filter the home dashboard (LAST_30_DAYS / THIS_QUARTER / THIS_YEAR / CUSTOM) | 4 | enums.md §HomeDateFilter |
| HomeIncentiveTypeFilter | Incentive type grouping used to filter the home dashboard (ALL / SALES / ENABLEMENT / JOURNEYS) | 4 | enums.md §HomeIncentiveTypeFilter |
| NotificationCategory | Top-level topic category for a user notification (INCENTIVE / BUDGET / REWARDS / DATA) | 7 | enums.md §NotificationCategory |
| RecommendationType | Type of recommended item shown to a user (TRAINING / INCENTIVE) | 2 | enums.md §RecommendationType |
| RecommendationInteractionType | User action taken on a recommendation card (VIEWED / DISMISSED / COMPLETED) | 3 | enums.md §RecommendationInteractionType |
| AuditAction | Specific event recorded in an audit log entry (CREATED / EDITED / LOGGED_IN / ENROLLED / BANK_LINKED / BANK_UNLINKED) | 23 | enums.md §AuditAction |
| AuditActorType | Whether an audit action was performed by a human user or the system (USER / SYSTEM) | 2 | enums.md §AuditActorType |
| AuditResourceType | Entity type that an audit log entry refers to (INCENTIVE / USER / CONNECTOR / CLIENT / BALANCE_EXPIRATION_POLICY / PARTNER_REDEMPTION) | 25 | enums.md §AuditResourceType |
| PolicyType | Type of compliance policy document requiring user acceptance (PRIVACY_NOTICE / TERMS_OF_SERVICE / ANTI_BRIBERY_POLICY) | 3 | enums.md §PolicyType |
| ConsentType | Specific user consent scope captured at registration or in preferences (AI_RECOMMENDATIONS / MARKETING_EMAIL / ANALYTICS) | 3 | enums.md §ConsentType |
| KycStatus | Stage of Know Your Customer verification for a partner company (NOT_STARTED / IN_PROGRESS / APPROVED / EXPIRED) | 5 | enums.md §KycStatus |
| ComplianceRiskLevel | Assessed risk severity of a compliance concern (LOW / MEDIUM / HIGH / CRITICAL) | 4 | enums.md §ComplianceRiskLevel |
| ComplianceAlertType | Specific compliance trigger that generated an automated alert (VALUE_CAP_EXCEEDED / GOVERNMENT_DEAL_FLAGGED / KYC_EXPIRED) | 6 | enums.md §ComplianceAlertType |
| ComplianceAlertStatus | Review and resolution state of a compliance alert (NEW / INVESTIGATING / RESOLVED / DISMISSED) | 4 | enums.md §ComplianceAlertStatus |
| GovernmentDealRestrictionMode | Policy controlling how rewards to government-segment recipients are handled (NONE / STRICT / APPROVAL_REQUIRED / AUDIT_ONLY) | 4 | enums.md §GovernmentDealRestrictionMode |
| WhistleblowerReportType | Category of compliance concern reported via the whistleblower channel (SUSPICIOUS_INCENTIVE / POTENTIAL_KICKBACK / POLICY_VIOLATION) | 5 | enums.md §WhistleblowerReportType |
| WhistleblowerStatus | Lifecycle stage of a whistleblower report (NEW / UNDER_INVESTIGATION / RESOLVED / DISMISSED) | 5 | enums.md §WhistleblowerStatus |
| BreachSeverity | Impact severity of a data breach (LOW / MEDIUM / HIGH / CRITICAL) | 4 | enums.md §BreachSeverity |
| BreachStatus | Investigation and remediation stage of a data breach (DETECTED / INVESTIGATING / REPORTED / RESOLVED / CLOSED) | 5 | enums.md §BreachStatus |
| DpaStatus | Signature state of a Data Processing Agreement with a partner (PENDING / SIGNED / EXPIRED) | 3 | enums.md §DpaStatus |
| SccStatus | Status of Standard Contractual Clauses for cross-border data transfers (NOT_REQUIRED / REQUIRED / SIGNED / DEPENDS_ON_REGION) | 4 | enums.md §SccStatus |
| DataCategory | Type of platform data subject to retention policy scheduling (INACTIVE_USERS / AUDIT_LOGS / REWARD_TRANSACTIONS) | 6 | enums.md §DataCategory |
| RetentionActionType | Action applied to data records when a retention policy fires (ANONYMIZE / DELETE) | 2 | enums.md §RetentionActionType |
| ReturnStatus | Lifecycle state of a non-cash return request (PENDING_APPROVAL / APPROVED / RETURN_CONFIRMED / RETURN_REJECTED / CANCELLED / RETURN_TIMED_OUT) | 6 | enums.md §ReturnStatus |
| ReturnResolution | Admin or vendor resolution decision for a non-cash return (CONFIRM / REJECT) | 2 | enums.md §ReturnResolution |
| LedgerEntryType | Balance movement type on a reward wallet ledger entry (CREDIT / RESERVE / DEBIT / RELEASE / RETURN_CREDIT / REVERSAL / EXPIRY) | 7 | enums.md §LedgerEntryType |
| ExpirationMode | Whether a balance expiration policy expires on inactivity or a fixed date (INACTIVITY / FIXED_DATE) | 2 | enums.md §ExpirationMode |
| ExpiryNoticeStatus | Lifecycle state of a scheduled balance expiry event (SCHEDULED / NOTIFIED / EXPIRED / CANCELLED) | 4 | enums.md §ExpiryNoticeStatus |
| XtrmEnrollmentStatus | Lifecycle of a user's XTRM payout enrollment (NOT_ENROLLED / ENROLLED / FAILED) | 3 | enums.md §XtrmEnrollmentStatus |
| RedemptionPayoutMethod | XTRM payout rail chosen for a user's cash redemptions (ANYPAY / BANK / CARD) | 3 | enums.md §RedemptionPayoutMethod |
