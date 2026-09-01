package com.tenxengage.app.dto.response;

import com.tenxengage.app.entity.PartnerBeneficialOwner;
import com.tenxengage.app.entity.PartnerKycRecord;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PartnerKycResponse(
    UUID id,
    UUID clientId,
    UUID partnerCompanyId,
    String legalEntityName,
    String registrationNumber,
    String incorporationCountry,
    String taxId,
    String kycStatus,
    UUID approvedBy,
    Instant approvedAt,
    Instant expiresAt,
    String rejectionReason,
    List<BeneficialOwnerResponse> beneficialOwners,
    Instant createdAt,
    Instant updatedAt
) {

    public static PartnerKycResponse from(PartnerKycRecord record,
                                           List<PartnerBeneficialOwner> owners) {
        List<BeneficialOwnerResponse> ownerResponses = owners.stream()
                .map(BeneficialOwnerResponse::from)
                .toList();

        return new PartnerKycResponse(
            record.getId(),
            record.getClientId(),
            record.getPartnerCompanyId(),
            record.getLegalEntityName(),
            record.getRegistrationNumber(),
            record.getIncorporationCountry(),
            record.getTaxId(),
            record.getKycStatus().name(),
            record.getApprovedBy(),
            record.getApprovedAt(),
            record.getExpiresAt(),
            record.getRejectionReason(),
            ownerResponses,
            record.getCreatedAt(),
            record.getUpdatedAt()
        );
    }

    public record BeneficialOwnerResponse(
        UUID id,
        String fullName,
        String nationality,
        java.math.BigDecimal ownershipPercentage,
        boolean isPep,
        Instant createdAt
    ) {

        public static BeneficialOwnerResponse from(PartnerBeneficialOwner owner) {
            return new BeneficialOwnerResponse(
                owner.getId(),
                owner.getFullName(),
                owner.getNationality(),
                owner.getOwnershipPercentage(),
                owner.isPep(),
                owner.getCreatedAt()
            );
        }
    }
}
