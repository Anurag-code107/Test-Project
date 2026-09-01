package com.tenxengage.app.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateDataObjectRequest(
    @NotBlank @Size(max = 255) String name,
    @Size(max = 1000) String description
) {}
