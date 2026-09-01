package com.tenxengage.app.entity.enums;

/**
 * How a company distribution reaches its recipient.
 *
 * <p>The first two leave the platform through XTRM and therefore create a
 * {@link com.tenxengage.app.entity.RedemptionRequest} payout leg. {@link #WALLET_CREDIT} does not — it moves
 * money inside the platform, so it has no vendor, no webhook, and deliberately no redemption row: counting
 * it as a redemption would count it again when the seller eventually redeems that balance.</p>
 */
public enum DistributionRail {

    /** XTRM digital gift card (XTR94505), delivered to the recipient's email. Requires an enrolled payee. */
    GIFT_CARD("Gift Card"),

    /** XTRM bank/ACH (XTR94500) into the recipient's default linked bank. */
    BANK_TRANSFER("Bank Transfer"),

    /**
     * Company wallet → the recipient's own <b>system cash wallet</b> ({@code reward_wallets},
     * {@code INDIVIDUAL}). Not their XTRM digital wallet. Internal ledger only, so any active seller can
     * receive one — which is what makes it the answer for sellers who have no payout profile yet.
     */
    WALLET_CREDIT("Wallet Transfer");

    private final String displayName;

    DistributionRail(String displayName) {
        this.displayName = displayName;
    }

    /** Human-facing label for UI/API. The stored value stays the enum name. */
    public String getDisplayName() {
        return displayName;
    }

    /** True for the rails that move money out through XTRM and so need a payout leg. */
    public boolean isVendorPayout() {
        return this != WALLET_CREDIT;
    }
}
