package com.tenxengage.app.dto.request;

import com.tenxengage.app.entity.enums.PartnerCompanyStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record CreatePartnerCompanyRequest(
    @NotBlank(message = "Name is required")
    @Size(max = 255)
    String name,

    @Size(max = 100) String externalPartnerId,
    @NotEmpty(message = "At least one location is required") List<UUID> locationValueIds,
    @NotBlank @Size(max = 50) String partnerType,

    PartnerCompanyStatus status,

    @Size(max = 500)
    String website,

    @Email(message = "Contact email must be valid")
    @Size(max = 255)
    String contactEmail,

    @Size(max = 20)
    String contactPhone,

    String metadata
) {}
