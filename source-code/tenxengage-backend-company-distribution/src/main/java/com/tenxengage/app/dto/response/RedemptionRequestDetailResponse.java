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
        String providerImageUrl,
        String category,
        String processingMode,
        WalletType walletType,
        String vendorReferenceId,
        String failureReason,
        Instant submittedAt,
        Instant completedAt,
        LocalDate scheduledBatchDate,
        UUID reviewedBy,
        String reviewedByName,
        Instant reviewedAt,
        String rejectionReason,
        UUID linkedReturnId
) {
    public static RedemptionRequestDetailResponse from(RedemptionRequest req, String catalogItemName, String imageUrl,
                                                       String providerImageUrl, String reviewedByName,
                                                       UUID linkedReturnId) {
        String vendorRef = req.getStatus() == RedemptionStatus.COMPLETED
                ? req.getVendorReferenceId()
                : null;
        String failure = req.getStatus() == RedemptionStatus.FAILED
                ? req.getFailureReason()
                : null;
        return new RedemptionRequestDetailResponse(
                req.getId(),
                req.getStatus().name(),
                req.getAmount(),
                req.getCurrencyId(),
                req.getCatalogItemId(),
                catalogItemName,
                imageUrl,
                providerImageUrl,
                req.getCategory().name(),
                req.getProcessingMode().name(),
                req.getWalletType(),
                vendorRef,
                failure,
                req.getSubmittedAt(),
                req.getCompletedAt(),
                req.getScheduledBatchDate(),
                req.getReviewedBy(),
                reviewedByName,
                req.getReviewedAt(),
                req.getRejectionReason(),
                linkedReturnId
        );
    }
}
