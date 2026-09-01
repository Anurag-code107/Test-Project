package com.tenxengage.app.dto.request.xtrm;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Select which linked bank is the default payout destination for the BANK rail (F-03 multi-bank).
 * {@code bankId} is OUR {@code partner_linked_bank} row PK — never the raw XTRM beneficiary id.
 */
public record SetDefaultBankRequest(@NotNull UUID bankId) {
}
