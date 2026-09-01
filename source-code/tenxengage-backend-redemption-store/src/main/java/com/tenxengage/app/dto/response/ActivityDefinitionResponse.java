package com.tenxengage.app.dto.response;

import com.tenxengage.app.entity.ActivityDefinition;

import java.util.List;
import java.util.UUID;

public record ActivityDefinitionResponse(
    UUID id,
    String name,
    String description,
    String categoryId,
    int sortOrder,
    List<ActivityDocumentResponse> requiredDocuments
) {

    public static ActivityDefinitionResponse from(ActivityDefinition def) {
        return new ActivityDefinitionResponse(
            def.getId(),
            def.getName(),
            def.getDescription(),
            def.getCategoryId(),
            def.getSortOrder(),
            def.getRequiredDocuments().stream()
                .map(ActivityDocumentResponse::from)
                .toList()
        );
    }
}
