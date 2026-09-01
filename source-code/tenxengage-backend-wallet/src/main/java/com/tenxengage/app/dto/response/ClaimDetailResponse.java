package com.tenxengage.app.dto.response;

import com.tenxengage.app.entity.enums.ClaimStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record ClaimDetailResponse(
    UUID id,
    String orderNumber,
    LocalDate orderDate,
    ClaimStatus status,
    String partnerCompanyName,
    UUID partnerCompanyId,
    String region,
    String customerName,
    BigDecimal totalAmount,
    BigDecimal totalMonetaryReward,
    RewardBreakdownResponse rewardBreakdown,
    List<ClaimResponse.ClaimerInfo> claimers,
    int maxClaimersPerDeal,
    List<EligibleIncentiveResponse> eligibleIncentives,
    List<IneligibleIncentiveResponse> ineligibleIncentives,
    String adminComment,
    Instant createdAt,
    Instant updatedAt
) {}
