package com.tenxengage.app.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record CreateKycRequest(
    @NotBlank(message = "Legal entity name is required")
    String legalEntityName,

    @NotBlank(message = "Registration number is required")
    String registrationNumber,

    @NotBlank(message = "Incorporation country is required")
    String incorporationCountry,

    String taxId,

    @Valid
    List<BeneficialOwnerRequest> beneficialOwners
) {}
