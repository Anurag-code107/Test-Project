package com.tenxengage.app.dto.request;

import jakarta.validation.constraints.NotBlank;

public record CreateProductRequest(
    @NotBlank(message = "Product name is required")
    String name,
    String category
) {}
