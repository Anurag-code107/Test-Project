package com.tenxengage.app.dto.response;

import com.tenxengage.app.entity.TaggingJob;
import com.tenxengage.app.entity.enums.TaggingJobStatus;

import java.time.Instant;
import java.util.UUID;

public record TaggingJobResponse(
        UUID id,
        TaggingJobStatus status,
        int posAnalyzed,
        int eligibleDeals,
        int incentivesMatched,
        int productsDiscovered,
        String errorMessage,
        Instant createdAt
) {
    public static TaggingJobResponse from(TaggingJob job) {
        return new TaggingJobResponse(
                job.getId(),
                job.getStatus(),
                job.getPosAnalyzed(),
                job.getEligibleDeals(),
                job.getIncentivesMatched(),
                job.getProductsDiscovered(),
                job.getErrorMessage(),
                job.getCreatedAt()
        );
    }
}
