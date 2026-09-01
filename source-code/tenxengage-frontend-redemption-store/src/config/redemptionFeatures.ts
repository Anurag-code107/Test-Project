/**
 * Client-side "not built yet" gates for redemption surfaces.
 *
 * These are NOT tier / subscription flags (see `featureModules.ts` for those) — they hide
 * UI for capabilities the platform does not yet support end-to-end. Flip a flag to `true`
 * once the corresponding flow is implemented; every gated surface re-appears together.
 */

/**
 * Company-wallet redemption. Today only personal (individual-wallet) redemptions are
 * supported, so the Partner-Admin company surfaces are hidden:
 *  - "Redeem (Company)" button in CatalogItemDetailSheet.tsx
 *  - "Company" tab in the transaction-history page (TransactionHistoryPage.tsx)
 *
 * Backend note: the export scope logic and company-history endpoints already exist; this
 * flag only controls whether the UI exposes the company path. Re-enable when company
 * redemption is shipped.
 */
export const COMPANY_REDEMPTION_ENABLED: boolean = false;

/**
 * Catalog "Geographic Scope" field (and the region-based gating it feeds).
 *
 * Today geographic scope has no functional effect on the seller flow: it does not filter
 * catalog visibility or redeemability, and the seller store never sends a region param, so
 * the per-region config it gates is inert. It is only a vendor allowlist + display tag —
 * misleading to admins who assume it restricts by geography. Hidden until the full geo chain
 * is wired (derive seller region -> filter browse -> enforce at redeem).
 *
 * Hiding only removes the input from GlobalCatalogItemForm; the geographicScope value is still
 * carried in the form payload, so existing items keep their scope on edit and new items are
 * created with an empty (global) scope.
 */
export const CATALOG_GEOGRAPHIC_SCOPE_ENABLED: boolean = false;
