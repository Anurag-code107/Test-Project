package com.tenxengage.app.dto.response.redemption;

import com.tenxengage.app.entity.redemption.RedemptionExportJob;

import java.time.Instant;
import java.util.UUID;

public record RedemptionExportJobResponse(
        UUID id,
        String status,
        Integer rowCount,
        Instant expiresAt
) {
    public static RedemptionExportJobResponse from(RedemptionExportJob job) {
        return new RedemptionExportJobResponse(
                job.getId(),
                job.getStatus().name(),
                job.getRowCount(),
                job.getExpiresAt()
        );
    }
}
