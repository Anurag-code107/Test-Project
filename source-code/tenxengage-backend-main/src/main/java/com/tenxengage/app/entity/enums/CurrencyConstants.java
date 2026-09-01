package com.tenxengage.app.entity.enums;

import java.util.Set;

/**
 * @deprecated Use {@code CurrencyService.getMonetaryCodes(clientId)} for per-tenant monetary currency lookup.
 * Kept as a fallback for code paths where tenant context is unavailable.
 */
@Deprecated
public final class CurrencyConstants {

    private CurrencyConstants() {}

    public static final Set<String> MONETARY_CURRENCIES = Set.of("cash", "points");
}
