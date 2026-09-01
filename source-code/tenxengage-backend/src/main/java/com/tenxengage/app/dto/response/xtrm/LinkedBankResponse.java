package com.tenxengage.app.dto.response.xtrm;

import com.tenxengage.app.entity.xtrm.PartnerLinkedBank;

import java.util.UUID;

/**
 * One linked bank in the user's payout profile (F-03 multi-bank). {@code id} is OUR row PK — the raw XTRM
 * beneficiary id is never exposed to the client. {@code isDefault} is true for the bank the BANK rail pays.
 */
public record LinkedBankResponse(
        UUID id,
        String label,
        String currency,
        boolean isDefault) {

    public static LinkedBankResponse of(PartnerLinkedBank bank, boolean isDefault) {
        return new LinkedBankResponse(bank.getId(), bank.getMaskedLabel(), bank.getCurrency(), isDefault);
    }
}
