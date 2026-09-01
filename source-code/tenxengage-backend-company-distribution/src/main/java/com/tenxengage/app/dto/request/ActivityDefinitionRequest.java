package com.tenxengage.app.dto.request;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record ActivityDefinitionRequest(
    @NotBlank String name,
    String description,
    @NotBlank String categoryId,
    int sortOrder,
    List<ActivityDocumentRequest> requiredDocuments
) {
}
