package com.tenxengage.app.dto.response;

import com.tenxengage.app.entity.ComplianceAlert;

import java.time.Instant;
import java.util.UUID;

public record ComplianceAlertResponse(
    UUID id,
    UUID clientId,
    String alertType,
    String severity,
    UUID userId,
    UUID partnerCompanyId,
    UUID incentiveId,
    String description,
    String status,
    Instant resolvedAt,
    UUID resolvedBy,
    String resolutionNotes,
    Instant createdAt,
    Instant updatedAt
) {

    public static ComplianceAlertResponse from(ComplianceAlert alert) {
        return new ComplianceAlertResponse(
            alert.getId(),
            alert.getClientId(),
            alert.getAlertType().name(),
            alert.getSeverity(),
            alert.getUserId(),
            alert.getPartnerCompanyId(),
            alert.getIncentiveId(),
            alert.getDescription(),
            alert.getStatus().name(),
            alert.getResolvedAt(),
            alert.getResolvedBy(),
            alert.getResolutionNotes(),
            alert.getCreatedAt(),
            alert.getUpdatedAt()
        );
    }
}
