package com.tenxengage.app.dto.response;

import java.util.Map;

public record ClaimSummaryResponse(
    String totalEarnings,
    Map<String, String> currencyBreakdown,
    long claimedCount,
    long unclaimedCount
) {}
