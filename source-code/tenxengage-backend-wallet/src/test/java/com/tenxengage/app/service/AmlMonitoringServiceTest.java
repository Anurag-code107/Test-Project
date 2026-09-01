package com.tenxengage.app.service;

import com.tenxengage.app.entity.ComplianceAlert;
import com.tenxengage.app.entity.enums.ComplianceAlertStatus;
import com.tenxengage.app.entity.enums.ComplianceAlertType;
import com.tenxengage.app.repository.ClientRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AmlMonitoringServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private ComplianceAlertService complianceAlertService;

    @Mock
    private ClientRepository clientRepository;

    @InjectMocks
    private AmlMonitoringService amlMonitoringService;

    private UUID clientId;

    @BeforeEach
    void setUp() {
        clientId = UUID.randomUUID();
    }

    @Test
    void detectAnomalies_createsAlertForConcentration() {
        UUID userId = UUID.randomUUID();
        UUID incentiveId = UUID.randomUUID();

        // User has 80% of incentive rewards (above 50% threshold)
        Map<String, Object> userRow = new HashMap<>();
        userRow.put("incentive_id", incentiveId);
        userRow.put("user_id", userId);
        userRow.put("user_total", new BigDecimal("8000.00"));
        userRow.put("incentive_total", new BigDecimal("10000.00"));

        // First call is user concentration query, second is partner concentration query,
        // third and fourth are proportionality queries
        when(jdbcTemplate.queryForList(anyString(), eq(clientId)))
                .thenReturn(List.of(userRow))
                .thenReturn(List.of())
                .thenReturn(List.of());

        ComplianceAlert mockAlert = ComplianceAlert.builder()
                .clientId(clientId)
                .alertType(ComplianceAlertType.CONCENTRATION_ALERT)
                .severity("HIGH")
                .status(ComplianceAlertStatus.NEW)
                .build();
        when(complianceAlertService.createAlert(
                any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(mockAlert);

        int alertCount = amlMonitoringService.detectAnomalies(clientId);

        assertThat(alertCount).isGreaterThanOrEqualTo(1);
        verify(complianceAlertService).createAlert(
                eq(clientId),
                eq(ComplianceAlertType.CONCENTRATION_ALERT),
                eq("HIGH"),
                any(String.class),
                eq(userId),
                eq(null),
                eq(incentiveId));
    }

    @Test
    void detectAnomalies_noAlertWhenDistributedEvenly() {
        UUID userId1 = UUID.randomUUID();
        UUID userId2 = UUID.randomUUID();
        UUID incentiveId = UUID.randomUUID();

        // Each user has 50% or less (at threshold, not above)
        Map<String, Object> row1 = new HashMap<>();
        row1.put("incentive_id", incentiveId);
        row1.put("user_id", userId1);
        row1.put("user_total", new BigDecimal("5000.00"));
        row1.put("incentive_total", new BigDecimal("10000.00"));

        Map<String, Object> row2 = new HashMap<>();
        row2.put("incentive_id", incentiveId);
        row2.put("user_id", userId2);
        row2.put("user_total", new BigDecimal("5000.00"));
        row2.put("incentive_total", new BigDecimal("10000.00"));

        // User concentration returns evenly distributed, partner and proportionality return empty
        when(jdbcTemplate.queryForList(anyString(), eq(clientId)))
                .thenReturn(List.of(row1, row2))
                .thenReturn(List.of())
                .thenReturn(List.of());

        int alertCount = amlMonitoringService.detectAnomalies(clientId);

        assertThat(alertCount).isEqualTo(0);
    }
}
