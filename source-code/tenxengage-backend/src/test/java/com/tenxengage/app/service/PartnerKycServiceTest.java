package com.tenxengage.app.service;

import com.tenxengage.app.dto.request.CreateKycRequest;
import com.tenxengage.app.entity.KycRegionConfig;
import com.tenxengage.app.entity.PartnerKycRecord;
import com.tenxengage.app.entity.enums.KycStatus;
import com.tenxengage.app.repository.KycRegionConfigRepository;
import com.tenxengage.app.repository.PartnerBeneficialOwnerRepository;
import com.tenxengage.app.repository.PartnerKycRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PartnerKycServiceTest {

    @Mock
    private PartnerKycRecordRepository kycRecordRepository;

    @Mock
    private PartnerBeneficialOwnerRepository beneficialOwnerRepository;

    @Mock
    private KycRegionConfigRepository kycRegionConfigRepository;

    @InjectMocks
    private PartnerKycService partnerKycService;

    private UUID partnerCompanyId;
    private UUID clientId;
    private UUID approverUserId;

    @BeforeEach
    void setUp() {
        partnerCompanyId = UUID.randomUUID();
        clientId = UUID.randomUUID();
        approverUserId = UUID.randomUUID();
    }

    @Test
    void initiateKyc_createsRecord() {
        CreateKycRequest request = new CreateKycRequest(
                "Acme Corp", "REG-123", "DE", "TAX-456", List.of());

        when(kycRecordRepository.findByPartnerCompanyId(partnerCompanyId)).thenReturn(Optional.empty());

        PartnerKycRecord savedRecord = PartnerKycRecord.builder()
                .clientId(clientId)
                .partnerCompanyId(partnerCompanyId)
                .legalEntityName("Acme Corp")
                .registrationNumber("REG-123")
                .kycStatus(KycStatus.IN_PROGRESS)
                .build();
        savedRecord.setId(UUID.randomUUID());
        when(kycRecordRepository.save(any(PartnerKycRecord.class))).thenReturn(savedRecord);

        PartnerKycRecord result = partnerKycService.initiateKyc(partnerCompanyId, clientId, request);

        assertThat(result).isNotNull();
        assertThat(result.getKycStatus()).isEqualTo(KycStatus.IN_PROGRESS);
        assertThat(result.getLegalEntityName()).isEqualTo("Acme Corp");
        verify(kycRecordRepository).save(any(PartnerKycRecord.class));
    }

    @Test
    void approveKyc_setsStatusAndExpiry() {
        PartnerKycRecord record = PartnerKycRecord.builder()
                .clientId(clientId)
                .partnerCompanyId(partnerCompanyId)
                .kycStatus(KycStatus.IN_PROGRESS)
                .build();
        record.setId(UUID.randomUUID());

        when(kycRecordRepository.findByPartnerCompanyId(partnerCompanyId)).thenReturn(Optional.of(record));
        when(kycRecordRepository.save(any(PartnerKycRecord.class))).thenAnswer(inv -> inv.getArgument(0));

        PartnerKycRecord result = partnerKycService.approveKyc(partnerCompanyId, approverUserId);

        assertThat(result.getKycStatus()).isEqualTo(KycStatus.APPROVED);
        assertThat(result.getApprovedBy()).isEqualTo(approverUserId);
        assertThat(result.getApprovedAt()).isNotNull();
        assertThat(result.getExpiresAt()).isAfter(Instant.now().plus(364, ChronoUnit.DAYS));
    }

    @Test
    void rejectKyc_setsStatusAndReason() {
        PartnerKycRecord record = PartnerKycRecord.builder()
                .clientId(clientId)
                .partnerCompanyId(partnerCompanyId)
                .kycStatus(KycStatus.IN_PROGRESS)
                .build();
        record.setId(UUID.randomUUID());

        when(kycRecordRepository.findByPartnerCompanyId(partnerCompanyId)).thenReturn(Optional.of(record));
        when(kycRecordRepository.save(any(PartnerKycRecord.class))).thenAnswer(inv -> inv.getArgument(0));

        PartnerKycRecord result = partnerKycService.rejectKyc(partnerCompanyId, "Incomplete documentation");

        assertThat(result.getKycStatus()).isEqualTo(KycStatus.REJECTED);
        assertThat(result.getRejectionReason()).isEqualTo("Incomplete documentation");
    }

    @Test
    void isKycRequired_returnsTrueForEuRegion() {
        KycRegionConfig config = KycRegionConfig.builder()
                .regionCode("EU")
                .tier1Required(true)
                .build();
        when(kycRegionConfigRepository.findByRegionCode("EU")).thenReturn(Optional.of(config));

        boolean result = partnerKycService.isKycRequired("EU");

        assertThat(result).isTrue();
    }

    @Test
    void isKycRequired_returnsFalseForUsRegion() {
        KycRegionConfig config = KycRegionConfig.builder()
                .regionCode("US")
                .tier1Required(false)
                .build();
        when(kycRegionConfigRepository.findByRegionCode("US")).thenReturn(Optional.of(config));

        boolean result = partnerKycService.isKycRequired("US");

        assertThat(result).isFalse();
    }

    @Test
    void isKycApproved_returnsTrueWhenApproved() {
        PartnerKycRecord record = PartnerKycRecord.builder()
                .clientId(clientId)
                .partnerCompanyId(partnerCompanyId)
                .kycStatus(KycStatus.APPROVED)
                .expiresAt(Instant.now().plus(365, ChronoUnit.DAYS))
                .build();
        when(kycRecordRepository.findByPartnerCompanyId(partnerCompanyId)).thenReturn(Optional.of(record));

        boolean result = partnerKycService.isKycApproved(partnerCompanyId);

        assertThat(result).isTrue();
    }
}
