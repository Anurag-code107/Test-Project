package com.tenxengage.app.dto.response;

import com.tenxengage.app.entity.RedemptionRequest;
import com.tenxengage.app.entity.enums.RedemptionStatus;
import com.tenxengage.app.entity.enums.WalletType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record RedemptionRequestDetailResponse(
        UUID id,
        String status,
        BigDecimal amount,
        String currencyId,
        UUID catalogItemId,
        String catalogItemName,
        String imageUrl,
        String category,
        String processingMode,
        WalletType walletType,
        String vendorReferenceId,
        String failureReason,
        Instant submittedAt,
        Instant completedAt,
        LocalDate scheduledBatchDate,
        UUID reviewedBy,
        Instant reviewedAt,
        String rejectionReason
) {
    public static RedemptionRequestDetailResponse from(RedemptionRequest req, String catalogItemName, String imageUrl) {
        String vendorRef = req.getStatus() == RedemptionStatus.COMPLETED
                ? req.getVendorReferenceId()
                : null;
        return new RedemptionRequestDetailResponse(
                req.getId(),
                req.getStatus().name(),
                req.getAmount(),
                req.getCurrencyId(),
                req.getCatalogItemId(),
                catalogItemName,
                imageUrl,
                req.getCategory().name(),
                req.getProcessingMode().name(),
                req.getWalletType(),
                vendorRef,
                req.getFailureReason(),
                req.getSubmittedAt(),
                req.getCompletedAt(),
                req.getScheduledBatchDate(),
                req.getReviewedBy(),
                req.getReviewedAt(),
                req.getRejectionReason()
        );
    }
}
