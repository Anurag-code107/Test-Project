package com.tenxengage.app.dto.response;

import com.tenxengage.app.entity.WhistleblowerCaseUpdate;
import com.tenxengage.app.entity.WhistleblowerReport;

import java.time.Instant;
import java.util.List;

/**
 * Limited-information response for public status checks by tracking number.
 * Only exposes non-sensitive status information that the reporter needs.
 * Reporter identity is returned ONLY if the reporter is NOT anonymous.
 */
public record WhistleblowerStatusResponse(
    String trackingNumber,
    String status,
    Instant acknowledgedAt,
    Instant resolutionDeadline,
    String latestUpdate,
    String reporterName,
    String reporterEmail
) {

    public static WhistleblowerStatusResponse from(WhistleblowerReport report,
                                                    List<WhistleblowerCaseUpdate> updates) {
        String latestUpdate = updates.isEmpty() ? null : updates.get(0).getUpdateText();

        // Only return reporter identity if the reporter is NOT anonymous
        String name = report.isAnonymous() ? null : report.getReporterName();
        String email = report.isAnonymous() ? null : report.getReporterEmail();

        return new WhistleblowerStatusResponse(
            report.getTrackingNumber(),
            report.getStatus().name(),
            report.getAcknowledgedAt(),
            report.getResolutionDeadline(),
            latestUpdate,
            name,
            email
        );
    }
}
