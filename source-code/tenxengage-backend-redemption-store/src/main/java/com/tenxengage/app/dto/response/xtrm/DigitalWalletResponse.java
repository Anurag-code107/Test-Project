package com.tenxengage.app.dto.response.xtrm;

import com.tenxengage.app.service.xtrm.XtrmApiClient.WalletInfo;

import java.math.BigDecimal;

/**
 * A user's XTRM digital wallet (view-only, F-03 digital-wallet enhancement). Exposes only id/name/currency/
 * balance — the balance is formatted client-side with the currency symbol + ISO code. Internal XTRM fields
 * (EntityID, IsBankLinked, Type) are deliberately omitted.
 */
public record DigitalWalletResponse(String id, String name, String currency, BigDecimal balance) {

    public static DigitalWalletResponse of(WalletInfo w) {
        return new DigitalWalletResponse(w.id(), w.name(), w.currency(), w.balance());
    }
}
