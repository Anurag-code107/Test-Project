package com.tenxengage.app.dto.request.redemption;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RejectRedemptionRequest(
        @NotBlank @Size(max = 1000) String rejectionReason
) {}
