package com.tenxengage.app.service.redemption;

import com.tenxengage.app.dto.response.redemption.CurrencyTypeRateDto;
import com.tenxengage.app.dto.response.redemption.RedemptionAnalyticsSummaryResponse;
import com.tenxengage.app.entity.enums.LedgerEntryType;
import com.tenxengage.app.entity.enums.RedemptionStatus;
import com.tenxengage.app.exception.BusinessRuleException;
import com.tenxengage.app.repository.LedgerEntryRepository;
import com.tenxengage.app.repository.RedemptionRequestRepository;
import com.tenxengage.app.repository.RewardWalletRepository;
import com.tenxengage.app.repository.projection.BalanceSumProjection;
import com.tenxengage.app.repository.projection.RewardWalletExportProjection;
import com.tenxengage.app.repository.projection.StatusCountProjection;
import com.tenxengage.app.security.TenantValidator;
import com.tenxengage.app.service.AuditLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RedemptionAnalyticsServiceTest {

    @Mock private LedgerEntryRepository ledgerEntryRepository;
    @Mock private RewardWalletRepository rewardWalletRepository;
    @Mock private RedemptionRequestRepository redemptionRequestRepository;
    @Mock private TenantValidator tenantValidator;
    @Mock private AuditLogService auditLogService;

    @InjectMocks
    private RedemptionAnalyticsService service;

    private static final UUID CLIENT_ID = UUID.fromString("a0000000-0000-0000-0000-000000000001");
    private static final String CURRENCY = "CASH";

    private static final LocalDate DATE_FROM = LocalDate.of(2026, 5, 1);
    private static final LocalDate DATE_TO = LocalDate.of(2026, 5, 31);

    @BeforeEach
    void setUp() {
        when(tenantValidator.getCurrentClientId()).thenReturn(CLIENT_ID);
    }

    // ── Happy path ────────────────────────────────────────────────────────────────

    @Test
    void happyPath_oneActiveCurrency_redemptionRateCalculatedCorrectly() {
        when(ledgerEntryRepository.findDistinctCurrencyIdsByClientId(CLIENT_ID))
                .thenReturn(List.of(CURRENCY));

        // 200 earned, 50 redeemed  →  25.00%
        when(ledgerEntryRepository.sumAmountByClientIdAndCurrencyIdAndEntryType(
                CLIENT_ID, CURRENCY, LedgerEntryType.CREDIT))
                .thenReturn(BigDecimal.valueOf(200));
        when(ledgerEntryRepository.sumAmountByClientIdAndCurrencyIdAndEntryType(
                CLIENT_ID, CURRENCY, LedgerEntryType.DEBIT))
                .thenReturn(BigDecimal.valueOf(50));

        stubBalances(BigDecimal.valueOf(1000), BigDecimal.valueOf(200));
        stubWindowedCounts(10L, 2L);
        stubStatusCounts(List.of(
                statusProjection(RedemptionStatus.COMPLETED, 8L),
                statusProjection(RedemptionStatus.FAILED, 1L),
                statusProjection(RedemptionStatus.CANCELLED, 1L)
        ));

        RedemptionAnalyticsSummaryResponse response =
                service.getAnalyticsSummary(DATE_FROM, DATE_TO);

        assertThat(response.redemptionRates()).hasSize(1);
        CurrencyTypeRateDto rate = response.redemptionRates().get(0);
        assertThat(rate.currencyId()).isEqualTo(CURRENCY);
        assertThat(rate.numerator()).isEqualTo(50L);
        assertThat(rate.denominator()).isEqualTo(200L);
        assertThat(rate.ratePercentage()).isEqualTo("25.00");
        assertThat(rate.hasActivity()).isTrue();
    }

    @Test
    void hasActivity_false_whenDenominatorIsZero_noException() {
        when(ledgerEntryRepository.findDistinctCurrencyIdsByClientId(CLIENT_ID))
                .thenReturn(List.of(CURRENCY));

        // No earning events yet
        when(ledgerEntryRepository.sumAmountByClientIdAndCurrencyIdAndEntryType(
                CLIENT_ID, CURRENCY, LedgerEntryType.CREDIT))
                .thenReturn(BigDecimal.ZERO);
        when(ledgerEntryRepository.sumAmountByClientIdAndCurrencyIdAndEntryType(
                CLIENT_ID, CURRENCY, LedgerEntryType.DEBIT))
                .thenReturn(BigDecimal.ZERO);

        stubBalances(BigDecimal.ZERO, BigDecimal.ZERO);
        stubWindowedCounts(0L, 0L);
        stubStatusCounts(List.of());

        RedemptionAnalyticsSummaryResponse response =
                service.getAnalyticsSummary(DATE_FROM, DATE_TO);

        CurrencyTypeRateDto rate = response.redemptionRates().get(0);
        assertThat(rate.hasActivity()).isFalse();
        assertThat(rate.ratePercentage()).isEqualTo("0.00");
        // Verify no ArithmeticException was thrown — test passes if we reach here
    }

    @Test
    void emptyTenant_noWallets_returnsEmptyLists() {
        when(ledgerEntryRepository.findDistinctCurrencyIdsByClientId(CLIENT_ID))
                .thenReturn(List.of());
        stubStatusCounts(List.of());

        RedemptionAnalyticsSummaryResponse response =
                service.getAnalyticsSummary(DATE_FROM, DATE_TO);

        assertThat(response.redemptionRates()).isEmpty();
        assertThat(response.unredeemedBalances()).isEmpty();
        assertThat(response.failedCancelledRates()).isEmpty();
        assertThat(response.totalRedemptionCount().total()).isZero();
        assertThat(response.totalRedemptionCount().hasActivity()).isFalse();

        // No per-currency repo calls when there are no active currencies
        verify(ledgerEntryRepository, never())
                .sumAmountByClientIdAndCurrencyIdAndEntryType(any(), any(), any());
    }

    @Test
    void dateFrom_afterDateTo_throwsBusinessRuleException() {
        assertThatThrownBy(() -> service.getAnalyticsSummary(DATE_TO, DATE_FROM))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("dateFrom must not be after dateTo");
    }

    @Test
    void spanOver730Days_throwsBusinessRuleException() {
        LocalDate farFuture = DATE_FROM.plusDays(731);
        assertThatThrownBy(() -> service.getAnalyticsSummary(DATE_FROM, farFuture))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("730");
    }

    // ── Failed/cancelled rate parameterized ────────────────────────────────────

    static Stream<Arguments> failedCancelledCases() {
        return Stream.of(
                Arguments.of("only FAILED", 3L, 0L, 10L, "30.00"),
                Arguments.of("only CANCELLED", 0L, 4L, 10L, "40.00"),
                Arguments.of("both FAILED+CANCELLED", 3L, 2L, 10L, "50.00")
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("failedCancelledCases")
    void failedCancelledRate_parameterized(String label, long failed, long cancelled,
                                           long total, String expectedPct) {
        when(ledgerEntryRepository.findDistinctCurrencyIdsByClientId(CLIENT_ID))
                .thenReturn(List.of(CURRENCY));
        stubLifetimeLedger(BigDecimal.valueOf(100), BigDecimal.valueOf(10));
        stubBalances(BigDecimal.valueOf(500), BigDecimal.valueOf(100));

        // total requests in window
        when(redemptionRequestRepository.countByClientIdAndCurrencyIdAndSubmittedAtBetween(
                eq(CLIENT_ID), eq(CURRENCY), any(), any()))
                .thenReturn(total);
        // failed+cancelled requests in window
        when(redemptionRequestRepository.countByClientIdAndCurrencyIdAndStatusInAndSubmittedAtBetween(
                eq(CLIENT_ID), eq(CURRENCY), any(Collection.class), any(), any()))
                .thenReturn(failed + cancelled);

        stubStatusCounts(List.of(
                statusProjection(RedemptionStatus.FAILED, failed),
                statusProjection(RedemptionStatus.CANCELLED, cancelled),
                statusProjection(RedemptionStatus.COMPLETED, total - failed - cancelled)
        ));

        RedemptionAnalyticsSummaryResponse response =
                service.getAnalyticsSummary(DATE_FROM, DATE_TO);

        CurrencyTypeRateDto fcRate = response.failedCancelledRates().get(0);
        assertThat(fcRate.hasActivity()).isTrue();
        assertThat(fcRate.ratePercentage()).isEqualTo(expectedPct);
    }

    // ── Date window and cache key ──────────────────────────────────────────────

    @Test
    void buildCacheKey_includesClientIdAndDates() {
        String key = service.buildCacheKey(DATE_FROM, DATE_TO);
        assertThat(key).startsWith(CLIENT_ID.toString());
        assertThat(key).contains(DATE_FROM.toString());
        assertThat(key).contains(DATE_TO.toString());
    }

    @Test
    void dateWindow_reflectedInResponse() {
        when(ledgerEntryRepository.findDistinctCurrencyIdsByClientId(CLIENT_ID))
                .thenReturn(List.of());
        stubStatusCounts(List.of());

        RedemptionAnalyticsSummaryResponse response =
                service.getAnalyticsSummary(DATE_FROM, DATE_TO);

        assertThat(response.dateWindow().from()).isEqualTo(DATE_FROM);
        assertThat(response.dateWindow().to()).isEqualTo(DATE_TO);
    }

    // ── Status aggregation (PENDING_APPROVAL + RESERVED → PENDING) ──────────

    @Test
    void totalCountCard_mergesPendingApprovalAndReservedIntoPending() {
        when(ledgerEntryRepository.findDistinctCurrencyIdsByClientId(CLIENT_ID))
                .thenReturn(List.of());

        stubStatusCounts(List.of(
                statusProjection(RedemptionStatus.PENDING_APPROVAL, 3L),
                statusProjection(RedemptionStatus.RESERVED, 2L),
                statusProjection(RedemptionStatus.PROCESSING, 1L),
                statusProjection(RedemptionStatus.COMPLETED, 4L)
        ));

        RedemptionAnalyticsSummaryResponse response =
                service.getAnalyticsSummary(DATE_FROM, DATE_TO);

        assertThat(response.totalRedemptionCount().byStatus().get("PENDING")).isEqualTo(5L);
        assertThat(response.totalRedemptionCount().byStatus().get("PROCESSING")).isEqualTo(1L);
        assertThat(response.totalRedemptionCount().byStatus().get("COMPLETED")).isEqualTo(4L);
        assertThat(response.totalRedemptionCount().total()).isEqualTo(10L);
        assertThat(response.totalRedemptionCount().hasActivity()).isTrue();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void stubLifetimeLedger(BigDecimal earned, BigDecimal redeemed) {
        when(ledgerEntryRepository.sumAmountByClientIdAndCurrencyIdAndEntryType(
                CLIENT_ID, CURRENCY, LedgerEntryType.CREDIT)).thenReturn(earned);
        when(ledgerEntryRepository.sumAmountByClientIdAndCurrencyIdAndEntryType(
                CLIENT_ID, CURRENCY, LedgerEntryType.DEBIT)).thenReturn(redeemed);
    }

    private void stubBalances(BigDecimal available, BigDecimal reserved) {
        BalanceSumProjection proj = new BalanceSumProjection() {
            @Override public BigDecimal getAvailable() { return available; }
            @Override public BigDecimal getReserved()  { return reserved; }
        };
        when(rewardWalletRepository.sumBalancesByClientIdAndCurrencyId(CLIENT_ID, CURRENCY))
                .thenReturn(proj);
    }

    private void stubWindowedCounts(Long total, Long failedCancelled) {
        when(redemptionRequestRepository.countByClientIdAndCurrencyIdAndSubmittedAtBetween(
                eq(CLIENT_ID), eq(CURRENCY), any(), any()))
                .thenReturn(total);
        when(redemptionRequestRepository.countByClientIdAndCurrencyIdAndStatusInAndSubmittedAtBetween(
                eq(CLIENT_ID), eq(CURRENCY), any(Collection.class), any(), any()))
                .thenReturn(failedCancelled);
    }

    private void stubStatusCounts(List<StatusCountProjection> projections) {
        when(redemptionRequestRepository.countGroupByStatusByClientIdAndSubmittedAtBetween(
                eq(CLIENT_ID), any(), any()))
                .thenReturn(projections);
    }

    private StatusCountProjection statusProjection(RedemptionStatus status, long count) {
        return new StatusCountProjection() {
            @Override public RedemptionStatus getStatus() { return status; }
            @Override public Long getCount()              { return count; }
        };
    }

    // ── exportUnredeemedBalances ──────────────────────────────────────────────────

    @Test
    void export_happyPath_threeWallets_csvHasHeaderPlusThreeRows() {
        UUID userId1 = UUID.randomUUID();
        UUID userId2 = UUID.randomUUID();
        UUID userId3 = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();

        when(rewardWalletRepository.findAllByClientIdForExport(CLIENT_ID))
                .thenReturn(List.of(
                        walletProjection(userId1, "Alice Smith",  companyId,  "Acme Corp", "CASH",   500L, 100L),
                        walletProjection(userId2, "Bob Jones",    companyId,  "Acme Corp", "POINTS", 200L, 50L),
                        walletProjection(userId3, "Carol White",  null,        null,        "CASH",   300L, 0L)
                ));
        when(tenantValidator.getCurrentUserId()).thenReturn(UUID.randomUUID());

        byte[] result = service.exportUnredeemedBalances();
        String csv = new String(result, StandardCharsets.UTF_8);
        String[] lines = csv.split("\n");

        // Header + 3 data rows
        assertThat(lines).hasSize(4);
        assertThat(lines[0]).isEqualTo("userId,userName,companyId,companyName,currencyType,availableBalance,reservedBalance");

        assertThat(lines[1]).contains("Alice Smith").contains("Acme Corp").contains("CASH").contains("500").contains("100");
        assertThat(lines[2]).contains("Bob Jones").contains("POINTS").contains("200").contains("50");
        assertThat(lines[3]).contains("Carol White");
    }

    @Test
    void export_nullPartnerCompanyId_rendersEmptyCompanyIdAndIndividualCompanyName() {
        UUID userId = UUID.randomUUID();

        when(rewardWalletRepository.findAllByClientIdForExport(CLIENT_ID))
                .thenReturn(List.of(
                        walletProjection(userId, "Solo User", null, null, "CASH", 100L, 0L)
                ));
        when(tenantValidator.getCurrentUserId()).thenReturn(UUID.randomUUID());

        byte[] result = service.exportUnredeemedBalances();
        String csv = new String(result, StandardCharsets.UTF_8);
        String[] lines = csv.split("\n");

        // Data row: companyId column should be empty, companyName should be "Individual"
        String dataRow = lines[1];
        String[] fields = dataRow.split(",", -1);
        // Columns: userId, userName, companyId, companyName, currencyType, availableBalance, reservedBalance
        assertThat(fields[2]).isEmpty();        // companyId = ""
        assertThat(fields[3]).isEqualTo("Individual"); // companyName = "Individual"
    }

    @Test
    void export_zeroWallets_returnsCsvWithHeaderRowOnly() {
        when(rewardWalletRepository.findAllByClientIdForExport(CLIENT_ID))
                .thenReturn(List.of());
        when(tenantValidator.getCurrentUserId()).thenReturn(UUID.randomUUID());

        byte[] result = service.exportUnredeemedBalances();
        assertThat(result).isNotEmpty();

        String csv = new String(result, StandardCharsets.UTF_8);
        String[] lines = csv.split("\n");

        assertThat(lines).hasSize(1);
        assertThat(lines[0]).startsWith("userId,");
    }

    @Test
    void export_columnOrderAndDelimiter_matchSpec() {
        UUID userId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();

        when(rewardWalletRepository.findAllByClientIdForExport(CLIENT_ID))
                .thenReturn(List.of(
                        walletProjection(userId, "Test User", companyId, "Corp", "TICKETS", 999L, 1L)
                ));
        when(tenantValidator.getCurrentUserId()).thenReturn(UUID.randomUUID());

        byte[] result = service.exportUnredeemedBalances();
        String csv = new String(result, StandardCharsets.UTF_8);
        String[] lines = csv.split("\n");

        assertThat(lines[0]).isEqualTo(
                "userId,userName,companyId,companyName,currencyType,availableBalance,reservedBalance");

        String dataRow = lines[1];
        String[] fields = dataRow.split(",", -1);
        assertThat(fields).hasSize(7);
        assertThat(fields[0]).isEqualTo(userId.toString());
        assertThat(fields[1]).isEqualTo("Test User");
        assertThat(fields[2]).isEqualTo(companyId.toString());
        assertThat(fields[3]).isEqualTo("Corp");
        assertThat(fields[4]).isEqualTo("TICKETS");
        assertThat(fields[5]).isEqualTo("999");
        assertThat(fields[6]).isEqualTo("1");
    }

    // ── Export test helpers ────────────────────────────────────────────────────────

    private RewardWalletExportProjection walletProjection(UUID userId, String userName,
                                                           UUID companyId, String companyName,
                                                           String currencyType,
                                                           long available, long reserved) {
        return new RewardWalletExportProjection() {
            @Override public UUID getUserId()              { return userId; }
            @Override public String getUserName()          { return userName; }
            @Override public UUID getCompanyId()           { return companyId; }
            @Override public String getCompanyName()       { return companyName; }
            @Override public String getCurrencyType()      { return currencyType; }
            @Override public BigDecimal getAvailableBalance() { return BigDecimal.valueOf(available); }
            @Override public BigDecimal getReservedBalance()  { return BigDecimal.valueOf(reserved); }
        };
    }
}
