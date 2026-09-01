package com.tenxengage.app.dto.response;

import com.tenxengage.app.entity.ActivityCategory;

import java.util.UUID;

public record ActivityCategoryResponse(
    UUID id,
    String name,
    String description,
    int sortOrder
) {
    public static ActivityCategoryResponse from(ActivityCategory category) {
        return new ActivityCategoryResponse(
            category.getId(),
            category.getName(),
            category.getDescription(),
            category.getSortOrder()
        );
    }
}
