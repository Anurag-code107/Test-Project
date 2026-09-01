package com.tenxengage.app.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record UpdateComplianceValueCapRequest(
    @NotNull(message = "Annual cap amount is required")
    @DecimalMin(value = "0.01", message = "Annual cap must be greater than zero")
    BigDecimal annualCapAmount,

    @NotNull(message = "Enhanced approval threshold is required")
    @DecimalMin(value = "0.01", message = "Enhanced approval threshold must be greater than zero")
    BigDecimal enhancedApprovalThreshold
) {}
