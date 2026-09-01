package com.tenxengage.app.dto.response;

import com.tenxengage.app.entity.ActivityDocumentRequirement;

import java.util.UUID;

public record ActivityDocumentResponse(
    UUID id,
    String name,
    String description,
    boolean required
) {

    public static ActivityDocumentResponse from(ActivityDocumentRequirement doc) {
        return new ActivityDocumentResponse(
            doc.getId(),
            doc.getName(),
            doc.getDescription(),
            Boolean.TRUE.equals(doc.getRequired())
        );
    }
}
