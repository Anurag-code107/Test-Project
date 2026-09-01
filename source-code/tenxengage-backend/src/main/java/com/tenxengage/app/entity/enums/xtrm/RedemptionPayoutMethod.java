package com.tenxengage.app.entity.enums.xtrm;

/**
 * XTRM payout rail chosen for a user's cash redemptions (v1).
 *
 * <ul>
 *   <li>{@code ANYPAY} — XTRM AnyPay Individual ({@code XTR94502}); credits the recipient's XTRM wallet. Default.</li>
 *   <li>{@code BANK} — XTRM Bank / ACH ({@code XTR94500}); requires a linked bank ({@code partner_linked_bank_id}).</li>
 *   <li>{@code CARD} — XTRM Rapid Transfer ({@code XTR94508}); requires a linked card ({@code partner_linked_card_id}); pushed to the card via {@code CardToken}.</li>
 * </ul>
 *
 * Prepaid / Check / Gift-card rails and AnyPay Company are out of scope for v1.
 */
public enum RedemptionPayoutMethod {
    ANYPAY("Digital Wallet"),
    BANK("Bank Account"),
    CARD("Card");

    private final String displayName;

    RedemptionPayoutMethod(String displayName) {
        this.displayName = displayName;
    }

    /** Human-facing name for UI/API. The stored value stays the enum name (e.g. {@code ANYPAY}). */
    public String getDisplayName() {
        return displayName;
    }
}
