package com.tenxengage.app.service.redemption;

import com.tenxengage.app.dto.response.BalanceBreakageReportResponse;
import com.tenxengage.app.entity.enums.Granularity;
import com.tenxengage.app.exception.BusinessRuleException;
import com.tenxengage.app.repository.LedgerEntryRepository;
import com.tenxengage.app.repository.projection.BreakageRowProjection;
import com.tenxengage.app.security.TenantValidator;
import com.tenxengage.app.service.AuditLogService;
import com.tenxengage.app.service.FeatureFlagService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BalanceBreakageReportServiceTest {

    @Mock
    private LedgerEntryRepository ledgerEntryRepository;
    @Mock
    private TenantValidator tenantValidator;
    @Mock
    private FeatureFlagService featureFlagService;
    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private BalanceBreakageReportService service;

    private static final UUID CLIENT_ID = UUID.randomUUID();
    private static final LocalDate FROM = LocalDate.of(2025, 1, 1);
    private static final LocalDate TO = LocalDate.of(2025, 6, 30);

    @BeforeEach
    void setUp() {
        when(tenantValidator.getCurrentClientId()).thenReturn(CLIENT_ID);
        when(featureFlagService.getEnabledFeatures(CLIENT_ID))
                .thenReturn(List.of("reward_balance_expiration"));
    }

    // ── getBreakage — aggregation correctness ──────────────────────────────────

    @Test
    void getBreakage_returnsReport_withRows() {
        BreakageRowProjection proj = mockProjection(
                LocalDate.of(2025, 1, 1), "points", 5, new BigDecimal("1500.00"));
        when(ledgerEntryRepository.aggregateExpiryBreakage(
                eq(CLIENT_ID), any(Instant.class), any(Instant.class), isNull(), eq("month")))
                .thenReturn(List.of(proj));

        BalanceBreakageReportResponse response = service.getBreakage(FROM, TO, null, Granularity.MONTH);

        assertThat(response.from()).isEqualTo(FROM);
        assertThat(response.to()).isEqualTo(TO);
        assertThat(response.granularity()).isEqualTo(Granularity.MONTH);
        assertThat(response.rows()).hasSize(1);
        assertThat(response.rows().get(0).currencyId()).isEqualTo("points");
        assertThat(response.rows().get(0).expiredCount()).isEqualTo(5L);
        assertThat(response.rows().get(0).totalExpiredAmount()).isEqualByComparingTo(new BigDecimal("1500.00"));
        assertThat(response.rows().get(0).currencyDisplayName()).isEqualTo("Points");
    }

    // ── getBreakage — period end derivation (MONTH) ────────────────────────────

    @Test
    void getBreakage_monthGranularity_computesPeriodEnd() {
        BreakageRowProjection proj = mockProjection(
                LocalDate.of(2025, 3, 1), "cash", 2, new BigDecimal("200.00"));
        when(ledgerEntryRepository.aggregateExpiryBreakage(
                any(), any(), any(), isNull(), eq("month")))
                .thenReturn(List.of(proj));

        BalanceBreakageReportResponse response = service.getBreakage(FROM, TO, null, Granularity.MONTH);

        assertThat(response.rows().get(0).periodStart()).isEqualTo(LocalDate.of(2025, 3, 1));
        assertThat(response.rows().get(0).periodEnd()).isEqualTo(LocalDate.of(2025, 3, 31));
    }

    // ── getBreakage — period end derivation (QUARTER) ─────────────────────────

    @Test
    void getBreakage_quarterGranularity_computesPeriodEnd_Q1() {
        BreakageRowProjection proj = mockProjection(
                LocalDate.of(2025, 1, 1), "credits", 3, new BigDecimal("900.00"));
        when(ledgerEntryRepository.aggregateExpiryBreakage(
                any(), any(), any(), isNull(), eq("quarter")))
                .thenReturn(List.of(proj));

        BalanceBreakageReportResponse response = service.getBreakage(FROM, TO, null, Granularity.QUARTER);

        assertThat(response.rows().get(0).periodStart()).isEqualTo(LocalDate.of(2025, 1, 1));
        assertThat(response.rows().get(0).periodEnd()).isEqualTo(LocalDate.of(2025, 3, 31));
    }

    @Test
    void getBreakage_quarterGranularity_computesPeriodEnd_Q2() {
        BreakageRowProjection proj = mockProjection(
                LocalDate.of(2025, 4, 1), "credits", 7, new BigDecimal("2100.00"));
        when(ledgerEntryRepository.aggregateExpiryBreakage(
                any(), any(), any(), isNull(), eq("quarter")))
                .thenReturn(List.of(proj));

        BalanceBreakageReportResponse response = service.getBreakage(FROM, TO, null, Granularity.QUARTER);

        assertThat(response.rows().get(0).periodEnd()).isEqualTo(LocalDate.of(2025, 6, 30));
    }

    // ── getBreakage — currency filter ─────────────────────────────────────────

    @Test
    void getBreakage_withCurrencyFilter_passesCurrencyIdToRepository() {
        BreakageRowProjection proj = mockProjection(
                LocalDate.of(2025, 2, 1), "points", 1, new BigDecimal("500.00"));
        when(ledgerEntryRepository.aggregateExpiryBreakage(
                eq(CLIENT_ID), any(Instant.class), any(Instant.class), eq("points"), eq("month")))
                .thenReturn(List.of(proj));

        BalanceBreakageReportResponse response = service.getBreakage(FROM, TO, "points", Granularity.MONTH);

        assertThat(response.rows()).hasSize(1);
        assertThat(response.rows().get(0).currencyId()).isEqualTo("points");
    }

    // ── getBreakage — empty result ─────────────────────────────────────────────

    @Test
    void getBreakage_returnsEmptyRows_whenNoExpiries() {
        when(ledgerEntryRepository.aggregateExpiryBreakage(any(), any(), any(), any(), anyString()))
                .thenReturn(List.of());

        BalanceBreakageReportResponse response = service.getBreakage(FROM, TO, null, Granularity.MONTH);

        assertThat(response.rows()).isEmpty();
    }

    // ── getBreakage — null granularity defaults to MONTH ──────────────────────

    @Test
    void getBreakage_nullGranularity_defaultsToMonth() {
        when(ledgerEntryRepository.aggregateExpiryBreakage(
                any(), any(), any(), any(), eq("month")))
                .thenReturn(List.of());

        BalanceBreakageReportResponse response = service.getBreakage(FROM, TO, null, null);

        assertThat(response.granularity()).isEqualTo(Granularity.MONTH);
    }

    // ── getBreakage — date range validation ───────────────────────────────────

    @Test
    void getBreakage_throws400_whenToBeforeFrom() {
        LocalDate badTo = FROM.minusDays(1);

        assertThatThrownBy(() -> service.getBreakage(FROM, badTo, null, Granularity.MONTH))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(ex -> assertThat(((BusinessRuleException) ex).getErrorCode())
                        .isEqualTo("ERR_INVALID_DATE_RANGE"))
                .hasMessageContaining("End date must be on or after start date");
    }

    @Test
    void getBreakage_throws400_whenRangeExceeds24Months() {
        LocalDate longTo = FROM.plusMonths(25);

        assertThatThrownBy(() -> service.getBreakage(FROM, longTo, null, Granularity.MONTH))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(ex -> assertThat(((BusinessRuleException) ex).getErrorCode())
                        .isEqualTo("ERR_INVALID_DATE_RANGE"))
                .hasMessageContaining("24 months");
    }

    @Test
    void getBreakage_allowsExactly24Months() {
        LocalDate exactTo = FROM.plusMonths(24);
        when(ledgerEntryRepository.aggregateExpiryBreakage(any(), any(), any(), any(), anyString()))
                .thenReturn(List.of());

        // Should not throw
        BalanceBreakageReportResponse response = service.getBreakage(FROM, exactTo, null, Granularity.MONTH);
        assertThat(response).isNotNull();
    }

    // ── exportBreakageCsv — CSV structure and formula-injection escaping ───────

    @Test
    void exportBreakageCsv_returnsHeaderOnlyWhenNoRows() {
        when(ledgerEntryRepository.aggregateExpiryBreakage(any(), any(), any(), any(), anyString()))
                .thenReturn(List.of());

        String csv = service.exportBreakageCsv(FROM, TO, null, Granularity.MONTH);

        assertThat(csv).isEqualTo("period_start,period_end,currency_id,expired_count,total_expired_amount\n");
    }

    @Test
    void exportBreakageCsv_containsDataRow() {
        BreakageRowProjection proj = mockProjection(
                LocalDate.of(2025, 1, 1), "points", 3, new BigDecimal("750.00"));
        when(ledgerEntryRepository.aggregateExpiryBreakage(
                any(), any(), any(), any(), eq("month")))
                .thenReturn(List.of(proj));

        String csv = service.exportBreakageCsv(FROM, TO, null, Granularity.MONTH);

        assertThat(csv).contains("2025-01-01");
        assertThat(csv).contains("2025-01-31");
        assertThat(csv).contains("points");
        assertThat(csv).contains("3");
        assertThat(csv).contains("750.00");
    }

    @Test
    void exportBreakageCsv_escapes_formulaInjection_leadingEquals() {
        // currencyId starting with '=' — should be prefixed with a single-quote
        BreakageRowProjection proj = mockProjection(
                LocalDate.of(2025, 1, 1), "=DANGEROUS", 1, BigDecimal.ONE);
        when(ledgerEntryRepository.aggregateExpiryBreakage(any(), any(), any(), any(), anyString()))
                .thenReturn(List.of(proj));

        String csv = service.exportBreakageCsv(FROM, TO, null, Granularity.MONTH);

        // CsvUtil prepends ' to neutralise the formula; result column contains '=DANGEROUS
        assertThat(csv).contains("'=DANGEROUS");
    }

    // ── exportBreakageCsv — audit is emitted in the service (not via @Audited on controller) ──

    @Test
    void exportBreakageCsv_emitsAuditRecord_afterCsvBuilt() {
        when(ledgerEntryRepository.aggregateExpiryBreakage(any(), any(), any(), any(), anyString()))
                .thenReturn(List.of());

        service.exportBreakageCsv(FROM, TO, null, Granularity.MONTH);

        // Verify auditLogService.logAsync was called exactly once, meaning the audit
        // is fired from the service (not via @Audited on the controller method).
        // This proves that 429 rate-limited returns — which exit the controller before
        // reaching the service call — do NOT produce an audit row.
        verify(auditLogService).logAsync(
                eq(com.tenxengage.app.entity.enums.AuditAction.DATA_EXPORTED),
                eq(com.tenxengage.app.entity.enums.AuditResourceType.BALANCE_EXPIRY_BREAKAGE_EXPORT),
                isNull(),
                isNull(),
                any(),
                any()
        );
    }

    // ── feature flag guard ─────────────────────────────────────────────────────

    @Test
    void getBreakage_throws_whenFeatureDisabled() {
        when(featureFlagService.getEnabledFeatures(CLIENT_ID)).thenReturn(List.of());

        assertThatThrownBy(() -> service.getBreakage(FROM, TO, null, Granularity.MONTH))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(ex -> assertThat(((BusinessRuleException) ex).getErrorCode())
                        .isEqualTo("FEATURE_DISABLED"));
    }

    // ── Helper ─────────────────────────────────────────────────────────────────

    private static BreakageRowProjection mockProjection(
            LocalDate periodStart, String currencyId, long expiredCount, BigDecimal totalExpiredAmount) {
        return new BreakageRowProjection() {
            @Override
            public LocalDate getPeriodStart() {
                return periodStart;
            }

            @Override
            public String getCurrencyId() {
                return currencyId;
            }

            @Override
            public long getExpiredCount() {
                return expiredCount;
            }

            @Override
            public BigDecimal getTotalExpiredAmount() {
                return totalExpiredAmount;
            }
        };
    }
}
