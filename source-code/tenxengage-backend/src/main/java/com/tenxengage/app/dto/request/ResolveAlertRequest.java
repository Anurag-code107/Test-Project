package com.tenxengage.app.dto.request;

import jakarta.validation.constraints.NotBlank;

public record ResolveAlertRequest(
    @NotBlank(message = "Resolution notes are required")
    String notes
) {}
