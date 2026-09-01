package com.tenxengage.app.dto.request;

import jakarta.validation.constraints.NotBlank;

public record RejectKycRequest(
    @NotBlank(message = "Rejection reason is required")
    String reason
) {}
