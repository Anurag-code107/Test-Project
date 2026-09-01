package com.tenxengage.app.entity.enums;

/**
 * Lifecycle of a {@link DistributionRail#WALLET_CREDIT} distribution item.
 *
 * <p>Only that rail stores a status. Gift-card and bank items point at a
 * {@link com.tenxengage.app.entity.RedemptionRequest} and read their status from there, so there is exactly
 * one owner of the truth per item ({@code chk_distribution_item_leg} enforces it).</p>
 *
 * <p>The reserve step is what makes per-recipient settlement safe. Without it, settling recipients in
 * separate transactions would let a concurrent distribution spend balance that later recipients were
 * counting on — recipient 400 failing for insufficient funds after 1–399 were already paid.</p>
 */
public enum DistributionItemStatus {

    /** Funds earmarked on the company wallet at submit; the debit/credit pair has not run yet. */
    RESERVED,

    /** Company wallet debited and the recipient's cash wallet credited, in one transaction. Terminal. */
    COMPLETED,

    /** Definitively rejected; this recipient's share was released back to available. Terminal. */
    FAILED
}
