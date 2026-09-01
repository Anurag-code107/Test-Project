package com.tenxengage.app.dto.request;

import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;

public record BeneficialOwnerRequest(
    @NotBlank(message = "Full name is required")
    String fullName,

    String nationality,

    BigDecimal ownershipPercentage,

    boolean isPep
) {}
