package com.tenxengage.app.dto.response;

import com.tenxengage.app.entity.WhistleblowerReport;

import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO for whistleblower reports shown to TENX_ADMIN.
 * NEVER exposes reporter identity unless the reporter themselves looks up
 * by tracking number AND they chose not to be anonymous.
 */
public record WhistleblowerReportResponse(
    UUID id,
    UUID clientId,
    String reportType,
    String description,
    String evidenceUrl,
    String trackingNumber,
    String status,
    boolean anonymous,
    Instant acknowledgedAt,
    Instant resolutionDeadline,
    Instant resolvedAt,
    UUID resolvedBy,
    String resolutionNotes,
    Instant createdAt,
    Instant updatedAt
) {

    /**
     * Standard admin view: strips reporter identity.
     */
    public static WhistleblowerReportResponse from(WhistleblowerReport report) {
        return new WhistleblowerReportResponse(
            report.getId(),
            report.getClientId(),
            report.getReportType().name(),
            report.getDescription(),
            report.getEvidenceUrl(),
            report.getTrackingNumber(),
            report.getStatus().name(),
            report.isAnonymous(),
            report.getAcknowledgedAt(),
            report.getResolutionDeadline(),
            report.getResolvedAt(),
            report.getResolvedBy(),
            report.getResolutionNotes(),
            report.getCreatedAt(),
            report.getUpdatedAt()
        );
    }
}
