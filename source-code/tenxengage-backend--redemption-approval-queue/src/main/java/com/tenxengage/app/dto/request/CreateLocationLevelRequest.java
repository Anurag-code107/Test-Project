package com.tenxengage.app.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateLocationLevelRequest(
    @NotBlank(message = "Level name is required")
    @Size(max = 100, message = "Level name must be at most 100 characters")
    String name
) {}
