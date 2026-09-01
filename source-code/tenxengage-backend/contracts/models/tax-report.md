# Tax Report Models

Aggregated reward data for tax compliance and employer benefit-in-kind reporting.

## AnnualRewardSummaryResponse

Client-level annual summary of all rewards issued.

| Field | Type | Required | Constraints | Notes |
|-------|------|----------|-------------|-------|
| year | integer | yes | — | Tax year |
| clientId | UUID | yes | foreign key | References Client |
| totalRewardValueUsd | BigDecimal | yes | string representation | Total USD value of all rewards issued |
| totalRecipients | integer | yes | — | Distinct count of reward recipients |
| totalTransactions | integer | yes | — | Total number of reward transactions |
| breakdownByCurrency | CurrencyBreakdown[] | yes | — | Totals grouped by reward currency |
| breakdownByPartner | PartnerBreakdown[] | yes | — | Totals grouped by partner company |

### CurrencyBreakdown (inline object)

| Field | Type | Required | Constraints | Notes |
|-------|------|----------|-------------|-------|
| rewardCurrency | string | yes | — | Reward currency name (e.g. "cash", "points") |
| totalValue | BigDecimal | yes | string representation | Total value in this currency |
| transactionCount | integer | yes | — | Number of transactions in this currency |

### PartnerBreakdown (inline object)

| Field | Type | Required | Constraints | Notes |
|-------|------|----------|-------------|-------|
| partnerCompanyId | UUID | yes | foreign key | References PartnerCompany |
| partnerCompanyName | string | yes | — | Display name |
| totalValueUsd | BigDecimal | yes | string representation | Total USD value for this partner |
| recipientCount | integer | yes | — | Distinct recipients in this partner |

## EmployerBikReportResponse

Per-partner-company benefit-in-kind report for employer tax obligations.

| Field | Type | Required | Constraints | Notes |
|-------|------|----------|-------------|-------|
| year | integer | yes | — | Tax year |
| partnerCompanyId | UUID | yes | foreign key | References PartnerCompany |
| partnerCompanyName | string | yes | — | Display name |
| country | string | yes | — | Country of the partner company |
| totalBikValueUsd | BigDecimal | yes | string representation | Total benefit-in-kind value in USD |
| recipients | RecipientDetail[] | yes | — | Per-recipient breakdown |

### RecipientDetail (inline object)

| Field | Type | Required | Constraints | Notes |
|-------|------|----------|-------------|-------|
| userId | UUID | yes | foreign key | References User |
| fullName | string | yes | — | Recipient's full name |
| totalValueUsd | BigDecimal | yes | string representation | Total reward value for this recipient |
| transactionCount | integer | yes | — | Number of reward transactions |

## Notes

- All monetary values use string representation to avoid floating-point precision issues
- Currency conversion to USD uses the platform conversion rates (Cash 1:1, Points 200:1)
- Non-monetary rewards (credits, tickets) are excluded from tax reports as they have no USD equivalent
- The tax export endpoint provides the same data in CSV/XLSX format for external reporting systems
