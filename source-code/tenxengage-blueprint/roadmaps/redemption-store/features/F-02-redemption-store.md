# F-02: Redemption Catalog

> **Slug**: `redemption-catalog` · **Roadmap**: `redemption-store` · **Phase**: 1 · **Recommended order**: 2nd
> **Roadmap**: [../roadmap.md](../roadmap.md) · **Digest**: [../digest.md](../digest.md)
> **BRD anchors**: "Redemption Catalog", "Regional Catalog Configuration", "Payout Speed and SLAs", "Permissions and Feature Flags"

## Business outcome

Client Admins gain a self-service catalog configuration tool that lets them curate the redemption options available to their partner ecosystem — by item, processing mode, threshold, and region — without platform team involvement. Partner sellers see only what they can actually redeem, surfaced with local relevance and payout timelines shown upfront.

## Primary persona of record

**`CLIENT_ADMIN`** — The tenant configurator who shapes which catalog items are available, at what thresholds, in what modes, and for which regions. Their decisions directly determine what partners see.

## Secondary personas

- **TenXEngage Platform Admin** *(new role)* — Creates and maintains the global catalog items that Client Admins configure from.
- **`PARTNER_SELLER`** and **`PARTNER_ADMIN`** — Browse the tenant-configured, currency-filtered catalog to select redemption items.

## User journey (sketch)

**Platform Admin journey**: Platform Admin navigates to the global catalog manager (top-level platform admin area in main app), creates a new non-cash catalog item sourced from Xoxoday with its provider ID, sets geographic scope and default processing mode, and marks it as globally active. All Client Admins can now see it in their configuration panel.

**Client Admin journey**: Client Admin navigates to the Redemption section in their settings panel, sees the global catalog, enables specific items for their tenant, overrides the processing mode for one item to Approval Required, sets the minimum wallet balance to 500 points, configures a 30-day return window, and restricts an Amazon gift card to North America only. Changes reflect immediately to partner users.

**Partner journey**: Partner Seller navigates to the Redemption Store, sees only items enabled by their Client Admin and compatible with their current point balance. Items they cannot afford are shown with a shortfall indicator. Each item shows its estimated payout timeline before they select it.

## Functional requirements (business intent)

1. **FR-02.1** — Platform Admin can create, edit, and deactivate global catalog items; each item specifies its category (cash or non-cash), compatible currency type(s), minimum redemption amount, default processing mode (Instant/Batch/Approval Required), geographic availability (ISO country codes), provider item ID, and whether it supports returns.
2. **FR-02.2** — Platform Admin can manage vendor API credentials for XTRM and Xoxoday and monitor integration health including webhook delivery logs and catalog sync status.
3. **FR-02.3** — The platform periodically syncs the Xoxoday catalog to keep global non-cash catalog items current; items removed by Xoxoday are automatically deactivated.
4. **FR-02.4** — Client Admin can enable or disable any globally active catalog item for their tenant; changes are immediately visible to partner users browsing the catalog.
5. **FR-02.5** — Client Admin can override the default processing mode for any enabled item at the tenant level, and set per-item minimum transaction amounts, minimum wallet balance thresholds, and return window durations (in days).
6. **FR-02.6** — Client Admin can configure a `batchCadence` (daily or weekly) for their tenant's batch-mode redemptions; this setting applies to all items configured with Batch processing mode.
7. **FR-02.7** — Client Admin can configure catalog item availability at the regional level within their tenant — enabling an item for specific regions (e.g., APAC) while disabling it for others (e.g., EMEA); regional configuration is layered on top of global availability and a Client Admin cannot enable an item for a region where the vendor does not support it.
8. **FR-02.8** — If no regional override is set for an item, the tenant-level default enablement applies to all regions.
9. **FR-02.9** — Partners browsing the catalog see only items enabled by their Client Admin, compatible with the currency types they currently hold, and available in their geographic region; items they cannot afford due to balance shortfall are shown but marked unavailable with the shortfall amount displayed.
10. **FR-02.10** — The catalog is organized by currency type; non-cash items surface localized options by default based on the partner's country via Xoxoday's region-aware catalog API.
11. **FR-02.11** — Each catalog item detail displays the estimated payout timeline for the item's processing mode before the partner submits a redemption.

## Business rules

- A Client Admin cannot enable an item for a region where Xoxoday does not support it; the platform enforces this hard limit.
- Items deactivated globally by Platform Admin are immediately hidden from all tenant catalogs regardless of Client Admin configuration.
- The minimum wallet balance threshold is checked at redemption submission time (F-03), not at catalog display time; items where the partner's balance is below threshold are displayed but the submission will be blocked.
- Cash catalog items (XTRM) are always amount-based; non-cash items (Xoxoday) are also amount-based (confirmed — no quantity model in v1).

## Constraints / validations

- Catalog items must have a valid provider item ID before they can be made globally active.
- Regional enablement must be a subset of the item's `geographicScope` — Client Admin cannot expand geographic availability beyond what the vendor supports.
- `batchCadence` must be one of: daily, weekly.
- `returnWindowDays` must be a positive integer; zero means returns are not accepted.

## Edge cases / open questions

- **Platform Admin cross-tenant access (ADR — blocking for this feature)**: The main platform enforces `X-Client-Subdomain` on all requests. Platform Admin needs cross-tenant access for global catalog operations. Resolve before /create-spec: does the global catalog API live in `tenxengage-admin-backend` (called from main FE) or in main backend with a special cross-tenant permission scope?
- How are catalog items seeded for a new tenant when they first onboard — are all globally active items auto-disabled-by-default, or is there a default enablement policy?
- What is the "no regional override" default behavior for catalog items with geographic restrictions — should they be visible globally or only in the vendor-supported regions?

## Dependencies

- **Features**: F-01 (wallet balance needed for catalog shortfall display and currency-aware filtering)
- **ADRs**: Platform Admin cross-tenant access mechanism must be resolved before spec freeze
- **External counterparties**: Xoxoday catalog sync API; XTRM item catalog

## Riskiest unknown

The cross-tenant access model for Platform Admin operations. If the platform's tenant filter is applied at the Hibernate level to all repository calls, a Platform Admin writing a global catalog item with no `client_id` will be rejected by the filter. The spec must define exactly how Platform Admin bypasses or operates outside the standard tenant isolation layer — this is an architectural decision that affects both backend and the security model.

## Candidate domain concepts (business nouns)

- **Global catalog item**: A redeemable reward option defined by Platform Admin, available for any tenant to enable.
- **Tenant catalog configuration**: A Client Admin's per-tenant view of the global catalog — which items are enabled, at what thresholds, in what modes, and for which regions.
- **Regional catalog override**: A Client Admin's per-region enablement of a specific catalog item within their tenant.
- **Currency-aware catalog**: The partner's view of the catalog — filtered to items redeemable with the currencies they currently hold.
- **Processing mode**: The fulfillment timing mode for a catalog item: Instant, Batch, or Approval Required.
- **Return window**: The number of days after fulfillment within which a partner may submit a return for a non-cash item.

## Cross-feature / cross-quadrant signals (business intent)

| Direction | Counterparty | Business intent |
|---|---|---|
| Receives from | F-01 (Wallet & Ledger) | Available balance per currency type determines which catalog items are shown as redeemable vs. shortfall-flagged |
| Sends to | F-03 (Redemption Flow) | Catalog item routing category (cash/non-cash), processing mode, and minimum amounts govern redemption submission and vendor handoff |
| Sends to | Xoxoday | Periodic catalog sync requests to keep non-cash inventory current |

---

## Suggested story seeds

| # | Title | Business outcome | Type | Depends on |
|---|---|---|---|---|
| S-01 | Manage global catalog items | Platform Admin can create and maintain the master set of redeemable items across both vendors | admin | — |
| S-02 | Configure tenant catalog | Client Admin enables/disables items and sets thresholds for their partner program | admin | S-01 |
| S-03 | Configure regional catalog | Client Admin controls which items are available to partners by region within their tenant | admin | S-02 |
| S-04 | Browse currency-aware catalog | Partners see only items they can redeem with their current balances, organized by currency type, with payout timelines shown | UI | F-01.S-01 |
| S-05 | Sync Xoxoday catalog | Platform keeps the non-cash catalog current with Xoxoday's item inventory and geographic availability | integration | S-01 |

---

## `/create-spec` invocation

```
/create-spec redemption-store F-02
```
