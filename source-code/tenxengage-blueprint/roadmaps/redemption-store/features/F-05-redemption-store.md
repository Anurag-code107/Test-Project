# F-05: Transaction History & Export

> **Slug**: `redemption-history` · **Roadmap**: `redemption-store` · **Phase**: 1 · **Recommended order**: 5th
> **Roadmap**: [../roadmap.md](../roadmap.md) · **Digest**: [../digest.md](../digest.md)
> **BRD anchors**: "Transaction History and Reporting"

## Business outcome

Partners have a permanent, filterable receipt book for all their redemptions and returns — always accessible and always exportable. Client Admins get a complete tenant-wide view for program reporting and audits. No redemption is ever lost or hard to find.

## Primary persona of record

**`PARTNER_SELLER`** — The primary user of transaction history; needs to track their redemption status, find specific transactions, and export for personal records.

## Secondary personas

- **`PARTNER_ADMIN`** — Views and exports company wallet transaction history.
- **`CLIENT_ADMIN`** — Views and exports the full tenant-wide transaction history across all users and companies.

## User journey (sketch)

Partner Seller navigates to their transaction history (accessible from the Redemption Store or via the balance widget), filters by date range and status "COMPLETED", finds a specific gift card redemption, clicks through to see the full detail including the Xoxoday reference ID, and exports the last 90 days as CSV for their expense report.

## Functional requirements (business intent)

1. **FR-05.1** — Partner Seller and Partner Admin can view their own redemption transaction history listing all redemptions and return transactions with: transaction ID, catalog item name, currency type, amount, status, submission timestamp, completion timestamp, provider name (vendor), and provider reference ID.
2. **FR-05.2** — Transaction history is paginated and filterable by date range, transaction status, and redemption type (cash / non-cash).
3. **FR-05.3** — Return transactions in the history are linked to their originating redemption transaction so the partner can trace the full lifecycle from submission through return outcome.
4. **FR-05.4** — Partner Seller and Partner Admin can export their own transaction history as CSV or XLSX.
5. **FR-05.5** — Client Admin can view the full tenant-wide transaction history across all partner users and companies, with the same filtering capabilities plus the ability to filter by user and by company.
6. **FR-05.6** — Client Admin can export the full tenant transaction history as CSV or XLSX; the export includes requesting user, company, all transaction detail fields, and return linkage.
7. **FR-05.7** — Individual transaction detail displays all lifecycle timestamps, provider reference ID, failure reason (if applicable), and the linked return request (if one was filed).

## Business rules

- Vendor names (XTRM, Xoxoday) are stored on the transaction record at routing time and visible in history — these are internal labels visible in admin export, not shown as vendor names in the partner-facing UI (per vendor-transparent UX principle).
- Return transactions must remain linked to their originating redemption even if the return is rejected.
- Transaction history is read-only; records cannot be edited or deleted.

## Constraints / validations

- Export must support both CSV and XLSX formats.
- Pagination must be supported for large result sets; the system cannot return unbounded result sets.

## Edge cases / open questions

- Should the export have a max record limit per request, or support streaming for large tenant histories?
- How far back does transaction history go — is there a retention policy, or is it indefinite?

## Dependencies

- **Features**: F-03 (redemption transactions created here), F-06 (return transactions linked here)

## Riskiest unknown

Export performance for large tenants with many users and many transactions. If all-tenant export runs synchronously, it will time out at scale. /create-spec must decide: synchronous download for small datasets vs. async generation + download link for large datasets.

## Candidate domain concepts (business nouns)

- **Transaction history**: The paginated, filterable list of all redemption and return events for a user, company, or tenant.
- **Transaction detail**: The full record of a single redemption including its lifecycle timestamps, vendor reference, and any linked return.
- **Export**: A downloadable snapshot of transaction history in CSV or XLSX format.

## Cross-feature / cross-quadrant signals (business intent)

| Direction | Counterparty | Business intent |
|---|---|---|
| Receives from | F-03 (Redemption Flow) | Redemption transactions are recorded and surfaced in history |
| Receives from | F-06 (Returns) | Return transactions are linked to their originating redemptions |
| Sends to | F-06 (Returns) | Return initiation is triggered from a transaction history entry |

---

## Suggested story seeds

| # | Title | Business outcome | Type | Depends on |
|---|---|---|---|---|
| S-01 | View personal redemption history | Partner sees a filterable, status-tracked list of all their redemptions with full detail | UI | F-03.S-01 |
| S-02 | View company redemption history | Partner Admin sees redemption activity for the company wallet separately | UI | F-03.S-02 |
| S-03 | Export personal transaction data | Partner downloads their own redemption history as CSV or XLSX | reporting | S-01 |
| S-04 | View and export tenant-wide history | Client Admin sees and exports redemption activity across all users and companies | reporting | S-01 |

---

## `/create-spec` invocation

```
/create-spec redemption-store F-05
```
