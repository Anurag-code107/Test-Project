package com.tenxengage.app.dto.response;

import com.tenxengage.app.entity.BreachIncident;

import java.time.Instant;
import java.util.UUID;

public record BreachIncidentResponse(
    UUID id,
    String description,
    String severity,
    String dataAffected,
    Instant detectedAt,
    Instant reportedToAuthorityAt,
    Instant individualsNotifiedAt,
    String status,
    String resolutionNotes,
    UUID createdBy,
    Instant createdAt
) {

    public static BreachIncidentResponse from(BreachIncident incident) {
        return new BreachIncidentResponse(
            incident.getId(),
            incident.getDescription(),
            incident.getSeverity().name(),
            incident.getDataAffected(),
            incident.getDetectedAt(),
            incident.getReportedToAuthorityAt(),
            incident.getIndividualsNotifiedAt(),
            incident.getStatus().name(),
            incident.getResolutionNotes(),
            incident.getCreatedBy(),
            incident.getCreatedAt()
        );
    }
}
