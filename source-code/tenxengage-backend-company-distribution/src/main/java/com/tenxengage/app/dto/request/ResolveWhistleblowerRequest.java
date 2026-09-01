package com.tenxengage.app.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResolveWhistleblowerRequest(
    @NotBlank(message = "Resolution notes are required")
    @Size(max = 5000, message = "Resolution notes must not exceed 5000 characters")
    String notes
) {}
