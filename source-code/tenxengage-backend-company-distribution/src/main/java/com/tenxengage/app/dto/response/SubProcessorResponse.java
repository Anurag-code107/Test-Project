package com.tenxengage.app.dto.response;

import com.tenxengage.app.entity.SubProcessor;

import java.time.Instant;
import java.util.UUID;

public record SubProcessorResponse(
    UUID id,
    String name,
    String purpose,
    String dataProcessed,
    String location,
    String dpaStatus,
    String sccStatus,
    Instant addedAt,
    Instant updatedAt
) {

    public static SubProcessorResponse from(SubProcessor processor) {
        return new SubProcessorResponse(
            processor.getId(),
            processor.getName(),
            processor.getPurpose(),
            processor.getDataProcessed(),
            processor.getLocation(),
            processor.getDpaStatus().name(),
            processor.getSccStatus().name(),
            processor.getAddedAt(),
            processor.getUpdatedAt()
        );
    }
}
