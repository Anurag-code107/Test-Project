package com.tenxengage.app.dto.request.redemption;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record SubmitReturnRequest(
        @NotNull UUID redemptionId,
        @Size(max = 500) String reason
) {}
