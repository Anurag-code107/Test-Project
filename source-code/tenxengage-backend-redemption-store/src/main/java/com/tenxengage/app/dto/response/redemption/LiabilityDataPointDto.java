package com.tenxengage.app.dto.response.redemption;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One period-end liability data point for a single currency type (FR-08.5).
 *
 * <p>{@code currencyId} holds the platform currency identifier (e.g. "POINTS", "CASH").
 * The MV column is named {@code currency_type} and is populated from
 * {@code reward_wallets.currency_id}; the DTO field follows the contract name {@code currencyId}.
 */
public record LiabilityDataPointDto(
        LocalDate periodDate,
        String currencyId,
        BigDecimal totalUnredeemedBalance
) {}
