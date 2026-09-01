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

    @NotBlank(message = "Partner ID is required")
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

    String metadata,

    // --- Default company admin (D-16) --------------------------------------------------------------
    //
    // Identity only: enough to create this person's login. The address XTRM also needs is supplied by the
    // admin themselves, because they are the ones who know it — and because a mistyped admin email burns
    // that address at XTRM permanently, so the person who owns it should be the one to type it.
    //
    // All five or none; enforced as a group in PartnerCompanyService.validateAdminDetails, because bean
    // validation cannot express "all present or all absent" without a custom annotation.

    @Size(max = 100)
    String adminFirstName,

    @Size(max = 100)
    String adminLastName,

    @Email(message = "Admin email must be valid")
    @Size(max = 255)
    String adminEmail,

    @Size(max = 20)
    String adminMobileNumber,

    @Size(min = 2, max = 2, message = "Admin country must be a 2-letter ISO code")
    String adminCountryIso2
) {}
