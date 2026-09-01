package com.tenxengage.app.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

public record AnnualRewardSummaryResponse(
    UUID userId,
    String firstName,
    String lastName,
    String email,
    String countryCode,
    String partnerCompanyName,
    String currencyId,
    int year,
    BigDecimal totalAwarded,
    long transactionCount
) {}
