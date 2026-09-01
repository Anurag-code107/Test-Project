package com.tenxengage.app.service;

import com.tenxengage.app.dto.request.BeneficialOwnerRequest;
import com.tenxengage.app.dto.request.CreateKycRequest;
import com.tenxengage.app.entity.KycRegionConfig;
import com.tenxengage.app.entity.PartnerBeneficialOwner;
import com.tenxengage.app.entity.PartnerKycRecord;
import com.tenxengage.app.entity.enums.KycStatus;
import com.tenxengage.app.exception.BusinessRuleException;
import com.tenxengage.app.repository.KycRegionConfigRepository;
import com.tenxengage.app.repository.PartnerBeneficialOwnerRepository;
import com.tenxengage.app.repository.PartnerKycRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

@Service
public class PartnerKycService {

    private static final Logger log = LoggerFactory.getLogger(PartnerKycService.class);

    private final PartnerKycRecordRepository kycRecordRepository;
    private final PartnerBeneficialOwnerRepository beneficialOwnerRepository;
    private final KycRegionConfigRepository kycRegionConfigRepository;

    public PartnerKycService(PartnerKycRecordRepository kycRecordRepository,
                             PartnerBeneficialOwnerRepository beneficialOwnerRepository,
                             KycRegionConfigRepository kycRegionConfigRepository) {
        this.kycRecordRepository = kycRecordRepository;
        this.beneficialOwnerRepository = beneficialOwnerRepository;
        this.kycRegionConfigRepository = kycRegionConfigRepository;
    }

    @Transactional
    public PartnerKycRecord initiateKyc(UUID partnerCompanyId, UUID clientId,
                                         CreateKycRequest request) {
        Optional<PartnerKycRecord> existing = kycRecordRepository.findByPartnerCompanyId(partnerCompanyId);

        PartnerKycRecord record;
        if (existing.isPresent()) {
            record = existing.get();
            if (record.getKycStatus() == KycStatus.APPROVED) {
                throw new BusinessRuleException(
                        "KYC record is already approved for partner company: " + partnerCompanyId);
            }
        } else {
            record = PartnerKycRecord.builder()
                    .clientId(clientId)
                    .partnerCompanyId(partnerCompanyId)
                    .build();
        }

        record.setLegalEntityName(request.legalEntityName());
        record.setRegistrationNumber(request.registrationNumber());
        record.setIncorporationCountry(request.incorporationCountry());
        record.setTaxId(request.taxId());
        record.setKycStatus(KycStatus.IN_PROGRESS);
        record.setRejectionReason(null);

        PartnerKycRecord saved = kycRecordRepository.save(record);

        // Replace beneficial owners
        if (request.beneficialOwners() != null && !request.beneficialOwners().isEmpty()) {
            beneficialOwnerRepository.deleteAll(
                    beneficialOwnerRepository.findByKycRecordId(saved.getId()));

            for (BeneficialOwnerRequest ownerReq : request.beneficialOwners()) {
                PartnerBeneficialOwner owner = PartnerBeneficialOwner.builder()
                        .kycRecordId(saved.getId())
                        .fullName(ownerReq.fullName())
                        .nationality(ownerReq.nationality())
                        .ownershipPercentage(ownerReq.ownershipPercentage())
                        .isPep(ownerReq.isPep())
                        .createdAt(Instant.now())
                        .build();
                beneficialOwnerRepository.save(owner);
            }
        }

        log.info("KYC initiated: partnerCompanyId={}, status={}", partnerCompanyId, saved.getKycStatus());
        return saved;
    }

    @Transactional
    public PartnerKycRecord approveKyc(UUID partnerCompanyId, UUID approvedByUserId) {
        PartnerKycRecord record = kycRecordRepository.findByPartnerCompanyId(partnerCompanyId)
                .orElseThrow(() -> new BusinessRuleException(
                        "No KYC record found for partner company: " + partnerCompanyId));

        if (record.getKycStatus() != KycStatus.IN_PROGRESS) {
            throw new BusinessRuleException(
                    "KYC record must be IN_PROGRESS to approve. Current status: " + record.getKycStatus());
        }

        record.setKycStatus(KycStatus.APPROVED);
        record.setApprovedBy(approvedByUserId);
        record.setApprovedAt(Instant.now());
        record.setExpiresAt(Instant.now().plus(365, ChronoUnit.DAYS));
        record.setRejectionReason(null);

        PartnerKycRecord saved = kycRecordRepository.save(record);
        log.info("KYC approved: partnerCompanyId={}, approvedBy={}, expiresAt={}",
                partnerCompanyId, approvedByUserId, saved.getExpiresAt());
        return saved;
    }

    @Transactional
    public PartnerKycRecord rejectKyc(UUID partnerCompanyId, String reason) {
        PartnerKycRecord record = kycRecordRepository.findByPartnerCompanyId(partnerCompanyId)
                .orElseThrow(() -> new BusinessRuleException(
                        "No KYC record found for partner company: " + partnerCompanyId));

        if (record.getKycStatus() != KycStatus.IN_PROGRESS) {
            throw new BusinessRuleException(
                    "KYC record must be IN_PROGRESS to reject. Current status: " + record.getKycStatus());
        }

        record.setKycStatus(KycStatus.REJECTED);
        record.setRejectionReason(reason);

        PartnerKycRecord saved = kycRecordRepository.save(record);
        log.info("KYC rejected: partnerCompanyId={}, reason={}", partnerCompanyId, reason);
        return saved;
    }

    @Transactional(readOnly = true)
    public Optional<PartnerKycRecord> getKycRecord(UUID partnerCompanyId) {
        return kycRecordRepository.findByPartnerCompanyId(partnerCompanyId);
    }

    @Transactional(readOnly = true)
    public boolean isKycRequired(String region) {
        return kycRegionConfigRepository.findByRegionCode(region)
                .map(KycRegionConfig::isTier1Required)
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public boolean isKycApproved(UUID partnerCompanyId) {
        return kycRecordRepository.findByPartnerCompanyId(partnerCompanyId)
                .map(record -> record.getKycStatus() == KycStatus.APPROVED
                        && (record.getExpiresAt() == null || record.getExpiresAt().isAfter(Instant.now())))
                .orElse(false);
    }
}
