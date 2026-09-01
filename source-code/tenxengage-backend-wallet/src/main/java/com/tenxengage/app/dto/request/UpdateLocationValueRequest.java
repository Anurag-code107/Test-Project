package com.tenxengage.app.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateLocationValueRequest(
    @NotBlank(message = "Value name is required")
    @Size(max = 255, message = "Value name must be at most 255 characters")
    String name,

    @Size(max = 50, message = "Code must be at most 50 characters")
    String code
) {}
