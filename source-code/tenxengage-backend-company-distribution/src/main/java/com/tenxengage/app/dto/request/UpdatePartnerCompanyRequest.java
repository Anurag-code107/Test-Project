package com.tenxengage.app.dto.request;

import com.tenxengage.app.entity.enums.PartnerCompanyStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record UpdatePartnerCompanyRequest(
    @Size(max = 255)
    String name,

    @Size(max = 100) String externalPartnerId,
    List<UUID> locationValueIds,
    @Size(max = 50) String partnerType,

    PartnerCompanyStatus status,

    @Size(max = 500)
    String website,

    @Email(message = "Contact email must be valid")
    @Size(max = 255)
    String contactEmail,

    @Size(max = 20)
    String contactPhone,

    String metadata,

    // --- Default company admin -------------------------------------------------------------------------
    //
    // Patch semantics, matching every other field on this record: a null means "leave alone", so an update
    // that touches only the company name need not resend the admin block.

    @Size(max = 100)
    String adminFirstName,

    @Size(max = 100)
    String adminLastName,

    @Email(message = "Admin email must be valid")
    @Size(max = 255)
    String adminEmail,

    @Size(max = 20)
    String adminMobileNumber,

    @Size(max = 100)
    String adminCity,

    @Size(max = 100)
    String adminRegion,

    @Size(max = 20)
    String adminPostalCode,

    @Size(min = 2, max = 2, message = "Admin country must be a 2-letter ISO code")
    String adminCountryIso2
) {}
