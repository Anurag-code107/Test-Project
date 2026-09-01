package com.tenxengage.app.service;

import com.tenxengage.app.entity.PartnerProgramAcknowledgment;
import com.tenxengage.app.exception.BusinessRuleException;
import com.tenxengage.app.repository.PartnerProgramAcknowledgmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PartnerAcknowledgmentServiceTest {

    @Mock
    private PartnerProgramAcknowledgmentRepository acknowledgmentRepository;

    @InjectMocks
    private PartnerAcknowledgmentService partnerAcknowledgmentService;

    private UUID partnerCompanyId;
    private UUID incentiveId;
    private UUID userId;
    private UUID clientId;

    @BeforeEach
    void setUp() {
        partnerCompanyId = UUID.randomUUID();
        incentiveId = UUID.randomUUID();
        userId = UUID.randomUUID();
        clientId = UUID.randomUUID();
    }

    @Test
    void acknowledgeProgram_createsRecord() {
        when(acknowledgmentRepository.existsByPartnerCompanyIdAndIncentiveId(partnerCompanyId, incentiveId))
                .thenReturn(false);

        PartnerProgramAcknowledgment savedAck = PartnerProgramAcknowledgment.builder()
                .id(UUID.randomUUID())
                .clientId(clientId)
                .partnerCompanyId(partnerCompanyId)
                .incentiveId(incentiveId)
                .acknowledgedBy(userId)
                .acknowledgedAt(Instant.now())
                .policyVersion("1.0")
                .build();
        when(acknowledgmentRepository.save(any(PartnerProgramAcknowledgment.class))).thenReturn(savedAck);

        PartnerProgramAcknowledgment result = partnerAcknowledgmentService
                .acknowledgeProgram(partnerCompanyId, incentiveId, userId, clientId);

        assertThat(result).isNotNull();
        assertThat(result.getPartnerCompanyId()).isEqualTo(partnerCompanyId);
        assertThat(result.getPolicyVersion()).isEqualTo("1.0");
        verify(acknowledgmentRepository).save(any(PartnerProgramAcknowledgment.class));
    }

    @Test
    void acknowledgeProgram_throwsOnDuplicate() {
        when(acknowledgmentRepository.existsByPartnerCompanyIdAndIncentiveId(partnerCompanyId, incentiveId))
                .thenReturn(true);

        assertThatThrownBy(() -> partnerAcknowledgmentService
                .acknowledgeProgram(partnerCompanyId, incentiveId, userId, clientId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("already acknowledged");
    }

    @Test
    void isAcknowledged_returnsTrue() {
        when(acknowledgmentRepository.existsByPartnerCompanyIdAndIncentiveId(partnerCompanyId, incentiveId))
                .thenReturn(true);

        boolean result = partnerAcknowledgmentService.isAcknowledged(partnerCompanyId, incentiveId);

        assertThat(result).isTrue();
    }

    @Test
    void isAcknowledged_returnsFalse() {
        when(acknowledgmentRepository.existsByPartnerCompanyIdAndIncentiveId(partnerCompanyId, incentiveId))
                .thenReturn(false);

        boolean result = partnerAcknowledgmentService.isAcknowledged(partnerCompanyId, incentiveId);

        assertThat(result).isFalse();
    }

    @Test
    void getAcknowledgmentsForIncentive_returnsList() {
        PartnerProgramAcknowledgment ack = PartnerProgramAcknowledgment.builder()
                .id(UUID.randomUUID())
                .clientId(clientId)
                .partnerCompanyId(partnerCompanyId)
                .incentiveId(incentiveId)
                .acknowledgedBy(userId)
                .build();
        when(acknowledgmentRepository.findByIncentiveId(incentiveId)).thenReturn(List.of(ack));

        List<PartnerProgramAcknowledgment> result = partnerAcknowledgmentService
                .getAcknowledgmentsForIncentive(incentiveId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getIncentiveId()).isEqualTo(incentiveId);
    }
}
