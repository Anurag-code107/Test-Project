package com.tenxengage.app.service;

import com.tenxengage.app.entity.ComplianceAlert;
import com.tenxengage.app.entity.enums.ComplianceAlertStatus;
import com.tenxengage.app.entity.enums.ComplianceAlertType;
import com.tenxengage.app.exception.BusinessRuleException;
import com.tenxengage.app.repository.ComplianceAlertRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ComplianceAlertServiceTest {

    @Mock
    private ComplianceAlertRepository complianceAlertRepository;

    @InjectMocks
    private ComplianceAlertService complianceAlertService;

    private UUID clientId;
    private UUID alertId;
    private UUID resolverUserId;

    @BeforeEach
    void setUp() {
        clientId = UUID.randomUUID();
        alertId = UUID.randomUUID();
        resolverUserId = UUID.randomUUID();
    }

    @Test
    void createAlert_savesNewAlert() {
        ComplianceAlert savedAlert = ComplianceAlert.builder()
                .clientId(clientId)
                .alertType(ComplianceAlertType.CONCENTRATION_ALERT)
                .severity("HIGH")
                .description("User received >50% of rewards")
                .status(ComplianceAlertStatus.NEW)
                .build();
        savedAlert.setId(alertId);

        when(complianceAlertRepository.save(any(ComplianceAlert.class))).thenReturn(savedAlert);

        ComplianceAlert result = complianceAlertService.createAlert(
                clientId, ComplianceAlertType.CONCENTRATION_ALERT, "HIGH",
                "User received >50% of rewards", null, null, null);

        assertThat(result).isNotNull();
        assertThat(result.getAlertType()).isEqualTo(ComplianceAlertType.CONCENTRATION_ALERT);
        assertThat(result.getSeverity()).isEqualTo("HIGH");
        assertThat(result.getStatus()).isEqualTo(ComplianceAlertStatus.NEW);
        verify(complianceAlertRepository).save(any(ComplianceAlert.class));
    }

    @Test
    void resolveAlert_setsResolutionFields() {
        ComplianceAlert alert = ComplianceAlert.builder()
                .clientId(clientId)
                .alertType(ComplianceAlertType.CONCENTRATION_ALERT)
                .severity("HIGH")
                .description("Test alert")
                .status(ComplianceAlertStatus.NEW)
                .build();
        alert.setId(alertId);

        when(complianceAlertRepository.findById(alertId)).thenReturn(Optional.of(alert));
        when(complianceAlertRepository.save(any(ComplianceAlert.class))).thenAnswer(inv -> inv.getArgument(0));

        ComplianceAlert result = complianceAlertService.resolveAlert(alertId, resolverUserId, "Reviewed and OK");

        assertThat(result.getStatus()).isEqualTo(ComplianceAlertStatus.RESOLVED);
        assertThat(result.getResolvedBy()).isEqualTo(resolverUserId);
        assertThat(result.getResolvedAt()).isNotNull();
        assertThat(result.getResolutionNotes()).isEqualTo("Reviewed and OK");
    }

    @Test
    void resolveAlert_throwsForAlreadyResolved() {
        ComplianceAlert alert = ComplianceAlert.builder()
                .clientId(clientId)
                .alertType(ComplianceAlertType.CONCENTRATION_ALERT)
                .severity("HIGH")
                .description("Test alert")
                .status(ComplianceAlertStatus.RESOLVED)
                .build();
        alert.setId(alertId);

        when(complianceAlertRepository.findById(alertId)).thenReturn(Optional.of(alert));

        assertThatThrownBy(() -> complianceAlertService.resolveAlert(alertId, resolverUserId, "notes"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("already resolved or dismissed");
    }

    @Test
    void getActiveAlerts_returnsList() {
        ComplianceAlert alert = ComplianceAlert.builder()
                .clientId(clientId)
                .alertType(ComplianceAlertType.VALUE_CAP_EXCEEDED)
                .severity("MEDIUM")
                .description("Cap exceeded")
                .status(ComplianceAlertStatus.NEW)
                .build();
        alert.setId(UUID.randomUUID());

        when(complianceAlertRepository.findByClientIdAndStatus(clientId, ComplianceAlertStatus.NEW))
                .thenReturn(List.of(alert));

        List<ComplianceAlert> result = complianceAlertService.getActiveAlerts(clientId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getAlertType()).isEqualTo(ComplianceAlertType.VALUE_CAP_EXCEEDED);
    }
}
