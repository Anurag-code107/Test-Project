package com.tenxengage.app.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

public record EligibleIncentiveResponse(
    UUID incentiveId,
    String incentiveName,
    RewardBreakdownResponse rewardBreakdown,
    BigDecimal totalReward
) {}
