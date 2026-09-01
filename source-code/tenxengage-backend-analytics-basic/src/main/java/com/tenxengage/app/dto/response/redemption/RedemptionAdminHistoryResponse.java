package com.tenxengage.app.dto.response.redemption;

import com.tenxengage.app.entity.RedemptionRequest;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record RedemptionAdminHistoryResponse(
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
        UUID userId,
        String userDisplayName,
        UUID partnerCompanyId,
        String partnerCompanyName
) {
    public static RedemptionAdminHistoryResponse from(
            RedemptionRequest req,
            String catalogItemName,
            String userDisplayName,
            String partnerCompanyName) {
        return new RedemptionAdminHistoryResponse(
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
                req.getUserId(),
                userDisplayName,
                req.getUser() != null ? req.getUser().getPartnerCompanyId() : null,
                partnerCompanyName
        );
    }
}
