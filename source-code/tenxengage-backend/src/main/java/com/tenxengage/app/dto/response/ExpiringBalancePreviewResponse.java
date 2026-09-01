package com.tenxengage.app.dto.response;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

public record ExpiringBalancePreviewResponse(
        String currencyId,
        String currencyDisplayName,
        LocalDate scheduledExpiryDate,
        long affectedWalletCount,
        // Contract mandates type:string (BigDecimal string representation) — ToStringSerializer ensures JSON string output
        @JsonSerialize(using = ToStringSerializer.class)
        BigDecimal totalAmountAtRisk
) {

    private static final Map<String, String> CURRENCY_DISPLAY_NAMES = Map.of(
            "cash", "Cash",
            "points", "Points",
            "credits", "Credits",
            "tickets", "Tickets"
    );

    public static ExpiringBalancePreviewResponse of(
            String currencyId,
            LocalDate scheduledExpiryDate,
            long affectedWalletCount,
            BigDecimal totalAmountAtRisk) {
        String displayName = CURRENCY_DISPLAY_NAMES.getOrDefault(
                currencyId != null ? currencyId.toLowerCase() : "",
                currencyId
        );
        return new ExpiringBalancePreviewResponse(
                currencyId,
                displayName,
                scheduledExpiryDate,
                affectedWalletCount,
                totalAmountAtRisk
        );
    }
}
