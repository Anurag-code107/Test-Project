package com.tenxengage.app.dto.response.redemption;

import com.tenxengage.app.entity.redemption.RedemptionExportJob;

import java.time.Instant;
import java.util.UUID;

public record RedemptionExportJobDetailResponse(
        UUID id,
        String status,
        Integer rowCount,
        Instant expiresAt,
        String downloadUrl
) {
    public static RedemptionExportJobDetailResponse from(RedemptionExportJob job, String downloadUrl) {
        return new RedemptionExportJobDetailResponse(
                job.getId(),
                job.getStatus().name(),
                job.getRowCount(),
                job.getExpiresAt(),
                downloadUrl
        );
    }
}
