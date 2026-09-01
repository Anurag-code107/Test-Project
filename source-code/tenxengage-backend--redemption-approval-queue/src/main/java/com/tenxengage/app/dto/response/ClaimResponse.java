package com.tenxengage.app.dto.response;

import com.tenxengage.app.entity.enums.ClaimStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record ClaimResponse(
    UUID id,
    String orderNumber,
    LocalDate orderDate,
    ClaimStatus status,
    String partnerCompanyName,
    UUID partnerCompanyId,
    String region,
    BigDecimal totalAmount,
    BigDecimal totalMonetaryReward,
    RewardBreakdownResponse rewardBreakdown,
    List<ClaimerInfo> claimers,
    int eligibleIncentiveCount,
    String primaryIncentiveName,
    List<String> eligibleIncentiveNames,
    Instant createdAt,
    Instant updatedAt
) {
    public record ClaimerInfo(UUID userId, String name, Instant claimedAt) {}
}
