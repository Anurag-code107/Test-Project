package com.tenxengage.app.dto.response;

import com.tenxengage.app.entity.IncentiveDocument;

import java.util.UUID;

public record DocumentSummaryResponse(
    UUID id,
    String name,
    String documentType,
    String fileType,
    String size,
    String downloadUrl
) {
    public static DocumentSummaryResponse from(IncentiveDocument doc) {
        String downloadUrl = doc.getStoragePath() != null
            ? "/api/v1/incentives/" + doc.getIncentive().getId() + "/documents/" + doc.getId() + "/download"
            : null;
        return new DocumentSummaryResponse(
            doc.getId(),
            doc.getName(),
            doc.getDocumentType(),
            doc.getFileType(),
            doc.getSize(),
            downloadUrl
        );
    }
}
