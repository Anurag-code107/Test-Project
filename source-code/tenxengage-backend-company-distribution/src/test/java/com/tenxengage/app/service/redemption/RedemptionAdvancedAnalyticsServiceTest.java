package com.tenxengage.app.service.redemption;

import com.tenxengage.app.dto.request.redemption.AdvancedAnalyticsFilter;
import com.tenxengage.app.dto.response.redemption.AnalyticsRefreshStatusResponse;
import com.tenxengage.app.dto.response.redemption.FailureBreakdownResponse;
import com.tenxengage.app.dto.response.redemption.FailureModeDto;
import com.tenxengage.app.dto.response.redemption.ItemBreakdownResponse;
import com.tenxengage.app.dto.response.redemption.ItemRedemptionDto;
import com.tenxengage.app.dto.response.redemption.LiabilityDataPointDto;
import com.tenxengage.app.dto.response.redemption.LiabilityTrendResponse;
import com.tenxengage.app.dto.response.redemption.RedemptionTrendResponse;
import com.tenxengage.app.dto.response.redemption.RegionTimeToRedemptionDto;
import com.tenxengage.app.dto.response.redemption.SegmentBreakdownResponse;
import com.tenxengage.app.dto.response.redemption.SegmentRedemptionDto;
import com.tenxengage.app.dto.response.redemption.TimeToFirstRedemptionResponse;
import com.tenxengage.app.dto.response.redemption.TrendDataPointDto;
import com.tenxengage.app.entity.enums.AuditAction;
import com.tenxengage.app.entity.enums.AuditResourceType;
import com.tenxengage.app.exception.BusinessRuleException;
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
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RedemptionAdvancedAnalyticsServiceTest {

    @Mock private NamedParameterJdbcTemplate namedJdbc;
    @Mock private TenantValidator tenantValidator;
    @Mock private FeatureFlagService featureFlagService;
    @Mock private AuditLogService auditLogService;

    @InjectMocks
    private RedemptionAdvancedAnalyticsService service;

    private static final UUID CLIENT_ID = UUID.fromString("b0000000-0000-0000-0000-000000000002");
    private static final UUID USER_ID   = UUID.fromString("c0000000-0000-0000-0000-000000000003");
    private static final String FEATURE_FLAG_KEY = "redemption_analytics_advanced";
    private static final int EXPECTED_MV_COUNT = AnalyticsMvRefreshScheduler.MV_NAMES.size();

    @BeforeEach
    void setUp() {
        when(tenantValidator.getCurrentClientId()).thenReturn(CLIENT_ID);
        when(tenantValidator.getCurrentUserId()).thenReturn(USER_ID);
    }

    // ── Feature-flag gate ──────────────────────────────────────────────────────

    @Test
    void getRefreshStatus_throws403_whenFeatureFlagDisabled() {
        when(featureFlagService.getEnabledFeatures(CLIENT_ID)).thenReturn(List.of());

        assertThatThrownBy(() -> service.getRefreshStatus())
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Advanced analytics is not available for your account.");
    }

    // ── getRefreshStatus — empty log table (first deploy) ─────────────────────

    @Test
    void getRefreshStatus_emptyLogTable_isStaleTrue_lastRefreshedAtNull() {
        enableFeatureFlag();
        stubRefreshLog(null, 0);

        AnalyticsRefreshStatusResponse response = service.getRefreshStatus();

        assertThat(response.isStale()).isTrue();
        assertThat(response.lastRefreshedAt()).isNull();
        assertThat(response.stalenessThresholdHours()).isEqualTo(4);
    }

    // ── getRefreshStatus — partial refresh (fewer rows than expected) → stale ─

    @Test
    void getRefreshStatus_partialRefresh_isStaleTrue() {
        enableFeatureFlag();
        // 4 out of 5 MVs refreshed 1 hour ago — one MV row is missing
        Instant oneHourAgo = Instant.now().minus(1, ChronoUnit.HOURS);
        stubRefreshLog(oneHourAgo, EXPECTED_MV_COUNT - 1);

        AnalyticsRefreshStatusResponse response = service.getRefreshStatus();

        assertThat(response.isStale()).isTrue();
        assertThat(response.lastRefreshedAt()).isNull();
        assertThat(response.stalenessThresholdHours()).isEqualTo(4);
    }

    // ── getRefreshStatus — last_refreshed_at = NOW()-5h → isStale=true ────────

    @Test
    void getRefreshStatus_lastRefreshedAt5hAgo_isStaleTrue() {
        enableFeatureFlag();
        Instant fiveHoursAgo = Instant.now().minus(5, ChronoUnit.HOURS);
        stubRefreshLog(fiveHoursAgo, EXPECTED_MV_COUNT);

        AnalyticsRefreshStatusResponse response = service.getRefreshStatus();

        assertThat(response.isStale()).isTrue();
        assertThat(response.lastRefreshedAt()).isEqualTo(fiveHoursAgo);
        assertThat(response.stalenessThresholdHours()).isEqualTo(4);
    }

    // ── getRefreshStatus — last_refreshed_at = NOW()-1h → isStale=false ───────

    @Test
    void getRefreshStatus_lastRefreshedAt1hAgo_isStaleFalse() {
        enableFeatureFlag();
        Instant oneHourAgo = Instant.now().minus(1, ChronoUnit.HOURS);
        stubRefreshLog(oneHourAgo, EXPECTED_MV_COUNT);

        AnalyticsRefreshStatusResponse response = service.getRefreshStatus();

        assertThat(response.isStale()).isFalse();
        assertThat(response.lastRefreshedAt()).isEqualTo(oneHourAgo);
        assertThat(response.stalenessThresholdHours()).isEqualTo(4);
    }

    // ── getItemBreakdown — happy path: returns sorted list ────────────────────

    @Test
    void getItemBreakdown_happyPath_returnsSortedItems() {
        enableFeatureFlag();
        LocalDate dateFrom = LocalDate.of(2026, 1, 1);
        LocalDate dateTo   = LocalDate.of(2026, 1, 31);
        AdvancedAnalyticsFilter filter = new AdvancedAnalyticsFilter(dateFrom, dateTo, null);

        ItemRedemptionDto item1 = new ItemRedemptionDto(
                UUID.randomUUID().toString(), "Gold Ring", "POINTS",
                150L, new BigDecimal("1500.00"), 75.0);
        ItemRedemptionDto item2 = new ItemRedemptionDto(
                UUID.randomUUID().toString(), "Silver Coin", "POINTS",
                75L, new BigDecimal("750.00"), 50.0);
        // first call = item breakdown query; second call = refresh log min timestamp
        stubItemBreakdownQuery(List.of(item1, item2));

        ItemBreakdownResponse response = service.getItemBreakdown(filter);

        assertThat(response.items()).hasSize(2);
        assertThat(response.items().get(0).totalRedeemedCount()).isEqualTo(150L);
        assertThat(response.items().get(1).totalRedeemedCount()).isEqualTo(75L);
        assertThat(response.dateWindow().from()).isEqualTo(dateFrom);
        assertThat(response.dateWindow().to()).isEqualTo(dateTo);
    }

    // ── getItemBreakdown — span > 365 days → BusinessRuleException (422) ──────

    @Test
    void getItemBreakdown_spanExceeds365Days_throwsBusinessRuleException() {
        enableFeatureFlag();
        LocalDate dateFrom = LocalDate.of(2024, 1, 1);
        LocalDate dateTo   = LocalDate.of(2026, 1, 1); // 731 days
        AdvancedAnalyticsFilter filter = new AdvancedAnalyticsFilter(dateFrom, dateTo, null);

        assertThatThrownBy(() -> service.getItemBreakdown(filter))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("365 days");
    }

    // ── getItemBreakdown — dateFrom after dateTo → BusinessRuleException (422) ─

    @Test
    void getItemBreakdown_dateFromAfterDateTo_throwsBusinessRuleException() {
        enableFeatureFlag();
        LocalDate dateFrom = LocalDate.of(2026, 6, 10);
        LocalDate dateTo   = LocalDate.of(2026, 6, 1);
        AdvancedAnalyticsFilter filter = new AdvancedAnalyticsFilter(dateFrom, dateTo, null);

        assertThatThrownBy(() -> service.getItemBreakdown(filter))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("dateFrom must not be after dateTo");
    }

    // ── getItemBreakdown — region filter applied ───────────────────────────────

    @Test
    void getItemBreakdown_withRegionFilter_returnsRegionScopedItems() {
        enableFeatureFlag();
        LocalDate dateFrom = LocalDate.of(2026, 1, 1);
        LocalDate dateTo   = LocalDate.of(2026, 1, 31);
        AdvancedAnalyticsFilter filter = new AdvancedAnalyticsFilter(dateFrom, dateTo, "APAC");

        ItemRedemptionDto apacItem = new ItemRedemptionDto(
                UUID.randomUUID().toString(), "APAC Item", "POINTS",
                100L, new BigDecimal("1000.00"), 80.0);
        stubItemBreakdownQuery(List.of(apacItem));

        ItemBreakdownResponse response = service.getItemBreakdown(filter);

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).catalogItemName()).isEqualTo("APAC Item");
    }

    // ── getItemBreakdown — empty result → empty items list ─────────────────────

    @Test
    void getItemBreakdown_noData_returnsEmptyItemsList() {
        enableFeatureFlag();
        LocalDate dateFrom = LocalDate.of(2026, 1, 1);
        LocalDate dateTo   = LocalDate.of(2026, 1, 31);
        AdvancedAnalyticsFilter filter = new AdvancedAnalyticsFilter(dateFrom, dateTo, null);

        stubItemBreakdownQuery(Collections.emptyList());

        ItemBreakdownResponse response = service.getItemBreakdown(filter);

        assertThat(response.items()).isEmpty();
    }

    // ── getSegmentBreakdown — happy path: returns all segments ────────────────

    @Test
    void getSegmentBreakdown_happyPath_returnsSegments() {
        enableFeatureFlag();
        LocalDate dateFrom = LocalDate.of(2026, 1, 1);
        LocalDate dateTo   = LocalDate.of(2026, 1, 31);
        AdvancedAnalyticsFilter filter = new AdvancedAnalyticsFilter(dateFrom, dateTo, null, null);

        SegmentRedemptionDto seg1 = new SegmentRedemptionDto(
                "APAC", "MANAGER", "POINTS", 42L, new BigDecimal("35.00"));
        SegmentRedemptionDto seg2 = new SegmentRedemptionDto(
                "EMEA", "SELLER", "POINTS", 10L, new BigDecimal("20.00"));
        stubSegmentBreakdownQuery(List.of(seg1, seg2));

        SegmentBreakdownResponse response = service.getSegmentBreakdown(filter);

        assertThat(response.segments()).hasSize(2);
        assertThat(response.segments().get(0).region()).isEqualTo("APAC");
        assertThat(response.segments().get(0).totalRedeemedCount()).isEqualTo(42L);
        assertThat(response.dateWindow().from()).isEqualTo(dateFrom);
        assertThat(response.dateWindow().to()).isEqualTo(dateTo);
    }

    // ── getSegmentBreakdown — region=APAC filter constrains results ───────────

    @Test
    void getSegmentBreakdown_withRegionFilter_returnsOnlyApacSegments() {
        enableFeatureFlag();
        LocalDate dateFrom = LocalDate.of(2026, 1, 1);
        LocalDate dateTo   = LocalDate.of(2026, 1, 31);
        AdvancedAnalyticsFilter filter = new AdvancedAnalyticsFilter(dateFrom, dateTo, "APAC", null);

        SegmentRedemptionDto apacSeg = new SegmentRedemptionDto(
                "APAC", "MANAGER", "POINTS", 42L, new BigDecimal("35.00"));
        stubSegmentBreakdownQuery(List.of(apacSeg));

        SegmentBreakdownResponse response = service.getSegmentBreakdown(filter);

        assertThat(response.segments()).hasSize(1);
        assertThat(response.segments().get(0).region()).isEqualTo("APAC");
    }

    // ── getSegmentBreakdown — role filter constrains results ──────────────────

    @Test
    void getSegmentBreakdown_withRoleFilter_returnsOnlyManagerSegments() {
        enableFeatureFlag();
        LocalDate dateFrom = LocalDate.of(2026, 1, 1);
        LocalDate dateTo   = LocalDate.of(2026, 1, 31);
        AdvancedAnalyticsFilter filter = new AdvancedAnalyticsFilter(dateFrom, dateTo, null, "MANAGER");

        SegmentRedemptionDto managerSeg = new SegmentRedemptionDto(
                "APAC", "MANAGER", "POINTS", 30L, new BigDecimal("0.6000"));
        stubSegmentBreakdownQuery(List.of(managerSeg));

        SegmentBreakdownResponse response = service.getSegmentBreakdown(filter);

        assertThat(response.segments()).hasSize(1);
        assertThat(response.segments().get(0).role()).isEqualTo("MANAGER");
    }

    // ── getSegmentBreakdown — empty result → empty segments list ──────────────

    @Test
    void getSegmentBreakdown_noData_returnsEmptySegmentsList() {
        enableFeatureFlag();
        LocalDate dateFrom = LocalDate.of(2026, 1, 1);
        LocalDate dateTo   = LocalDate.of(2026, 1, 31);
        AdvancedAnalyticsFilter filter = new AdvancedAnalyticsFilter(dateFrom, dateTo, null, null);

        stubSegmentBreakdownQuery(Collections.emptyList());

        SegmentBreakdownResponse response = service.getSegmentBreakdown(filter);

        assertThat(response.segments()).isEmpty();
    }

    // ── getSegmentBreakdown — dateFrom after dateTo → BusinessRuleException (422) ─

    @Test
    void getSegmentBreakdown_dateFromAfterDateTo_throwsBusinessRuleException() {
        enableFeatureFlag();
        LocalDate dateFrom = LocalDate.of(2026, 6, 10);
        LocalDate dateTo   = LocalDate.of(2026, 6, 1);
        AdvancedAnalyticsFilter filter = new AdvancedAnalyticsFilter(dateFrom, dateTo, null, null);

        assertThatThrownBy(() -> service.getSegmentBreakdown(filter))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("dateFrom must not be after dateTo");
    }

    // ── getSegmentBreakdown — span > 365 days → BusinessRuleException (422) ──

    @Test
    void getSegmentBreakdown_spanExceeds365Days_throwsBusinessRuleException() {
        enableFeatureFlag();
        LocalDate dateFrom = LocalDate.of(2024, 1, 1);
        LocalDate dateTo   = LocalDate.of(2026, 1, 1); // 731 days
        AdvancedAnalyticsFilter filter = new AdvancedAnalyticsFilter(dateFrom, dateTo, null, null);

        assertThatThrownBy(() -> service.getSegmentBreakdown(filter))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("365 days");
    }

    // ── getTimeToFirstRedemption — happy path: mixed null/non-null avg ────────

    @Test
    void getTimeToFirstRedemption_happyPath_returnsMixedNullAndNonNullAvg() {
        enableFeatureFlag();
        LocalDate dateFrom = LocalDate.of(2026, 1, 1);
        LocalDate dateTo   = LocalDate.of(2026, 1, 31);
        AdvancedAnalyticsFilter filter = new AdvancedAnalyticsFilter(dateFrom, dateTo, null);

        RegionTimeToRedemptionDto apacRow = new RegionTimeToRedemptionDto(
                "APAC", new BigDecimal("24.5"), null, 120L);
        RegionTimeToRedemptionDto emeaRow = new RegionTimeToRedemptionDto(
                "EMEA", null, null, 0L);
        stubTtfrQuery(List.of(apacRow, emeaRow));

        TimeToFirstRedemptionResponse response = service.getTimeToFirstRedemption(filter);

        assertThat(response.regions()).hasSize(2);
        assertThat(response.regions().get(0).region()).isEqualTo("APAC");
        assertThat(response.regions().get(0).avgHoursToFirstRedemption())
                .isEqualByComparingTo(new BigDecimal("24.5"));
        assertThat(response.regions().get(1).region()).isEqualTo("EMEA");
        assertThat(response.regions().get(1).avgHoursToFirstRedemption()).isNull();
        assertThat(response.filters()).isEmpty();
    }

    // ── getTimeToFirstRedemption — sampleCount=0 row → avgHours=null ─────────

    @Test
    void getTimeToFirstRedemption_sampleCountZero_avgAndMedianAreNull() {
        enableFeatureFlag();
        LocalDate dateFrom = LocalDate.of(2026, 1, 1);
        LocalDate dateTo   = LocalDate.of(2026, 1, 31);
        AdvancedAnalyticsFilter filter = new AdvancedAnalyticsFilter(dateFrom, dateTo, null);

        RegionTimeToRedemptionDto zeroRow = new RegionTimeToRedemptionDto(
                "LATAM", null, null, 0L);
        stubTtfrQuery(List.of(zeroRow));

        TimeToFirstRedemptionResponse response = service.getTimeToFirstRedemption(filter);

        assertThat(response.regions()).hasSize(1);
        RegionTimeToRedemptionDto row = response.regions().get(0);
        assertThat(row.sampleCount()).isZero();
        assertThat(row.avgHoursToFirstRedemption()).isNull();
        assertThat(row.medianHoursToFirstRedemption()).isNull();
    }

    // ── getTimeToFirstRedemption — region filter applied ─────────────────────

    @Test
    void getTimeToFirstRedemption_withRegionFilter_filtersMapPopulated() {
        enableFeatureFlag();
        LocalDate dateFrom = LocalDate.of(2026, 1, 1);
        LocalDate dateTo   = LocalDate.of(2026, 1, 31);
        AdvancedAnalyticsFilter filter = new AdvancedAnalyticsFilter(dateFrom, dateTo, "APAC");

        RegionTimeToRedemptionDto apacRow = new RegionTimeToRedemptionDto(
                "APAC", new BigDecimal("18.0"), null, 50L);
        stubTtfrQuery(List.of(apacRow));

        TimeToFirstRedemptionResponse response = service.getTimeToFirstRedemption(filter);

        assertThat(response.regions()).hasSize(1);
        assertThat(response.regions().get(0).region()).isEqualTo("APAC");
        assertThat(response.filters()).containsEntry("region", "APAC");
    }

    // ── getTimeToFirstRedemption — span > 365 days → BusinessRuleException ───

    @Test
    void getTimeToFirstRedemption_spanExceeds365Days_throwsBusinessRuleException() {
        enableFeatureFlag();
        LocalDate dateFrom = LocalDate.of(2024, 1, 1);
        LocalDate dateTo   = LocalDate.of(2026, 1, 1); // 731 days
        AdvancedAnalyticsFilter filter = new AdvancedAnalyticsFilter(dateFrom, dateTo, null);

        assertThatThrownBy(() -> service.getTimeToFirstRedemption(filter))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("365 days");
    }

    // ── getTimeToFirstRedemption — dateFrom after dateTo → BusinessRuleException

    @Test
    void getTimeToFirstRedemption_dateFromAfterDateTo_throwsBusinessRuleException() {
        enableFeatureFlag();
        LocalDate dateFrom = LocalDate.of(2026, 6, 10);
        LocalDate dateTo   = LocalDate.of(2026, 6, 1);
        AdvancedAnalyticsFilter filter = new AdvancedAnalyticsFilter(dateFrom, dateTo, null);

        assertThatThrownBy(() -> service.getTimeToFirstRedemption(filter))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("dateFrom must not be after dateTo");
    }

    // ── getTimeToFirstRedemption — feature flag disabled → AccessDeniedException

    @Test
    void getTimeToFirstRedemption_featureFlagDisabled_throwsAccessDeniedException() {
        when(featureFlagService.getEnabledFeatures(CLIENT_ID)).thenReturn(List.of());
        AdvancedAnalyticsFilter filter = new AdvancedAnalyticsFilter(
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31), null);

        assertThatThrownBy(() -> service.getTimeToFirstRedemption(filter))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }

    // ── getRedemptionTrend — happy path: returns data points ordered ASC ──────

    @Test
    void getRedemptionTrend_happyPath_returnsDataPointsOrderedAsc() {
        enableFeatureFlag();
        LocalDate dateFrom = LocalDate.of(2026, 5, 1);
        LocalDate dateTo   = LocalDate.of(2026, 5, 7);

        TrendDataPointDto point1 = new TrendDataPointDto(
                LocalDate.of(2026, 5, 1), "POINTS", 10L, 10.0);
        TrendDataPointDto point2 = new TrendDataPointDto(
                LocalDate.of(2026, 5, 2), "POINTS", 15L, 15.0);
        TrendDataPointDto point3 = new TrendDataPointDto(
                LocalDate.of(2026, 5, 2), "CREDITS", 5L, 5.0);
        stubTrendQuery(List.of(point1, point2, point3));

        RedemptionTrendResponse response = service.getRedemptionTrend(dateFrom, dateTo);

        assertThat(response.dataPoints()).hasSize(3);
        // DB query orders ASC — verify the returned order is preserved by the service
        assertThat(response.dataPoints().get(0).periodDate()).isEqualTo(LocalDate.of(2026, 5, 1));
        assertThat(response.dataPoints().get(0).currencyId()).isEqualTo("POINTS");
        assertThat(response.dataPoints().get(0).redeemedCount()).isEqualTo(10L);
        assertThat(response.dataPoints().get(1).periodDate()).isEqualTo(LocalDate.of(2026, 5, 2));
        assertThat(response.dataPoints().get(1).currencyId()).isEqualTo("POINTS");
        assertThat(response.dataPoints().get(2).currencyId()).isEqualTo("CREDITS");
        assertThat(response.dateWindow().from()).isEqualTo(dateFrom);
        assertThat(response.dateWindow().to()).isEqualTo(dateTo);
    }

    // ── getRedemptionTrend — span > 365 days → BusinessRuleException (422) ───

    @Test
    void getRedemptionTrend_spanExceeds365Days_throwsBusinessRuleException() {
        enableFeatureFlag();
        LocalDate dateFrom = LocalDate.of(2024, 1, 1);
        LocalDate dateTo   = LocalDate.of(2026, 1, 1); // 731 days

        assertThatThrownBy(() -> service.getRedemptionTrend(dateFrom, dateTo))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("365 days");
    }

    // ── getRedemptionTrend — empty result → empty dataPoints list ─────────────

    @Test
    void getRedemptionTrend_noData_returnsEmptyDataPointsList() {
        enableFeatureFlag();
        LocalDate dateFrom = LocalDate.of(2026, 5, 1);
        LocalDate dateTo   = LocalDate.of(2026, 5, 7);
        stubTrendQuery(Collections.emptyList());

        RedemptionTrendResponse response = service.getRedemptionTrend(dateFrom, dateTo);

        assertThat(response.dataPoints()).isEmpty();
        assertThat(response.dateWindow().from()).isEqualTo(dateFrom);
        assertThat(response.dateWindow().to()).isEqualTo(dateTo);
    }

    // ── getRedemptionTrend — span exactly 365 days → allowed ──────────────────

    @Test
    void getRedemptionTrend_span365Days_isAllowed() {
        enableFeatureFlag();
        LocalDate dateFrom = LocalDate.of(2025, 6, 22);
        LocalDate dateTo   = LocalDate.of(2026, 6, 22); // exactly 365 days
        stubTrendQuery(Collections.emptyList());

        // must not throw
        RedemptionTrendResponse response = service.getRedemptionTrend(dateFrom, dateTo);

        assertThat(response.dataPoints()).isEmpty();
    }

    // ── getRedemptionTrend — feature flag disabled → AccessDeniedException ────

    @Test
    void getRedemptionTrend_featureFlagDisabled_throwsAccessDeniedException() {
        when(featureFlagService.getEnabledFeatures(CLIENT_ID)).thenReturn(List.of());

        assertThatThrownBy(() -> service.getRedemptionTrend(
                LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 7)))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // getLiabilityTrend (FR-08.5, AC-1, AC-5)
    // ═══════════════════════════════════════════════════════════════════════════

    // ── happy path: returns data points ordered ASC ───────────────────────────

    @Test
    void getLiabilityTrend_happyPath_returnsDataPointsOrderedAsc() {
        enableFeatureFlag();
        LocalDate dateFrom = LocalDate.of(2026, 1, 1);
        LocalDate dateTo   = LocalDate.of(2026, 1, 7);

        LiabilityDataPointDto point1 = new LiabilityDataPointDto(
                LocalDate.of(2026, 1, 1), "POINTS", new BigDecimal("1200.50"));
        LiabilityDataPointDto point2 = new LiabilityDataPointDto(
                LocalDate.of(2026, 1, 2), "POINTS", new BigDecimal("1150.00"));
        LiabilityDataPointDto point3 = new LiabilityDataPointDto(
                LocalDate.of(2026, 1, 2), "CREDITS", new BigDecimal("300.00"));
        stubLiabilityTrendQuery(List.of(point1, point2, point3));

        LiabilityTrendResponse response = service.getLiabilityTrend(dateFrom, dateTo);

        assertThat(response.dataPoints()).hasSize(3);
        assertThat(response.dataPoints().get(0).periodDate()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(response.dataPoints().get(0).currencyId()).isEqualTo("POINTS");
        assertThat(response.dataPoints().get(0).totalUnredeemedBalance())
                .isEqualByComparingTo(new BigDecimal("1200.50"));
        assertThat(response.dataPoints().get(1).periodDate()).isEqualTo(LocalDate.of(2026, 1, 2));
        assertThat(response.dataPoints().get(1).currencyId()).isEqualTo("POINTS");
        assertThat(response.dataPoints().get(2).currencyId()).isEqualTo("CREDITS");
        assertThat(response.dateWindow().from()).isEqualTo(dateFrom);
        assertThat(response.dateWindow().to()).isEqualTo(dateTo);
    }

    // ── span > 365 days → BusinessRuleException (422) ────────────────────────

    @Test
    void getLiabilityTrend_spanExceeds365Days_throwsBusinessRuleException() {
        enableFeatureFlag();
        LocalDate dateFrom = LocalDate.of(2024, 1, 1);
        LocalDate dateTo   = LocalDate.of(2026, 1, 1); // 731 days

        assertThatThrownBy(() -> service.getLiabilityTrend(dateFrom, dateTo))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("365 days");
    }

    // ── empty result → empty dataPoints list ──────────────────────────────────

    @Test
    void getLiabilityTrend_noData_returnsEmptyDataPointsList() {
        enableFeatureFlag();
        LocalDate dateFrom = LocalDate.of(2026, 1, 1);
        LocalDate dateTo   = LocalDate.of(2026, 1, 31);
        stubLiabilityTrendQuery(Collections.emptyList());

        LiabilityTrendResponse response = service.getLiabilityTrend(dateFrom, dateTo);

        assertThat(response.dataPoints()).isEmpty();
        assertThat(response.dateWindow().from()).isEqualTo(dateFrom);
        assertThat(response.dateWindow().to()).isEqualTo(dateTo);
    }

    // ── feature flag disabled → AccessDeniedException ─────────────────────────

    @Test
    void getLiabilityTrend_featureFlagDisabled_throwsAccessDeniedException() {
        when(featureFlagService.getEnabledFeatures(CLIENT_ID)).thenReturn(List.of());

        assertThatThrownBy(() -> service.getLiabilityTrend(
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31)))
                .isInstanceOf(AccessDeniedException.class);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // exportLiabilityTrend (FR-08.5, FR-08.10, AC-2, AC-3)
    // ═══════════════════════════════════════════════════════════════════════════

    // ── CSV header is always present ──────────────────────────────────────────

    @Test
    void exportLiabilityTrend_csvHeaderAlwaysPresent() {
        enableFeatureFlag();
        LocalDate dateFrom = LocalDate.of(2026, 1, 1);
        LocalDate dateTo   = LocalDate.of(2026, 1, 31);
        stubExportLiabilityTrendQuery(Collections.emptyList());

        byte[] result = service.exportLiabilityTrend(dateFrom, dateTo);

        String csv = new String(result, StandardCharsets.UTF_8);
        assertThat(csv).startsWith("period_date,currency_type,total_unredeemed_balance\n");
    }

    // ── data rows match the queried data points ────────────────────────────────

    @Test
    void exportLiabilityTrend_dataRowsCorrect() {
        enableFeatureFlag();
        LocalDate dateFrom = LocalDate.of(2026, 1, 1);
        LocalDate dateTo   = LocalDate.of(2026, 1, 7);

        LiabilityDataPointDto dp1 = new LiabilityDataPointDto(
                LocalDate.of(2026, 1, 1), "POINTS", new BigDecimal("1200.5000"));
        LiabilityDataPointDto dp2 = new LiabilityDataPointDto(
                LocalDate.of(2026, 1, 2), "CREDITS", new BigDecimal("300.0000"));
        stubExportLiabilityTrendQuery(List.of(dp1, dp2));

        byte[] result = service.exportLiabilityTrend(dateFrom, dateTo);

        String csv = new String(result, StandardCharsets.UTF_8);
        String[] lines = csv.split("\n");
        assertThat(lines).hasSize(3); // header + 2 data rows
        assertThat(lines[1]).isEqualTo("2026-01-01,POINTS,1200.5000");
        assertThat(lines[2]).isEqualTo("2026-01-02,CREDITS,300.0000");
    }

    // ── escapeCsv applied to currencyId field ─────────────────────────────────

    @Test
    void exportLiabilityTrend_escapeCsvApplied_formulaInjectionNeutralised() {
        enableFeatureFlag();
        LocalDate dateFrom = LocalDate.of(2026, 1, 1);
        LocalDate dateTo   = LocalDate.of(2026, 1, 7);

        // Currency starting with "=" is a potential formula-injection payload
        LiabilityDataPointDto dp = new LiabilityDataPointDto(
                LocalDate.of(2026, 1, 1), "=DANGER", new BigDecimal("100.00"));
        stubExportLiabilityTrendQuery(List.of(dp));

        byte[] result = service.exportLiabilityTrend(dateFrom, dateTo);

        String csv = new String(result, StandardCharsets.UTF_8);
        // escapeCsv prefixes "=" values with a single quote to neutralise injection
        assertThat(csv).contains("'=DANGER");
        assertThat(csv).doesNotContain(",=DANGER");
    }

    // ── auditLogService.logAsync() called AFTER CSV built (AC-3, BE-4) ────────

    @Test
    void exportLiabilityTrend_auditCalledAfterCsvBuilt() {
        enableFeatureFlag();
        LocalDate dateFrom = LocalDate.of(2026, 1, 1);
        LocalDate dateTo   = LocalDate.of(2026, 1, 31);
        stubExportLiabilityTrendQuery(Collections.emptyList());

        service.exportLiabilityTrend(dateFrom, dateTo);

        verify(auditLogService, times(1)).logAsync(
                eq(AuditAction.DATA_EXPORTED),
                eq(AuditResourceType.REDEMPTION_ADVANCED_ANALYTICS_EXPORT),
                any(),
                any(),
                any(),
                any(Map.class)
        );
    }

    // ── audit NOT called when span > 365 throws before CSV built (AC-3, BE-4) ─

    @Test
    void exportLiabilityTrend_auditNotCalledOnSpanViolation() {
        enableFeatureFlag();
        LocalDate dateFrom = LocalDate.of(2024, 1, 1);
        LocalDate dateTo   = LocalDate.of(2026, 1, 1); // 731 days

        assertThatThrownBy(() -> service.exportLiabilityTrend(dateFrom, dateTo))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("365 days");

        verify(auditLogService, never()).logAsync(
                any(AuditAction.class),
                any(AuditResourceType.class),
                any(),
                any(),
                any(),
                any()
        );
    }

    // ── span exactly 365 days → allowed ───────────────────────────────────────

    @Test
    void exportLiabilityTrend_span365Days_isAllowed() {
        enableFeatureFlag();
        LocalDate dateFrom = LocalDate.of(2025, 6, 22);
        LocalDate dateTo   = LocalDate.of(2026, 6, 22); // exactly 365 days
        stubExportLiabilityTrendQuery(Collections.emptyList());

        byte[] result = service.exportLiabilityTrend(dateFrom, dateTo);

        assertThat(result).isNotNull();
        String csv = new String(result, StandardCharsets.UTF_8);
        assertThat(csv).startsWith("period_date,currency_type,total_unredeemed_balance\n");
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private void enableFeatureFlag() {
        when(featureFlagService.getEnabledFeatures(CLIENT_ID))
                .thenReturn(List.of(FEATURE_FLAG_KEY));
    }

    @SuppressWarnings("unchecked")
    private void stubRefreshLog(Instant minLastRefreshedAt, int mvCount) {
        RedemptionAdvancedAnalyticsService.RefreshLogRow row =
                new RedemptionAdvancedAnalyticsService.RefreshLogRow(minLastRefreshedAt, mvCount);
        when(namedJdbc.query(anyString(), any(SqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of(row));
    }

    /**
     * Stubs {@code namedJdbc.query()} calls for the item breakdown flow.
     *
     * <p>The service issues two queries in sequence:
     * <ol>
     *   <li>Item breakdown query → returns the given items list.</li>
     *   <li>Refresh log min-timestamp query → returns a list with one null element
     *       (simulating an empty {@code analytics_mv_refresh_log} table).</li>
     * </ol>
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void stubItemBreakdownQuery(List<ItemRedemptionDto> items) {
        List<Instant> refreshLogResult = Collections.singletonList(null);
        when(namedJdbc.query(anyString(), any(SqlParameterSource.class), any(RowMapper.class)))
                .thenReturn((List) items)              // 1st call: item breakdown
                .thenReturn((List) refreshLogResult);  // 2nd call: min lastRefreshedAt
    }

    /**
     * Stubs {@code namedJdbc.query()} calls for the segment breakdown flow.
     *
     * <p>The service issues two queries in sequence:
     * <ol>
     *   <li>Segment breakdown query → returns the given segments list.</li>
     *   <li>Refresh log min-timestamp query → returns a list with one null element
     *       (simulating an empty {@code analytics_mv_refresh_log} table).</li>
     * </ol>
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void stubSegmentBreakdownQuery(List<SegmentRedemptionDto> segments) {
        List<Instant> refreshLogResult = Collections.singletonList(null);
        when(namedJdbc.query(anyString(), any(SqlParameterSource.class), any(RowMapper.class)))
                .thenReturn((List) segments)           // 1st call: segment breakdown
                .thenReturn((List) refreshLogResult);  // 2nd call: min lastRefreshedAt
    }

    /**
     * Stubs {@code namedJdbc.query()} calls for the time-to-first-redemption flow.
     *
     * <p>The service issues two queries in sequence:
     * <ol>
     *   <li>TTFR query → returns the given region rows list.</li>
     *   <li>Refresh log min-timestamp query → returns a list with one null element
     *       (simulating an empty {@code analytics_mv_refresh_log} table).</li>
     * </ol>
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void stubTtfrQuery(List<RegionTimeToRedemptionDto> regions) {
        List<Instant> refreshLogResult = Collections.singletonList(null);
        when(namedJdbc.query(anyString(), any(SqlParameterSource.class), any(RowMapper.class)))
                .thenReturn((List) regions)            // 1st call: TTFR query
                .thenReturn((List) refreshLogResult);  // 2nd call: min lastRefreshedAt
    }

    /**
     * Stubs {@code namedJdbc.query()} calls for the redemption trend flow.
     *
     * <p>The service issues two queries in sequence:
     * <ol>
     *   <li>Trend query → returns the given data points list.</li>
     *   <li>Refresh log min-timestamp query → returns a list with one null element
     *       (simulating an empty {@code analytics_mv_refresh_log} table).</li>
     * </ol>
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void stubTrendQuery(List<TrendDataPointDto> dataPoints) {
        List<Instant> refreshLogResult = Collections.singletonList(null);
        when(namedJdbc.query(anyString(), any(SqlParameterSource.class), any(RowMapper.class)))
                .thenReturn((List) dataPoints)         // 1st call: trend query
                .thenReturn((List) refreshLogResult);  // 2nd call: min lastRefreshedAt
    }

    /**
     * Stubs {@code namedJdbc.query()} calls for the liability trend JSON flow.
     *
     * <p>The service issues two queries in sequence:
     * <ol>
     *   <li>Liability trend query → returns the given data points list.</li>
     *   <li>Refresh log min-timestamp query → returns a list with one null element
     *       (simulating an empty {@code analytics_mv_refresh_log} table).</li>
     * </ol>
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void stubLiabilityTrendQuery(List<LiabilityDataPointDto> dataPoints) {
        List<Instant> refreshLogResult = Collections.singletonList(null);
        when(namedJdbc.query(anyString(), any(SqlParameterSource.class), any(RowMapper.class)))
                .thenReturn((List) dataPoints)         // 1st call: liability trend query
                .thenReturn((List) refreshLogResult);  // 2nd call: min lastRefreshedAt
    }

    /**
     * Stubs {@code namedJdbc.query()} for the export path.
     *
     * <p>The export service method issues only the liability trend query (no refresh-log
     * query — export is uncached and does not include {@code lastRefreshedAt} in the
     * response).  A single stub return is sufficient.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void stubExportLiabilityTrendQuery(List<LiabilityDataPointDto> dataPoints) {
        when(namedJdbc.query(anyString(), any(SqlParameterSource.class), any(RowMapper.class)))
                .thenReturn((List) dataPoints);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // getFailureBreakdown (FR-08.7, US-07)
    // ═══════════════════════════════════════════════════════════════════════════

    // ── happy path: returns rows sorted by failureRate desc ───────────────────

    @Test
    void getFailureBreakdown_happyPath_returnsSortedByFailureRateDesc() {
        enableFeatureFlag();
        LocalDate dateFrom = LocalDate.of(2026, 1, 1);
        LocalDate dateTo   = LocalDate.of(2026, 1, 31);
        AdvancedAnalyticsFilter filter = new AdvancedAnalyticsFilter(dateFrom, dateTo, null);

        // DB orders by failure_rate DESC — service must preserve this order without re-sorting
        FailureModeDto row1 = new FailureModeDto(
                "APPROVAL_REQUIRED", UUID.randomUUID().toString(), "Gold Ring",
                "POINTS", 30L, 5L, 100L, 35.0);
        FailureModeDto row2 = new FailureModeDto(
                "BATCH", UUID.randomUUID().toString(), "Silver Coin",
                "POINTS", 10L, 2L, 100L, 12.0);
        stubFailureBreakdownQuery(List.of(row1, row2));

        FailureBreakdownResponse response = service.getFailureBreakdown(filter);

        assertThat(response.failureModes()).hasSize(2);
        assertThat(response.failureModes().get(0).failureRate()).isEqualTo(35.0);
        assertThat(response.failureModes().get(0).processingMode()).isEqualTo("APPROVAL_REQUIRED");
        assertThat(response.failureModes().get(0).catalogItemName()).isEqualTo("Gold Ring");
        assertThat(response.failureModes().get(0).failedCount()).isEqualTo(30L);
        assertThat(response.failureModes().get(0).cancelledCount()).isEqualTo(5L);
        assertThat(response.failureModes().get(0).totalCount()).isEqualTo(100L);
        assertThat(response.failureModes().get(1).failureRate()).isEqualTo(12.0);
        assertThat(response.failureModes().get(1).processingMode()).isEqualTo("BATCH");
        assertThat(response.dateWindow().from()).isEqualTo(dateFrom);
        assertThat(response.dateWindow().to()).isEqualTo(dateTo);
    }

    // ── region filter applied ─────────────────────────────────────────────────

    @Test
    void getFailureBreakdown_withRegionFilter_returnsRegionScopedRows() {
        enableFeatureFlag();
        LocalDate dateFrom = LocalDate.of(2026, 1, 1);
        LocalDate dateTo   = LocalDate.of(2026, 1, 31);
        AdvancedAnalyticsFilter filter = new AdvancedAnalyticsFilter(dateFrom, dateTo, "APAC");

        FailureModeDto apacRow = new FailureModeDto(
                "INSTANT", UUID.randomUUID().toString(), "APAC Item",
                "CREDITS", 5L, 1L, 50L, 12.0);
        stubFailureBreakdownQuery(List.of(apacRow));

        FailureBreakdownResponse response = service.getFailureBreakdown(filter);

        assertThat(response.failureModes()).hasSize(1);
        assertThat(response.failureModes().get(0).catalogItemName()).isEqualTo("APAC Item");
    }

    // ── span > 365 days → BusinessRuleException (422) ────────────────────────

    @Test
    void getFailureBreakdown_spanExceeds365Days_throwsBusinessRuleException() {
        enableFeatureFlag();
        LocalDate dateFrom = LocalDate.of(2024, 1, 1);
        LocalDate dateTo   = LocalDate.of(2026, 1, 1); // 731 days
        AdvancedAnalyticsFilter filter = new AdvancedAnalyticsFilter(dateFrom, dateTo, null);

        assertThatThrownBy(() -> service.getFailureBreakdown(filter))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("365 days");
    }

    // ── empty result → empty failureModes list ────────────────────────────────

    @Test
    void getFailureBreakdown_noData_returnsEmptyFailureModesList() {
        enableFeatureFlag();
        LocalDate dateFrom = LocalDate.of(2026, 1, 1);
        LocalDate dateTo   = LocalDate.of(2026, 1, 31);
        AdvancedAnalyticsFilter filter = new AdvancedAnalyticsFilter(dateFrom, dateTo, null);

        stubFailureBreakdownQuery(Collections.emptyList());

        FailureBreakdownResponse response = service.getFailureBreakdown(filter);

        assertThat(response.failureModes()).isEmpty();
        assertThat(response.dateWindow().from()).isEqualTo(dateFrom);
        assertThat(response.dateWindow().to()).isEqualTo(dateTo);
    }

    // ── feature flag disabled → AccessDeniedException ────────────────────────

    @Test
    void getFailureBreakdown_featureFlagDisabled_throwsAccessDeniedException() {
        when(featureFlagService.getEnabledFeatures(CLIENT_ID)).thenReturn(List.of());
        AdvancedAnalyticsFilter filter = new AdvancedAnalyticsFilter(
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31), null);

        assertThatThrownBy(() -> service.getFailureBreakdown(filter))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }

    /**
     * Stubs {@code namedJdbc.query()} calls for the failure breakdown flow.
     *
     * <p>The service issues two queries in sequence:
     * <ol>
     *   <li>Failure breakdown query → returns the given failure mode rows list.</li>
     *   <li>Refresh log min-timestamp query → returns a list with one null element
     *       (simulating an empty {@code analytics_mv_refresh_log} table).</li>
     * </ol>
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void stubFailureBreakdownQuery(List<FailureModeDto> failureModes) {
        List<Instant> refreshLogResult = Collections.singletonList(null);
        when(namedJdbc.query(anyString(), any(SqlParameterSource.class), any(RowMapper.class)))
                .thenReturn((List) failureModes)      // 1st call: failure breakdown query
                .thenReturn((List) refreshLogResult); // 2nd call: min lastRefreshedAt
    }
}
