# F-08: Advanced Redemption Analytics

> **Slug**: `redemption-analytics-advanced` · **Roadmap**: `redemption-store` · **Phase**: 2 · **Recommended order**: 8th
> **Roadmap**: [../roadmap.md](../roadmap.md) · **Digest**: [../digest.md](../digest.md)
> **BRD anchors**: "Redemption Rate and Analytics Dashboard", "Release Strategy — Phase 2"

## Business outcome

Client Admins can diagnose *why* their redemption rate is what it is — breaking down performance by catalog item, partner segment, and time — so they can make targeted adjustments to the catalog, thresholds, and regional offerings that improve program ROI.

## Primary persona of record

**`CLIENT_ADMIN`** — Uses advanced analytics to optimize catalog curation, identify underperforming segments, and report program performance to stakeholders.

## Secondary personas

None — Client Admin only.

## User journey (sketch)

Client Admin opens the advanced analytics view (extending the Phase 1 dashboard), selects the Tier breakdown, and sees that Enterprise partners have a 60% redemption rate while Starter partners are at 12% — suggesting thresholds are too high for Starter. They drill into the APAC region, see that gift card options are rarely redeemed there, and use this to inform a regional catalog reconfiguration in F-02.

## Functional requirements (business intent)

1. **FR-08.1** — Client Admin can view redemption rate broken down by catalog item, identifying the most and least redeemed items.
2. **FR-08.2** — Client Admin can view redemption rate broken down by partner tier, region, and role — identifying which segments are engaging vs. not.
3. **FR-08.3** — Client Admin can view average time-to-first-redemption: how long after earning do partners redeem for the first time, segmented by partner tier.
4. **FR-08.4** — Dashboard shows redemption rate trends over configurable time windows (7 days, 30 days, 90 days, custom range).
5. **FR-08.5** — Client Admin can view an unredeemed balance liability trend report per currency type over time, exportable as CSV.
6. **FR-08.6** — Dashboard analytics can be filtered by partner tier, region, and date range in combination.
7. **FR-08.7** — Client Admin can view the failed and cancelled redemption rate by processing mode and by catalog item to identify where fulfillment friction is highest.

## Business rules

- Analytics are scoped to the Client Admin's tenant — no cross-tenant data.
- Segment breakdowns (tier, region, role) derive from the partner user's profile attributes at the time of analysis — not at the time of the original transaction.

## Constraints / validations

- Depends on F-07 (Phase 1) being live; F-08 extends the same dashboard.

## Edge cases / open questions

- Should the advanced analytics be a separate page or an extended tab within the Phase 1 dashboard?
- Is a separate reporting data store (e.g., Snowflake or materialized views) required for cross-dimensional analytics, or can this run against the primary database?

## Dependencies

- **Features**: F-07 (Phase 1 basic analytics must be live), F-03, F-01

## Riskiest unknown

Whether the dimensional analytics (by tier × region × currency type × time) can be served from the primary PostgreSQL database or require a Snowflake / data warehouse query — which would introduce a separate data pipeline dependency.

## Candidate domain concepts (business nouns)

- **Segment breakdown**: Redemption rate analysis partitioned by partner tier, region, or role.
- **Time-to-first-redemption**: The elapsed time between a partner's first earning event and their first completed redemption.
- **Redemption rate trend**: The change in redemption rate over time, surfaced as a chart or time-series table.

## Cross-feature / cross-quadrant signals (business intent)

| Direction | Counterparty | Business intent |
|---|---|---|
| Receives from | F-07 (Basic Analytics) | Extends the same data foundation with dimensional breakdowns |
| Receives from | F-03, F-01 | Full transaction and wallet dataset for segment-level analysis |

---

## Suggested story seeds

| # | Title | Business outcome | Type | Depends on |
|---|---|---|---|---|
| S-01 | View redemption rate by catalog item | Client Admin identifies most and least redeemed items to optimize catalog curation | reporting | F-07.S-01 |
| S-02 | View redemption by partner segment | Client Admin sees breakdowns by tier, region, and role to identify engagement gaps | reporting | S-01 |
| S-03 | View time-to-first-redemption | Client Admin understands how quickly new partners convert earned rewards | reporting | S-01 |
| S-04 | View redemption rate trends over time | Client Admin tracks engagement trends across configurable time windows | reporting | S-01 |
| S-05 | Filter analytics by tier, region, and date | Client Admin combines multiple filters for targeted program diagnosis | UI | S-02 |

---

## `/create-spec` invocation

```
/create-spec redemption-store F-08
```
