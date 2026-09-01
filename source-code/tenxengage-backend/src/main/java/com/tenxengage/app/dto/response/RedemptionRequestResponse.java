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
        String catalogItemName,
        Instant submittedAt,
        Instant completedAt,
        LocalDate scheduledBatchDate,
        String estimatedDelivery,
        boolean isReturnEligible
) {
    /**
     * Legacy factory — always sets isReturnEligible=false.
     * Use only in contexts where return eligibility is irrelevant (e.g. company/admin history endpoints).
     * Do NOT use in personal-history or any endpoint where eligibility must be computed.
     */
    public static RedemptionRequestResponse from(RedemptionRequest req, String catalogItemName) {
        return from(req, catalogItemName, false);
    }

    /**
     * Full factory — use in endpoints that compute isReturnEligible (F-05 personal history).
     */
    public static RedemptionRequestResponse from(
            RedemptionRequest req,
            String catalogItemName,
            boolean isReturnEligible) {
        return new RedemptionRequestResponse(
                req.getId(),
                req.getStatus().name(),
                req.getAmount(),
                req.getCurrencyId(),
                req.getCategory().name(),
                req.getProcessingMode().name(),
                catalogItemName,
                req.getSubmittedAt(),
                req.getCompletedAt(),
                req.getScheduledBatchDate(),
                null, // TODO(F-06): populate estimatedDelivery from catalog item
                isReturnEligible
        );
    }
}
