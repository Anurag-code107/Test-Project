package com.tenxengage.app.service;

import com.tenxengage.app.dto.request.SaveFiscalYearConfigRequest;
import com.tenxengage.app.dto.response.FiscalYearConfigResponse;
import com.tenxengage.app.dto.response.FiscalYearLabelResponse;
import com.tenxengage.app.entity.FiscalYearConfig;
import com.tenxengage.app.entity.enums.QuarterMethod;
import com.tenxengage.app.exception.ResourceNotFoundException;
import com.tenxengage.app.repository.FiscalYearConfigRepository;
import com.tenxengage.app.security.TenantValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FiscalYearConfigServiceTest {

    @Mock
    private FiscalYearConfigRepository repository;

    @Mock
    private TenantValidator tenantValidator;

    @InjectMocks
    private FiscalYearConfigService service;

    private UUID clientId;
    private FiscalYearConfig fy2026;

    @BeforeEach
    void setUp() {
        clientId = UUID.randomUUID();

        fy2026 = new FiscalYearConfig();
        fy2026.setId(UUID.randomUUID());
        fy2026.setClientId(clientId);
        fy2026.setLabel("FY2026");
        fy2026.setStartDate(LocalDate.of(2026, 1, 1));
        fy2026.setEndDate(LocalDate.of(2026, 12, 31));
        fy2026.setQuarterMethod(QuarterMethod.MONTHS);
        fy2026.setQuarterSize(3);
        fy2026.setQ1StartDate(LocalDate.of(2026, 1, 1));
        fy2026.setQ1EndDate(LocalDate.of(2026, 3, 31));
        fy2026.setQ2StartDate(LocalDate.of(2026, 4, 1));
        fy2026.setQ2EndDate(LocalDate.of(2026, 6, 30));
        fy2026.setQ3StartDate(LocalDate.of(2026, 7, 1));
        fy2026.setQ3EndDate(LocalDate.of(2026, 9, 30));
        fy2026.setQ4StartDate(LocalDate.of(2026, 10, 1));
        fy2026.setQ4EndDate(LocalDate.of(2026, 12, 31));
        fy2026.setCreatedAt(Instant.now());
        fy2026.setUpdatedAt(Instant.now());
    }

    @Test
    void listConfigs_returnsAllForClient() {
        when(tenantValidator.getCurrentClientId()).thenReturn(clientId);
        when(repository.findByClientIdOrderByStartDateAsc(clientId)).thenReturn(List.of(fy2026));

        List<FiscalYearConfigResponse> result = service.listConfigs();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).label()).isEqualTo("FY2026");
    }

    @Test
    void getConfig_returnsConfig() {
        when(repository.findById(fy2026.getId())).thenReturn(Optional.of(fy2026));

        FiscalYearConfigResponse result = service.getConfig(fy2026.getId());

        assertThat(result.label()).isEqualTo("FY2026");
        assertThat(result.quarterMethod()).isEqualTo(QuarterMethod.MONTHS);
    }

    @Test
    void getConfig_notFound_throws() {
        UUID randomId = UUID.randomUUID();
        when(repository.findById(randomId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getConfig(randomId))
            .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getConfigByLabel_returnsConfig() {
        when(tenantValidator.getCurrentClientId()).thenReturn(clientId);
        when(repository.findByClientIdAndLabel(clientId, "FY2026")).thenReturn(Optional.of(fy2026));

        FiscalYearConfigResponse result = service.getConfigByLabel("FY2026");

        assertThat(result.label()).isEqualTo("FY2026");
    }

    @Test
    void getCurrentConfig_returnsCurrent() {
        when(tenantValidator.getCurrentClientId()).thenReturn(clientId);
        when(repository.findByClientIdOrderByStartDateAsc(clientId)).thenReturn(List.of(fy2026));

        // This test works when run during 2026 — or when today falls within the FY dates.
        // For resilience, we construct a config that always contains today.
        LocalDate today = LocalDate.now();
        FiscalYearConfig currentFy = new FiscalYearConfig();
        currentFy.setId(UUID.randomUUID());
        currentFy.setClientId(clientId);
        currentFy.setLabel("FY" + today.getYear());
        currentFy.setStartDate(LocalDate.of(today.getYear(), 1, 1));
        currentFy.setEndDate(LocalDate.of(today.getYear(), 12, 31));
        currentFy.setQuarterMethod(QuarterMethod.MONTHS);
        currentFy.setQuarterSize(3);
        currentFy.setQ1StartDate(LocalDate.of(today.getYear(), 1, 1));
        currentFy.setQ1EndDate(LocalDate.of(today.getYear(), 3, 31));
        currentFy.setQ2StartDate(LocalDate.of(today.getYear(), 4, 1));
        currentFy.setQ2EndDate(LocalDate.of(today.getYear(), 6, 30));
        currentFy.setQ3StartDate(LocalDate.of(today.getYear(), 7, 1));
        currentFy.setQ3EndDate(LocalDate.of(today.getYear(), 9, 30));
        currentFy.setQ4StartDate(LocalDate.of(today.getYear(), 10, 1));
        currentFy.setQ4EndDate(LocalDate.of(today.getYear(), 12, 31));
        currentFy.setCreatedAt(Instant.now());
        currentFy.setUpdatedAt(Instant.now());

        when(repository.findByClientIdOrderByStartDateAsc(clientId)).thenReturn(List.of(currentFy));

        FiscalYearConfigResponse result = service.getCurrentConfig();

        assertThat(result.label()).isEqualTo("FY" + today.getYear());
    }

    @Test
    void listLabels_returnsLightweight() {
        when(tenantValidator.getCurrentClientId()).thenReturn(clientId);
        when(repository.findByClientIdOrderByStartDateAsc(clientId)).thenReturn(List.of(fy2026));

        List<FiscalYearLabelResponse> result = service.listLabels();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).label()).isEqualTo("FY2026");
        assertThat(result.get(0).startDate()).isEqualTo(LocalDate.of(2026, 1, 1));
    }

    @Test
    void createConfig_savesSuccessfully() {
        when(tenantValidator.getCurrentClientId()).thenReturn(clientId);
        when(repository.existsByClientIdAndLabel(clientId, "FY2027")).thenReturn(false);
        when(repository.save(any(FiscalYearConfig.class))).thenAnswer(inv -> {
            FiscalYearConfig saved = inv.getArgument(0);
            saved.setId(UUID.randomUUID());
            saved.setCreatedAt(Instant.now());
            saved.setUpdatedAt(Instant.now());
            return saved;
        });

        SaveFiscalYearConfigRequest request = new SaveFiscalYearConfigRequest(
            "FY2027",
            LocalDate.of(2027, 1, 1), LocalDate.of(2027, 12, 31),
            QuarterMethod.MONTHS, 3,
            LocalDate.of(2027, 1, 1), LocalDate.of(2027, 3, 31),
            LocalDate.of(2027, 4, 1), LocalDate.of(2027, 6, 30),
            LocalDate.of(2027, 7, 1), LocalDate.of(2027, 9, 30),
            LocalDate.of(2027, 10, 1), LocalDate.of(2027, 12, 31)
        );

        FiscalYearConfigResponse result = service.createConfig(request);

        assertThat(result.label()).isEqualTo("FY2027");
        assertThat(result.quarterMethod()).isEqualTo(QuarterMethod.MONTHS);
    }

    @Test
    void createConfig_duplicateLabel_throws() {
        when(tenantValidator.getCurrentClientId()).thenReturn(clientId);
        when(repository.existsByClientIdAndLabel(clientId, "FY2026")).thenReturn(true);

        SaveFiscalYearConfigRequest request = new SaveFiscalYearConfigRequest(
            "FY2026",
            LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31),
            QuarterMethod.MONTHS, 3,
            LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31),
            LocalDate.of(2026, 4, 1), LocalDate.of(2026, 6, 30),
            LocalDate.of(2026, 7, 1), LocalDate.of(2026, 9, 30),
            LocalDate.of(2026, 10, 1), LocalDate.of(2026, 12, 31)
        );

        assertThatThrownBy(() -> service.createConfig(request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("already exists");
    }

    @Test
    void createConfig_nonContiguousQuarters_throws() {
        when(tenantValidator.getCurrentClientId()).thenReturn(clientId);
        when(repository.existsByClientIdAndLabel(clientId, "FY2027")).thenReturn(false);

        // Gap between Q1 end (Mar 31) and Q2 start (Apr 5)
        SaveFiscalYearConfigRequest request = new SaveFiscalYearConfigRequest(
            "FY2027",
            LocalDate.of(2027, 1, 1), LocalDate.of(2027, 12, 31),
            QuarterMethod.CUSTOM, null,
            LocalDate.of(2027, 1, 1), LocalDate.of(2027, 3, 31),
            LocalDate.of(2027, 4, 5), LocalDate.of(2027, 6, 30),
            LocalDate.of(2027, 7, 1), LocalDate.of(2027, 9, 30),
            LocalDate.of(2027, 10, 1), LocalDate.of(2027, 12, 31)
        );

        assertThatThrownBy(() -> service.createConfig(request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Q1 end date must be the day before Q2 start date");
    }

    @Test
    void updateConfig_updatesSuccessfully() {
        when(repository.findById(fy2026.getId())).thenReturn(Optional.of(fy2026));
        when(repository.save(any(FiscalYearConfig.class))).thenAnswer(inv -> inv.getArgument(0));

        SaveFiscalYearConfigRequest request = new SaveFiscalYearConfigRequest(
            "FY2026",
            LocalDate.of(2026, 2, 1), LocalDate.of(2027, 1, 31),
            QuarterMethod.MONTHS, 3,
            LocalDate.of(2026, 2, 1), LocalDate.of(2026, 4, 30),
            LocalDate.of(2026, 5, 1), LocalDate.of(2026, 7, 31),
            LocalDate.of(2026, 8, 1), LocalDate.of(2026, 10, 31),
            LocalDate.of(2026, 11, 1), LocalDate.of(2027, 1, 31)
        );

        FiscalYearConfigResponse result = service.updateConfig(fy2026.getId(), request);

        assertThat(result.startDate()).isEqualTo(LocalDate.of(2026, 2, 1));
        assertThat(result.endDate()).isEqualTo(LocalDate.of(2027, 1, 31));
    }

    @Test
    void deleteConfig_deletesSuccessfully() {
        when(repository.findById(fy2026.getId())).thenReturn(Optional.of(fy2026));

        service.deleteConfig(fy2026.getId());

        verify(repository).delete(fy2026);
    }

    @Test
    void deleteConfig_notFound_throws() {
        UUID randomId = UUID.randomUUID();
        when(repository.findById(randomId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteConfig(randomId))
            .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void generateNextConfig_monthsMethod_createsShiftedFy() {
        when(repository.findByClientIdOrderByStartDateAsc(clientId)).thenReturn(List.of(fy2026));
        when(repository.existsByClientIdAndLabel(clientId, "FY2027")).thenReturn(false);
        when(repository.save(any(FiscalYearConfig.class))).thenAnswer(inv -> {
            FiscalYearConfig saved = inv.getArgument(0);
            saved.setId(UUID.randomUUID());
            saved.setCreatedAt(Instant.now());
            saved.setUpdatedAt(Instant.now());
            return saved;
        });

        FiscalYearConfig result = service.generateNextConfig(clientId);

        assertThat(result).isNotNull();
        assertThat(result.getLabel()).isEqualTo("FY2027");
        assertThat(result.getStartDate()).isEqualTo(LocalDate.of(2027, 1, 1));
        assertThat(result.getEndDate()).isEqualTo(LocalDate.of(2027, 12, 31));
        assertThat(result.getQuarterMethod()).isEqualTo(QuarterMethod.MONTHS);
        assertThat(result.getQuarterSize()).isEqualTo(3);
        assertThat(result.getQ1StartDate()).isEqualTo(LocalDate.of(2027, 1, 1));
        assertThat(result.getQ1EndDate()).isEqualTo(LocalDate.of(2027, 3, 31));
        assertThat(result.getQ2StartDate()).isEqualTo(LocalDate.of(2027, 4, 1));
        assertThat(result.getQ2EndDate()).isEqualTo(LocalDate.of(2027, 6, 30));
        assertThat(result.getQ3StartDate()).isEqualTo(LocalDate.of(2027, 7, 1));
        assertThat(result.getQ3EndDate()).isEqualTo(LocalDate.of(2027, 9, 30));
        assertThat(result.getQ4StartDate()).isEqualTo(LocalDate.of(2027, 10, 1));
        assertThat(result.getQ4EndDate()).isEqualTo(LocalDate.of(2027, 12, 31));
    }

    @Test
    void generateNextConfig_customMethod_shiftsByFyDuration() {
        FiscalYearConfig customFy = new FiscalYearConfig();
        customFy.setId(UUID.randomUUID());
        customFy.setClientId(clientId);
        customFy.setLabel("FY2026");
        customFy.setStartDate(LocalDate.of(2026, 1, 1));
        customFy.setEndDate(LocalDate.of(2026, 12, 31));
        customFy.setQuarterMethod(QuarterMethod.CUSTOM);
        customFy.setQuarterSize(null);
        customFy.setQ1StartDate(LocalDate.of(2026, 1, 1));
        customFy.setQ1EndDate(LocalDate.of(2026, 3, 31));
        customFy.setQ2StartDate(LocalDate.of(2026, 4, 1));
        customFy.setQ2EndDate(LocalDate.of(2026, 6, 30));
        customFy.setQ3StartDate(LocalDate.of(2026, 7, 1));
        customFy.setQ3EndDate(LocalDate.of(2026, 9, 30));
        customFy.setQ4StartDate(LocalDate.of(2026, 10, 1));
        customFy.setQ4EndDate(LocalDate.of(2026, 12, 31));
        customFy.setCreatedAt(Instant.now());
        customFy.setUpdatedAt(Instant.now());

        when(repository.findByClientIdOrderByStartDateAsc(clientId)).thenReturn(List.of(customFy));
        when(repository.existsByClientIdAndLabel(clientId, "FY2027")).thenReturn(false);
        when(repository.save(any(FiscalYearConfig.class))).thenAnswer(inv -> {
            FiscalYearConfig saved = inv.getArgument(0);
            saved.setId(UUID.randomUUID());
            saved.setCreatedAt(Instant.now());
            saved.setUpdatedAt(Instant.now());
            return saved;
        });

        FiscalYearConfig result = service.generateNextConfig(clientId);

        // 2026 is not a leap year: 365 days. offset = 365 + 1 = 366? No:
        // ChronoUnit.DAYS.between(Jan 1, Dec 31) = 364, offset = 365
        assertThat(result).isNotNull();
        assertThat(result.getLabel()).isEqualTo("FY2027");
        assertThat(result.getStartDate()).isEqualTo(LocalDate.of(2027, 1, 1));
        assertThat(result.getEndDate()).isEqualTo(LocalDate.of(2027, 12, 31));
        assertThat(result.getQuarterMethod()).isEqualTo(QuarterMethod.CUSTOM);
        assertThat(result.getQ1StartDate()).isEqualTo(LocalDate.of(2027, 1, 1));
        assertThat(result.getQ4EndDate()).isEqualTo(LocalDate.of(2027, 12, 31));
    }

    @Test
    void generateNextConfig_alreadyExists_returnsNull() {
        when(repository.findByClientIdOrderByStartDateAsc(clientId)).thenReturn(List.of(fy2026));
        when(repository.existsByClientIdAndLabel(clientId, "FY2027")).thenReturn(true);

        FiscalYearConfig result = service.generateNextConfig(clientId);

        assertThat(result).isNull();
        verify(repository, never()).save(any());
    }

    @Test
    void generateNextConfig_noConfigs_returnsNull() {
        when(repository.findByClientIdOrderByStartDateAsc(clientId)).thenReturn(List.of());

        FiscalYearConfig result = service.generateNextConfig(clientId);

        assertThat(result).isNull();
    }
}
