package com.tenxengage.app.dto.request.xtrm;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Select which linked card is the default payout destination for the CARD rail (F-03 multi-card).
 * {@code cardId} is OUR {@code partner_linked_card} row PK — never the raw XTRM {@code CardToken}.
 */
public record SetDefaultCardRequest(@NotNull UUID cardId) {
}
