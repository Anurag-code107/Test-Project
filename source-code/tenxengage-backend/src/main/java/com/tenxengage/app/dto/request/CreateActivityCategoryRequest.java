package com.tenxengage.app.dto.request;

import jakarta.validation.constraints.NotBlank;

public record CreateActivityCategoryRequest(
    @NotBlank String name,
    String description
) {}
