package com.tenxengage.app.dto.response;

import com.tenxengage.app.entity.RedemptionRequest;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record RedemptionRequestResponse(
        UUID id,
        String status,
        BigDecimal amount,
        String currencyId,
        String category,
        String processingMode,
        Instant submittedAt,
        LocalDate scheduledBatchDate,
        String estimatedDelivery
) {
    public static RedemptionRequestResponse from(RedemptionRequest req) {
        return new RedemptionRequestResponse(
                req.getId(),
                req.getStatus().name(),
                req.getAmount(),
                req.getCurrencyId(),
                req.getCategory().name(),
                req.getProcessingMode().name(),
                req.getSubmittedAt(),
                req.getScheduledBatchDate(),
                null
        );
    }
}
