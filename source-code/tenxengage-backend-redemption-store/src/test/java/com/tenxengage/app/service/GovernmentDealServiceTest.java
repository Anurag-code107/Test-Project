package com.tenxengage.app.service;

import com.tenxengage.app.entity.GovernmentSegmentConfig;
import com.tenxengage.app.entity.PartnerCompany;
import com.tenxengage.app.entity.enums.GovernmentDealRestrictionMode;
import com.tenxengage.app.repository.GovernmentSegmentConfigRepository;
import com.tenxengage.app.repository.PartnerCompanyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GovernmentDealServiceTest {

    @Mock
    private GovernmentSegmentConfigRepository governmentSegmentConfigRepository;

    @Mock
    private PartnerCompanyRepository partnerCompanyRepository;

    @InjectMocks
    private GovernmentDealService governmentDealService;

    private UUID clientId;
    private UUID partnerCompanyId;

    @BeforeEach
    void setUp() {
        clientId = UUID.randomUUID();
        partnerCompanyId = UUID.randomUUID();
    }

    @Test
    void isGovernmentDeal_returnsTrueForGovernmentSegment() {
        GovernmentSegmentConfig config = GovernmentSegmentConfig.builder()
                .clientId(clientId)
                .segmentValue("Federal")
                .isGovernment(true)
                .build();
        when(governmentSegmentConfigRepository.findByClientIdAndSegmentValue(clientId, "Federal"))
                .thenReturn(Optional.of(config));

        boolean result = governmentDealService.isGovernmentDeal(clientId, "Federal");

        assertThat(result).isTrue();
    }

    @Test
    void isGovernmentDeal_returnsFalseForPrivateSegment() {
        GovernmentSegmentConfig config = GovernmentSegmentConfig.builder()
                .clientId(clientId)
                .segmentValue("Enterprise")
                .isGovernment(false)
                .build();
        when(governmentSegmentConfigRepository.findByClientIdAndSegmentValue(clientId, "Enterprise"))
                .thenReturn(Optional.of(config));

        boolean result = governmentDealService.isGovernmentDeal(clientId, "Enterprise");

        assertThat(result).isFalse();
    }

    @Test
    void isGovernmentDeal_returnsFalseWhenNotConfigured() {
        when(governmentSegmentConfigRepository.findByClientIdAndSegmentValue(clientId, "Unknown"))
                .thenReturn(Optional.empty());

        boolean result = governmentDealService.isGovernmentDeal(clientId, "Unknown");

        assertThat(result).isFalse();
    }

    @Test
    void getRestrictionMode_returnsPartnerMode() {
        PartnerCompany partner = new PartnerCompany();
        partner.setId(partnerCompanyId);
        partner.setName("Acme Corp");
        partner.setGovernmentDealRestrictionMode(GovernmentDealRestrictionMode.STRICT);

        when(partnerCompanyRepository.findById(partnerCompanyId)).thenReturn(Optional.of(partner));

        GovernmentDealRestrictionMode result = governmentDealService.getRestrictionMode(partnerCompanyId);

        assertThat(result).isEqualTo(GovernmentDealRestrictionMode.STRICT);
    }
}
