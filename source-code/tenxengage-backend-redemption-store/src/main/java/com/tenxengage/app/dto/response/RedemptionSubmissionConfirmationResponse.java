package com.tenxengage.app.dto.response;

import com.tenxengage.app.entity.RedemptionRequest;
import com.tenxengage.app.entity.enums.RedemptionProcessingMode;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record RedemptionSubmissionConfirmationResponse(
        UUID id,
        String status,
        String processingMode,
        String estimatedDelivery,
        LocalDate scheduledBatchDate,
        Instant submittedAt
) {
    public static RedemptionSubmissionConfirmationResponse from(RedemptionRequest request) {
        String estimated = request.getProcessingMode() == RedemptionProcessingMode.INSTANT
                ? "Available in minutes"
                : null;
        return new RedemptionSubmissionConfirmationResponse(
                request.getId(),
                request.getStatus().name(),
                request.getProcessingMode().name(),
                estimated,
                request.getScheduledBatchDate(),
                request.getSubmittedAt()
        );
    }
}
