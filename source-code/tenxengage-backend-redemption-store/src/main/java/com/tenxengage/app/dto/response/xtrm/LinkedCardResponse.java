package com.tenxengage.app.dto.response.xtrm;

import com.tenxengage.app.entity.xtrm.PartnerLinkedCard;

import java.util.UUID;

/**
 * One linked card in the user's payout profile (F-03 multi-card). {@code id} is OUR row PK — the raw XTRM
 * {@code CardToken} is never exposed to the client. {@code label} is a masked display string
 * ("Visa ••1111"); {@code isDefault} is true for the card the CARD rail pays.
 */
public record LinkedCardResponse(
        UUID id,
        String label,
        String cardType,
        String status,
        boolean isDefault) {

    public static LinkedCardResponse of(PartnerLinkedCard card, boolean isDefault) {
        return new LinkedCardResponse(card.getId(), label(card), card.getCardType(), card.getStatus(), isDefault);
    }

    /** Masked display label, e.g. {@code "Visa ••1111"} — never contains the full number. */
    private static String label(PartnerLinkedCard card) {
        String type = (card.getCardType() == null || card.getCardType().isBlank()) ? "Card" : card.getCardType();
        String last4 = card.getMaskedLast4();
        return (last4 == null || last4.isBlank()) ? type : type + " ••" + last4;
    }
}
