package com.tenxengage.app.service;

import com.tenxengage.app.dto.request.CreateWhistleblowerReportRequest;
import com.tenxengage.app.entity.WhistleblowerReport;
import com.tenxengage.app.entity.enums.WhistleblowerReportType;
import com.tenxengage.app.entity.enums.WhistleblowerStatus;
import com.tenxengage.app.exception.BusinessRuleException;
import com.tenxengage.app.repository.WhistleblowerCaseUpdateRepository;
import com.tenxengage.app.repository.WhistleblowerReportRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WhistleblowerServiceTest {

    @Mock
    private WhistleblowerReportRepository reportRepository;

    @Mock
    private WhistleblowerCaseUpdateRepository caseUpdateRepository;

    @InjectMocks
    private WhistleblowerService whistleblowerService;

    private UUID clientId;
    private UUID reportId;
    private UUID resolverUserId;

    @BeforeEach
    void setUp() {
        clientId = UUID.randomUUID();
        reportId = UUID.randomUUID();
        resolverUserId = UUID.randomUUID();
    }

    @Test
    void submitReport_generatesTrackingNumber() {
        CreateWhistleblowerReportRequest request = new CreateWhistleblowerReportRequest(
                "SUSPICIOUS_INCENTIVE",
                "Suspicious activity observed in incentive program distribution",
                null, null, null, true, clientId);

        when(reportRepository.findByTrackingNumber(anyString())).thenReturn(Optional.empty());
        when(reportRepository.save(any(WhistleblowerReport.class))).thenAnswer(inv -> {
            WhistleblowerReport report = inv.getArgument(0);
            report.setId(UUID.randomUUID());
            return report;
        });

        WhistleblowerReport result = whistleblowerService.submitReport(request);

        assertThat(result).isNotNull();
        assertThat(result.getTrackingNumber()).hasSize(8);
        assertThat(result.getTrackingNumber()).matches("[A-Z2-9]{8}");
    }

    @Test
    void submitReport_setsNewStatus() {
        CreateWhistleblowerReportRequest request = new CreateWhistleblowerReportRequest(
                "POLICY_VIOLATION",
                "Policy violation detected during quarterly review process",
                null, "reporter@example.com", "John Doe", false, clientId);

        when(reportRepository.findByTrackingNumber(anyString())).thenReturn(Optional.empty());
        when(reportRepository.save(any(WhistleblowerReport.class))).thenAnswer(inv -> {
            WhistleblowerReport report = inv.getArgument(0);
            report.setId(UUID.randomUUID());
            return report;
        });

        WhistleblowerReport result = whistleblowerService.submitReport(request);

        assertThat(result.getStatus()).isEqualTo(WhistleblowerStatus.NEW);
        assertThat(result.getReporterEmail()).isEqualTo("reporter@example.com");
        assertThat(result.isAnonymous()).isFalse();
    }

    @Test
    void acknowledgeReport_setsDeadline() {
        WhistleblowerReport report = WhistleblowerReport.builder()
                .id(reportId)
                .clientId(clientId)
                .reportType(WhistleblowerReportType.SUSPICIOUS_INCENTIVE)
                .description("Test report")
                .trackingNumber("ABC12345")
                .status(WhistleblowerStatus.NEW)
                .build();

        when(reportRepository.findById(reportId)).thenReturn(Optional.of(report));
        when(reportRepository.save(any(WhistleblowerReport.class))).thenAnswer(inv -> inv.getArgument(0));

        WhistleblowerReport result = whistleblowerService.acknowledgeReport(reportId);

        assertThat(result.getStatus()).isEqualTo(WhistleblowerStatus.ACKNOWLEDGED);
        assertThat(result.getAcknowledgedAt()).isNotNull();
        assertThat(result.getResolutionDeadline()).isNotNull();
        assertThat(result.getResolutionDeadline()).isAfter(Instant.now());
    }

    @Test
    void resolveReport_setsResolvedFields() {
        WhistleblowerReport report = WhistleblowerReport.builder()
                .id(reportId)
                .clientId(clientId)
                .reportType(WhistleblowerReportType.POTENTIAL_KICKBACK)
                .description("Kickback report")
                .trackingNumber("DEF67890")
                .status(WhistleblowerStatus.UNDER_INVESTIGATION)
                .build();

        when(reportRepository.findById(reportId)).thenReturn(Optional.of(report));
        when(reportRepository.save(any(WhistleblowerReport.class))).thenAnswer(inv -> inv.getArgument(0));

        WhistleblowerReport result = whistleblowerService.resolveReport(
                reportId, resolverUserId, "Investigation complete, no violation found");

        assertThat(result.getStatus()).isEqualTo(WhistleblowerStatus.RESOLVED);
        assertThat(result.getResolvedBy()).isEqualTo(resolverUserId);
        assertThat(result.getResolvedAt()).isNotNull();
        assertThat(result.getResolutionNotes()).isEqualTo("Investigation complete, no violation found");
    }

    @Test
    void getReportByTrackingNumber_returnsReport() {
        WhistleblowerReport report = WhistleblowerReport.builder()
                .id(reportId)
                .clientId(clientId)
                .trackingNumber("XYZ99999")
                .status(WhistleblowerStatus.NEW)
                .reportType(WhistleblowerReportType.OTHER)
                .description("Other report")
                .build();

        when(reportRepository.findByTrackingNumber("XYZ99999")).thenReturn(Optional.of(report));

        WhistleblowerReport result = whistleblowerService.getReportByTrackingNumber("XYZ99999");

        assertThat(result).isNotNull();
        assertThat(result.getTrackingNumber()).isEqualTo("XYZ99999");
    }

    @Test
    void getReportByTrackingNumber_throwsWhenNotFound() {
        when(reportRepository.findByTrackingNumber("NOTFOUND")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> whistleblowerService.getReportByTrackingNumber("NOTFOUND"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("No report found for tracking number");
    }
}
