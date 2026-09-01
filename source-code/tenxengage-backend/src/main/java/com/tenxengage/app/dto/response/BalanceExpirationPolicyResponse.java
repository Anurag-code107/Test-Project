package com.tenxengage.app.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.tenxengage.app.entity.BalanceExpirationPolicy;
import com.tenxengage.app.entity.enums.ExpirationMode;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

// @JsonInclude.ALWAYS overrides the global NON_NULL setting so nullable fields
// (inactivityDays, fixedExpiryDate, enabledAt) are always present in the JSON —
// typed clients need stable field presence even when value is null.
@JsonInclude(JsonInclude.Include.ALWAYS)
public record BalanceExpirationPolicyResponse(
        String currencyId,
        String currencyDisplayName,
        boolean enabled,
        ExpirationMode expirationMode,
        Integer inactivityDays,
        LocalDate fixedExpiryDate,
        Integer leadTimeDays,
        Instant enabledAt,
        Instant updatedAt
) {

    private static final Map<String, String> CURRENCY_DISPLAY_NAMES = Map.of(
            "cash", "Cash",
            "points", "Points",
            "credits", "Credits",
            "tickets", "Tickets"
    );

    public static BalanceExpirationPolicyResponse from(BalanceExpirationPolicy policy) {
        String displayName = CURRENCY_DISPLAY_NAMES.getOrDefault(
                policy.getCurrencyId() != null ? policy.getCurrencyId().toLowerCase() : "",
                policy.getCurrencyId()
        );
        return new BalanceExpirationPolicyResponse(
                policy.getCurrencyId(),
                displayName,
                policy.isEnabled(),
                policy.getExpirationMode(),
                policy.getInactivityDays(),
                policy.getFixedExpiryDate(),
                policy.getLeadTimeDays(),
                policy.getEnabledAt(),
                policy.getUpdatedAt()
        );
    }
}
