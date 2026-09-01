package com.tenxengage.app.dto.response;

import com.tenxengage.app.entity.RedemptionRequest;
import com.tenxengage.app.entity.enums.RedemptionStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record RedemptionRequestDetailResponse(
        UUID id,
        String status,
        BigDecimal amount,
        String currencyId,
        String catalogItemName,
        String category,
        String processingMode,
        String vendorReferenceId,
        String failureReason,
        Instant submittedAt,
        Instant completedAt,
        LocalDate scheduledBatchDate
) {
    public static RedemptionRequestDetailResponse from(RedemptionRequest req, String catalogItemName) {
        String vendorRef = req.getStatus() == RedemptionStatus.COMPLETED
                ? req.getVendorReferenceId()
                : null;
        return new RedemptionRequestDetailResponse(
                req.getId(),
                req.getStatus().name(),
                req.getAmount(),
                req.getCurrencyId(),
                catalogItemName,
                req.getCategory().name(),
                req.getProcessingMode().name(),
                vendorRef,
                req.getFailureReason(),
                req.getSubmittedAt(),
                req.getCompletedAt(),
                req.getScheduledBatchDate()
        );
    }
}
