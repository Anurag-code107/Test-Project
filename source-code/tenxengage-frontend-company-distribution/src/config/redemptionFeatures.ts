/**
 * Client-side "not built yet" gates for redemption surfaces.
 *
 * These are NOT tier / subscription flags (see `featureModules.ts` for those) — they hide
 * UI for capabilities the platform does not yet support end-to-end. Flip a flag to `true`
 * once the corresponding flow is implemented; every gated surface re-appears together.
 */

/**
 * The two distribution rails that need XTRM: Gift Card and Bank Transfer.
 *
 * XTRM has no company-to-user transfer API — its `TransferFund` always sources from the ISSUER's own
 * wallet, so a partner company's wallet cannot fund a payout. Until XTRM ships that, both rails are shown
 * disabled with a reason and only Wallet Transfer can be sent.
 *
 * Wallet Transfer is deliberately unaffected: it moves money inside our own ledger and calls no vendor, so
 * the Distribution Store stays genuinely usable rather than being switched off wholesale.
 *
 * This flag is UX only. The server enforces the same rule in DistributionRecipientService, which refuses a
 * disabled rail on submit regardless of what the client sends — flip both together
 * (`XTRM_PAYOUT_RAILS_ENABLED=true`) when XTRM is ready.
 */
export const XTRM_PAYOUT_RAILS_ENABLED: boolean = true;

/**
 * Copy shown on a disabled rail. Kept identical to the server's reason so the two never disagree.
 *
 * No longer names a remedy: the wallet rail it used to point at was retired on 2026-08-26, and both
 * remaining rails need XTRM — so this state means distribution is off entirely.
 */
export const XTRM_RAIL_UNAVAILABLE_REASON = "Temporarily unavailable";

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
