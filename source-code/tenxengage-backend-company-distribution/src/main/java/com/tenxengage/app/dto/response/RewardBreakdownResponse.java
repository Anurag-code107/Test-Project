package com.tenxengage.app.dto.response;

import com.tenxengage.app.entity.RewardTransaction;
import com.tenxengage.app.entity.enums.CurrencyConstants;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public record RewardBreakdownResponse(
    Map<String, String> monetary,
    Map<String, String> nonMonetary
) {

    public static RewardBreakdownResponse from(List<RewardTransaction> transactions) {
        return from(transactions, null, CurrencyConstants.MONETARY_CURRENCIES);
    }

    public static RewardBreakdownResponse from(List<RewardTransaction> transactions,
                                                 Map<String, BigDecimal> fallbackByCurrency) {
        return from(transactions, fallbackByCurrency, CurrencyConstants.MONETARY_CURRENCIES);
    }

    public static RewardBreakdownResponse from(List<RewardTransaction> transactions,
                                                 Map<String, BigDecimal> fallbackByCurrency,
                                                 Set<String> monetaryCodes) {
        Map<String, BigDecimal> byCurrency = transactions.stream()
            .collect(Collectors.groupingBy(
                RewardTransaction::getCurrencyId,
                Collectors.reducing(BigDecimal.ZERO, RewardTransaction::getAmountAwarded, BigDecimal::add)
            ));

        // For unclaimed deals with no transactions, use tagged deal currency breakdown as fallback
        if (byCurrency.isEmpty() && fallbackByCurrency != null && !fallbackByCurrency.isEmpty()) {
            byCurrency.putAll(fallbackByCurrency);
        }

        Map<String, String> monetary = new LinkedHashMap<>();
        Map<String, String> nonMonetary = new LinkedHashMap<>();

        byCurrency.forEach((currencyId, amount) -> {
            if (monetaryCodes.contains(currencyId)) {
                monetary.put(currencyId, amount.stripTrailingZeros().toPlainString());
            } else {
                nonMonetary.put(currencyId, amount.stripTrailingZeros().toPlainString());
            }
        });

        return new RewardBreakdownResponse(monetary, nonMonetary);
    }
}
