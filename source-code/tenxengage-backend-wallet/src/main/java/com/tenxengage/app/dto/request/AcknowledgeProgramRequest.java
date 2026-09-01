package com.tenxengage.app.dto.request;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AcknowledgeProgramRequest(
    @NotNull(message = "Partner company ID is required")
    UUID partnerCompanyId,

    @NotNull(message = "Incentive ID is required")
    UUID incentiveId
) {}
