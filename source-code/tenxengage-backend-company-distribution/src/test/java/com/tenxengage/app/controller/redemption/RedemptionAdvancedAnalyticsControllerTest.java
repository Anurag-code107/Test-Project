package com.tenxengage.app.controller.redemption;

import com.tenxengage.app.config.SecurityConfig;
import com.tenxengage.app.dto.request.redemption.AdvancedAnalyticsFilter;
import com.tenxengage.app.dto.response.redemption.AnalyticsRefreshStatusResponse;
import com.tenxengage.app.dto.response.redemption.DateWindowDto;
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
import com.tenxengage.app.exception.BusinessRuleException;
import com.tenxengage.app.security.AnalyticsExportRateLimiter;
import com.tenxengage.app.security.CustomUserDetailsService;
import com.tenxengage.app.security.JwtTokenProvider;
import com.tenxengage.app.security.PermissionAspect;
import com.tenxengage.app.security.PermissionMetrics;
import com.tenxengage.app.security.TenantValidator;
import com.tenxengage.app.service.ClientService;
import com.tenxengage.app.service.FeatureFlagService;
import com.tenxengage.app.service.PermissionService;
import com.tenxengage.app.service.redemption.RedemptionAdvancedAnalyticsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RedemptionAdvancedAnalyticsController.class)
@Import({SecurityConfig.class, PermissionAspect.class, AopAutoConfiguration.class,
         com.tenxengage.app.config.JacksonConfig.class})
class RedemptionAdvancedAnalyticsControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockBean private RedemptionAdvancedAnalyticsService advancedAnalyticsService;
    @MockBean private PermissionService permissionService;
    @MockBean private TenantValidator tenantValidator;
    @MockBean private PermissionMetrics permissionMetrics;
    @MockBean private JwtTokenProvider jwtTokenProvider;
    @MockBean private CustomUserDetailsService customUserDetailsService;
    @MockBean private ClientService clientService;
    @MockBean private JpaMetamodelMappingContext jpaMetamodelMappingContext;
    @MockBean private AnalyticsExportRateLimiter exportRateLimiter;
    @MockBean private FeatureFlagService featureFlagService;

    private static final UUID USER_ID   = UUID.randomUUID();
    private static final UUID CLIENT_ID = UUID.randomUUID();
    private static final String REFRESH_ENDPOINT = "/api/v1/redemption/analytics/advanced/refresh-status";
    private static final String ITEM_BREAKDOWN_ENDPOINT = "/api/v1/redemption/analytics/advanced/item-breakdown";
    private static final String LIABILITY_TREND_ENDPOINT =
            "/api/v1/redemption/analytics/advanced/liability-trend";
    private static final String LIABILITY_EXPORT_ENDPOINT =
            "/api/v1/redemption/analytics/advanced/liability-trend/export";
    private static final String PERMISSION = "action.redemption.analytics.advanced";

    private static final String FEATURE_FLAG_KEY = "redemption_analytics_advanced";

    private void withPermission() {
        when(tenantValidator.getCurrentUserId()).thenReturn(USER_ID);
        when(tenantValidator.getCurrentClientId()).thenReturn(CLIENT_ID);
        when(permissionService.resolveEffectivePermissions(USER_ID)).thenReturn(Set.of(PERMISSION));
        when(featureFlagService.getEnabledFeatures(CLIENT_ID)).thenReturn(List.of(FEATURE_FLAG_KEY));
    }

    private void withoutPermission() {
        when(tenantValidator.getCurrentUserId()).thenReturn(USER_ID);
        when(tenantValidator.getCurrentClientId()).thenReturn(CLIENT_ID);
        when(permissionService.resolveEffectivePermissions(USER_ID)).thenReturn(Set.of());
    }

    /** Permission granted but feature flag disabled — controller gate should fire. */
    private void withFlagDisabled() {
        when(tenantValidator.getCurrentUserId()).thenReturn(USER_ID);
        when(tenantValidator.getCurrentClientId()).thenReturn(CLIENT_ID);
        when(permissionService.resolveEffectivePermissions(USER_ID)).thenReturn(Set.of(PERMISSION));
        when(featureFlagService.getEnabledFeatures(CLIENT_ID)).thenReturn(List.of());
    }

    private void withRateLimitAllowed() {
        when(exportRateLimiter.tryAcquireWithRetryAfter(CLIENT_ID))
                .thenReturn(new AnalyticsExportRateLimiter.RateLimitResult(true, 0L));
    }

    private void withRateLimitExceeded(long retryAfterSeconds) {
        when(exportRateLimiter.tryAcquireWithRetryAfter(CLIENT_ID))
                .thenReturn(new AnalyticsExportRateLimiter.RateLimitResult(false, retryAfterSeconds));
    }

    // ── 200: isStale=true ─────────────────────────────────────────────────────

    @Test
    @WithMockUser
    void GET_200_isStaleTrue() throws Exception {
        withPermission();
        Instant fiveHoursAgo = Instant.now().minus(5, ChronoUnit.HOURS);
        when(advancedAnalyticsService.getRefreshStatus())
                .thenReturn(AnalyticsRefreshStatusResponse.of(fiveHoursAgo, 4));

        mockMvc.perform(get(REFRESH_ENDPOINT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isStale").value(true))
                .andExpect(jsonPath("$.data.lastRefreshedAt").exists())
                .andExpect(jsonPath("$.data.stalenessThresholdHours").value(4));
    }

    // ── 200: isStale=false ────────────────────────────────────────────────────

    @Test
    @WithMockUser
    void GET_200_isStaleFalse() throws Exception {
        withPermission();
        Instant oneHourAgo = Instant.now().minus(1, ChronoUnit.HOURS);
        when(advancedAnalyticsService.getRefreshStatus())
                .thenReturn(AnalyticsRefreshStatusResponse.of(oneHourAgo, 4));

        mockMvc.perform(get(REFRESH_ENDPOINT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isStale").value(false))
                .andExpect(jsonPath("$.data.stalenessThresholdHours").value(4));
    }

    // ── 200: lastRefreshedAt=null (empty log table) ───────────────────────────

    @Test
    @WithMockUser
    void GET_200_nullLastRefreshedAt_isStaleTrue() throws Exception {
        withPermission();
        when(advancedAnalyticsService.getRefreshStatus())
                .thenReturn(AnalyticsRefreshStatusResponse.of(null, 4));

        mockMvc.perform(get(REFRESH_ENDPOINT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isStale").value(true))
                .andExpect(jsonPath("$.data.lastRefreshedAt").isEmpty());
    }

    // ── 403: permission missing ───────────────────────────────────────────────

    @Test
    @WithMockUser
    void GET_403_missingPermission() throws Exception {
        withoutPermission();

        mockMvc.perform(get(REFRESH_ENDPOINT))
                .andExpect(status().isForbidden());
    }

    // ── 401: no authentication ────────────────────────────────────────────────

    @Test
    void GET_401_noToken() throws Exception {
        mockMvc.perform(get(REFRESH_ENDPOINT))
                .andExpect(status().isUnauthorized());
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Item Breakdown  GET /item-breakdown
    // ═══════════════════════════════════════════════════════════════════════════

    // ── 200: happy path with items ─────────────────────────────────────────────

    @Test
    @WithMockUser
    void itemBreakdown_GET_200_returnsItems() throws Exception {
        withPermission();

        LocalDate dateFrom = LocalDate.of(2026, 1, 1);
        LocalDate dateTo   = LocalDate.of(2026, 1, 31);
        Instant lastRefreshedAt = Instant.now().minus(1, ChronoUnit.HOURS);

        ItemRedemptionDto item1 = new ItemRedemptionDto(
                "a1000000-0000-0000-0000-000000000001", "Gold Ring", "POINTS",
                150L, new BigDecimal("1500.00"), 75.0);
        ItemRedemptionDto item2 = new ItemRedemptionDto(
                "a2000000-0000-0000-0000-000000000002", "Silver Coin", "POINTS",
                75L, new BigDecimal("750.00"), 50.0);
        ItemBreakdownResponse response = new ItemBreakdownResponse(
                DateWindowDto.of(dateFrom, dateTo), List.of(item1, item2), lastRefreshedAt);

        when(advancedAnalyticsService.getItemBreakdown(any(AdvancedAnalyticsFilter.class)))
                .thenReturn(response);

        mockMvc.perform(get(ITEM_BREAKDOWN_ENDPOINT)
                        .param("dateFrom", "2026-01-01")
                        .param("dateTo",   "2026-01-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.items[0].catalogItemName").value("Gold Ring"))
                .andExpect(jsonPath("$.data.items[0].totalRedeemedCount").value(150))
                .andExpect(jsonPath("$.data.items[1].catalogItemName").value("Silver Coin"))
                .andExpect(jsonPath("$.data.items[1].totalRedeemedCount").value(75))
                .andExpect(jsonPath("$.data.lastRefreshedAt").exists())
                .andExpect(jsonPath("$.data.dateWindow.from").value("2026-01-01"))
                .andExpect(jsonPath("$.data.dateWindow.to").value("2026-01-31"));
    }

    // ── 422: date range exceeds 365 days ──────────────────────────────────────

    @Test
    @WithMockUser
    void itemBreakdown_GET_422_spanExceeds365Days() throws Exception {
        withPermission();

        when(advancedAnalyticsService.getItemBreakdown(any(AdvancedAnalyticsFilter.class)))
                .thenThrow(new BusinessRuleException("Date range must not exceed 365 days."));

        mockMvc.perform(get(ITEM_BREAKDOWN_ENDPOINT)
                        .param("dateFrom", "2024-01-01")
                        .param("dateTo",   "2026-01-01"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorMessage").value("Date range must not exceed 365 days."));
    }

    // ── 403: missing permission ────────────────────────────────────────────────

    @Test
    @WithMockUser
    void itemBreakdown_GET_403_missingPermission() throws Exception {
        withoutPermission();

        mockMvc.perform(get(ITEM_BREAKDOWN_ENDPOINT)
                        .param("dateFrom", "2026-01-01")
                        .param("dateTo",   "2026-01-31"))
                .andExpect(status().isForbidden());
    }

    // ── 403: feature flag disabled — controller gate fires before service/cache ─

    @Test
    @WithMockUser
    void itemBreakdown_GET_403_flagDisabled() throws Exception {
        withFlagDisabled();

        mockMvc.perform(get(ITEM_BREAKDOWN_ENDPOINT)
                        .param("dateFrom", "2026-01-01")
                        .param("dateTo",   "2026-01-31"))
                .andExpect(status().isForbidden());

        verify(advancedAnalyticsService, never()).getItemBreakdown(any());
    }

    // ── 403: flag disabled on repeated call — proves cache-hit path is also blocked ─
    // In production the second call would be a cache hit; the controller gate runs before
    // Spring Cache on every request, so the flag check cannot be bypassed by a warm cache.

    @Test
    @WithMockUser
    void itemBreakdown_GET_403_flagDisabledOnRepeatedCall_cacheBypassPrevented() throws Exception {
        withFlagDisabled();

        mockMvc.perform(get(ITEM_BREAKDOWN_ENDPOINT)
                        .param("dateFrom", "2026-01-01")
                        .param("dateTo",   "2026-01-31"))
                .andExpect(status().isForbidden());

        // Second call — still 403; the controller-level gate re-evaluates the flag
        // on every request, so a would-be cache hit cannot smuggle stale data through.
        mockMvc.perform(get(ITEM_BREAKDOWN_ENDPOINT)
                        .param("dateFrom", "2026-01-01")
                        .param("dateTo",   "2026-01-31"))
                .andExpect(status().isForbidden());

        verify(advancedAnalyticsService, never()).getItemBreakdown(any());
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Segment Breakdown  GET /segment-breakdown
    // ═══════════════════════════════════════════════════════════════════════════

    private static final String SEGMENT_BREAKDOWN_ENDPOINT =
            "/api/v1/redemption/analytics/advanced/segment-breakdown";

    // ── 200: happy path with segments ─────────────────────────────────────────

    @Test
    @WithMockUser
    void segmentBreakdown_GET_200_returnsSegments() throws Exception {
        withPermission();

        LocalDate dateFrom = LocalDate.of(2026, 1, 1);
        LocalDate dateTo   = LocalDate.of(2026, 1, 31);
        Instant lastRefreshedAt = Instant.now().minus(1, ChronoUnit.HOURS);

        SegmentRedemptionDto seg1 = new SegmentRedemptionDto(
                "APAC", "MANAGER", "POINTS", 42L, new BigDecimal("0.3500"));
        SegmentRedemptionDto seg2 = new SegmentRedemptionDto(
                null, null, "CASH", 5L, new BigDecimal("0.1000"));
        SegmentBreakdownResponse response = new SegmentBreakdownResponse(
                DateWindowDto.of(dateFrom, dateTo), List.of(seg1, seg2), lastRefreshedAt);

        when(advancedAnalyticsService.getSegmentBreakdown(any(AdvancedAnalyticsFilter.class)))
                .thenReturn(response);

        mockMvc.perform(get(SEGMENT_BREAKDOWN_ENDPOINT)
                        .param("dateFrom", "2026-01-01")
                        .param("dateTo",   "2026-01-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.segments").isArray())
                .andExpect(jsonPath("$.data.segments.length()").value(2))
                .andExpect(jsonPath("$.data.segments[0].region").value("APAC"))
                .andExpect(jsonPath("$.data.segments[0].role").value("MANAGER"))
                .andExpect(jsonPath("$.data.segments[0].currencyId").value("POINTS"))
                .andExpect(jsonPath("$.data.segments[0].totalRedeemedCount").value(42))
                .andExpect(jsonPath("$.data.lastRefreshedAt").exists())
                .andExpect(jsonPath("$.data.dateWindow.from").value("2026-01-01"))
                .andExpect(jsonPath("$.data.dateWindow.to").value("2026-01-31"));
    }

    // ── 422: date range exceeds 365 days ──────────────────────────────────────

    @Test
    @WithMockUser
    void segmentBreakdown_GET_422_spanExceeds365Days() throws Exception {
        withPermission();

        when(advancedAnalyticsService.getSegmentBreakdown(any(AdvancedAnalyticsFilter.class)))
                .thenThrow(new BusinessRuleException("Date range must not exceed 365 days."));

        mockMvc.perform(get(SEGMENT_BREAKDOWN_ENDPOINT)
                        .param("dateFrom", "2024-01-01")
                        .param("dateTo",   "2026-01-01"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorMessage").value("Date range must not exceed 365 days."));
    }

    // ── 403: missing permission ────────────────────────────────────────────────

    @Test
    @WithMockUser
    void segmentBreakdown_GET_403_missingPermission() throws Exception {
        withoutPermission();

        mockMvc.perform(get(SEGMENT_BREAKDOWN_ENDPOINT)
                        .param("dateFrom", "2026-01-01")
                        .param("dateTo",   "2026-01-31"))
                .andExpect(status().isForbidden());
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Time-to-First-Redemption  GET /time-to-first-redemption
    // ═══════════════════════════════════════════════════════════════════════════

    private static final String TTFR_ENDPOINT =
            "/api/v1/redemption/analytics/advanced/time-to-first-redemption";

    // ── 200: happy path with null avgHours (sampleCount=0) ────────────────────

    @Test
    @WithMockUser
    void ttfr_GET_200_withNullAvgHours() throws Exception {
        withPermission();

        Instant lastRefreshedAt = Instant.now().minus(1, ChronoUnit.HOURS);

        RegionTimeToRedemptionDto apacRow = new RegionTimeToRedemptionDto(
                "APAC", new BigDecimal("24.5"), null, 120L);
        RegionTimeToRedemptionDto emeaRow = new RegionTimeToRedemptionDto(
                "EMEA", null, null, 0L);
        TimeToFirstRedemptionResponse response = new TimeToFirstRedemptionResponse(
                Map.of(), List.of(apacRow, emeaRow), lastRefreshedAt);

        when(advancedAnalyticsService.getTimeToFirstRedemption(any(AdvancedAnalyticsFilter.class)))
                .thenReturn(response);

        mockMvc.perform(get(TTFR_ENDPOINT)
                        .param("dateFrom", "2026-01-01")
                        .param("dateTo",   "2026-01-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.regions").isArray())
                .andExpect(jsonPath("$.data.regions.length()").value(2))
                .andExpect(jsonPath("$.data.regions[0].region").value("APAC"))
                .andExpect(jsonPath("$.data.regions[0].avgHoursToFirstRedemption").value(24.5))
                .andExpect(jsonPath("$.data.regions[0].sampleCount").value(120))
                .andExpect(jsonPath("$.data.regions[1].region").value("EMEA"))
                .andExpect(jsonPath("$.data.regions[1].avgHoursToFirstRedemption").isEmpty())
                .andExpect(jsonPath("$.data.regions[1].sampleCount").value(0))
                .andExpect(jsonPath("$.data.lastRefreshedAt").exists());
    }

    // ── 422: date range exceeds 365 days ──────────────────────────────────────

    @Test
    @WithMockUser
    void ttfr_GET_422_spanExceeds365Days() throws Exception {
        withPermission();

        when(advancedAnalyticsService.getTimeToFirstRedemption(any(AdvancedAnalyticsFilter.class)))
                .thenThrow(new BusinessRuleException("Date range must not exceed 365 days."));

        mockMvc.perform(get(TTFR_ENDPOINT)
                        .param("dateFrom", "2024-01-01")
                        .param("dateTo",   "2026-01-01"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorMessage").value("Date range must not exceed 365 days."));
    }

    // ── 403: missing permission ────────────────────────────────────────────────

    @Test
    @WithMockUser
    void ttfr_GET_403_missingPermission() throws Exception {
        withoutPermission();

        mockMvc.perform(get(TTFR_ENDPOINT)
                        .param("dateFrom", "2026-01-01")
                        .param("dateTo",   "2026-01-31"))
                .andExpect(status().isForbidden());
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Redemption Rate Trend  GET /trend
    // ═══════════════════════════════════════════════════════════════════════════

    private static final String TREND_ENDPOINT =
            "/api/v1/redemption/analytics/advanced/trend";

    // ── 200: happy path with data points ordered ASC ──────────────────────────

    @Test
    @WithMockUser
    void trend_GET_200_returnsDataPointsOrderedAsc() throws Exception {
        withPermission();

        LocalDate dateFrom = LocalDate.of(2026, 5, 21);
        LocalDate dateTo   = LocalDate.of(2026, 5, 22);
        Instant lastRefreshedAt = Instant.now().minus(1, ChronoUnit.HOURS);

        TrendDataPointDto point1 = new TrendDataPointDto(
                LocalDate.of(2026, 5, 21), "POINTS", 10L, 10.0);
        TrendDataPointDto point2 = new TrendDataPointDto(
                LocalDate.of(2026, 5, 22), "POINTS", 15L, 15.0);
        RedemptionTrendResponse response = new RedemptionTrendResponse(
                DateWindowDto.of(dateFrom, dateTo), List.of(point1, point2), lastRefreshedAt);

        when(advancedAnalyticsService.getRedemptionTrend(any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(response);

        mockMvc.perform(get(TREND_ENDPOINT)
                        .param("dateFrom", "2026-05-21")
                        .param("dateTo",   "2026-05-22"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.dataPoints").isArray())
                .andExpect(jsonPath("$.data.dataPoints.length()").value(2))
                .andExpect(jsonPath("$.data.dataPoints[0].periodDate").value("2026-05-21"))
                .andExpect(jsonPath("$.data.dataPoints[0].currencyId").value("POINTS"))
                .andExpect(jsonPath("$.data.dataPoints[0].redeemedCount").value(10))
                .andExpect(jsonPath("$.data.dataPoints[0].redemptionRate").value(10.0))
                .andExpect(jsonPath("$.data.dataPoints[1].periodDate").value("2026-05-22"))
                .andExpect(jsonPath("$.data.dataPoints[1].redeemedCount").value(15))
                .andExpect(jsonPath("$.data.lastRefreshedAt").exists())
                .andExpect(jsonPath("$.data.dateWindow.from").value("2026-05-21"))
                .andExpect(jsonPath("$.data.dateWindow.to").value("2026-05-22"));
    }

    // ── 200: empty dataPoints list when no data ────────────────────────────────

    @Test
    @WithMockUser
    void trend_GET_200_emptyDataPoints_whenNoData() throws Exception {
        withPermission();

        LocalDate dateFrom = LocalDate.of(2026, 5, 21);
        LocalDate dateTo   = LocalDate.of(2026, 5, 22);
        Instant lastRefreshedAt = Instant.now().minus(1, ChronoUnit.HOURS);

        RedemptionTrendResponse response = new RedemptionTrendResponse(
                DateWindowDto.of(dateFrom, dateTo), List.of(), lastRefreshedAt);

        when(advancedAnalyticsService.getRedemptionTrend(any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(response);

        mockMvc.perform(get(TREND_ENDPOINT)
                        .param("dateFrom", "2026-05-21")
                        .param("dateTo",   "2026-05-22"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.dataPoints").isArray())
                .andExpect(jsonPath("$.data.dataPoints.length()").value(0));
    }

    // ── 422: date span > 365 days ─────────────────────────────────────────────

    @Test
    @WithMockUser
    void trend_GET_422_spanExceeds365Days() throws Exception {
        withPermission();

        when(advancedAnalyticsService.getRedemptionTrend(any(LocalDate.class), any(LocalDate.class)))
                .thenThrow(new BusinessRuleException("Date range must not exceed 365 days."));

        mockMvc.perform(get(TREND_ENDPOINT)
                        .param("dateFrom", "2024-01-01")
                        .param("dateTo",   "2026-01-01"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorMessage").value("Date range must not exceed 365 days."));
    }

    // ── 403: missing permission ────────────────────────────────────────────────

    @Test
    @WithMockUser
    void trend_GET_403_missingPermission() throws Exception {
        withoutPermission();

        mockMvc.perform(get(TREND_ENDPOINT)
                        .param("dateFrom", "2026-05-21")
                        .param("dateTo",   "2026-05-22"))
                .andExpect(status().isForbidden());
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Liability Trend JSON  GET /liability-trend
    // ═══════════════════════════════════════════════════════════════════════════

    // ── 200: happy path with data points ──────────────────────────────────────

    @Test
    @WithMockUser
    void liabilityTrend_GET_200_returnsDataPoints() throws Exception {
        withPermission();

        LocalDate dateFrom = LocalDate.of(2026, 6, 1);
        LocalDate dateTo   = LocalDate.of(2026, 6, 2);
        Instant lastRefreshedAt = Instant.now().minus(1, ChronoUnit.HOURS);

        LiabilityDataPointDto dp1 = new LiabilityDataPointDto(
                LocalDate.of(2026, 6, 1), "POINTS", new BigDecimal("1200.50"));
        LiabilityDataPointDto dp2 = new LiabilityDataPointDto(
                LocalDate.of(2026, 6, 2), "POINTS", new BigDecimal("1150.00"));
        LiabilityTrendResponse response = new LiabilityTrendResponse(
                DateWindowDto.of(dateFrom, dateTo), List.of(dp1, dp2), lastRefreshedAt);

        when(advancedAnalyticsService.getLiabilityTrend(any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(response);

        mockMvc.perform(get(LIABILITY_TREND_ENDPOINT)
                        .param("dateFrom", "2026-06-01")
                        .param("dateTo",   "2026-06-02"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.dataPoints").isArray())
                .andExpect(jsonPath("$.data.dataPoints.length()").value(2))
                .andExpect(jsonPath("$.data.dataPoints[0].periodDate").value("2026-06-01"))
                .andExpect(jsonPath("$.data.dataPoints[0].currencyId").value("POINTS"))
                .andExpect(jsonPath("$.data.dataPoints[0].totalUnredeemedBalance").value(1200.50))
                .andExpect(jsonPath("$.data.dataPoints[1].periodDate").value("2026-06-02"))
                .andExpect(jsonPath("$.data.lastRefreshedAt").exists())
                .andExpect(jsonPath("$.data.dateWindow.from").value("2026-06-01"))
                .andExpect(jsonPath("$.data.dateWindow.to").value("2026-06-02"));
    }

    // ── 200: empty dataPoints when no data ────────────────────────────────────

    @Test
    @WithMockUser
    void liabilityTrend_GET_200_emptyDataPoints_whenNoData() throws Exception {
        withPermission();

        LocalDate dateFrom = LocalDate.of(2026, 6, 1);
        LocalDate dateTo   = LocalDate.of(2026, 6, 30);
        Instant lastRefreshedAt = Instant.now().minus(1, ChronoUnit.HOURS);

        LiabilityTrendResponse response = new LiabilityTrendResponse(
                DateWindowDto.of(dateFrom, dateTo), List.of(), lastRefreshedAt);

        when(advancedAnalyticsService.getLiabilityTrend(any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(response);

        mockMvc.perform(get(LIABILITY_TREND_ENDPOINT)
                        .param("dateFrom", "2026-06-01")
                        .param("dateTo",   "2026-06-30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.dataPoints").isArray())
                .andExpect(jsonPath("$.data.dataPoints.length()").value(0));
    }

    // ── 422: date span > 365 days ─────────────────────────────────────────────

    @Test
    @WithMockUser
    void liabilityTrend_GET_422_spanExceeds365Days() throws Exception {
        withPermission();

        when(advancedAnalyticsService.getLiabilityTrend(any(LocalDate.class), any(LocalDate.class)))
                .thenThrow(new BusinessRuleException("Date range must not exceed 365 days."));

        mockMvc.perform(get(LIABILITY_TREND_ENDPOINT)
                        .param("dateFrom", "2024-01-01")
                        .param("dateTo",   "2026-01-01"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorMessage").value("Date range must not exceed 365 days."));
    }

    // ── 403: missing permission ────────────────────────────────────────────────

    @Test
    @WithMockUser
    void liabilityTrend_GET_403_missingPermission() throws Exception {
        withoutPermission();

        mockMvc.perform(get(LIABILITY_TREND_ENDPOINT)
                        .param("dateFrom", "2026-06-01")
                        .param("dateTo",   "2026-06-30"))
                .andExpect(status().isForbidden());
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Liability Trend Export  GET /liability-trend/export
    // ═══════════════════════════════════════════════════════════════════════════

    // ── 200: CSV content-type and Content-Disposition header ──────────────────

    @Test
    @WithMockUser
    void liabilityExport_GET_200_csvContentTypeAndDisposition() throws Exception {
        withPermission();
        withRateLimitAllowed();

        byte[] csvBytes = "period_date,currency_type,total_unredeemed_balance\n2026-06-01,POINTS,1200.50\n"
                .getBytes(java.nio.charset.StandardCharsets.UTF_8);
        when(advancedAnalyticsService.exportLiabilityTrend(any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(csvBytes);

        mockMvc.perform(get(LIABILITY_EXPORT_ENDPOINT)
                        .param("dateFrom", "2026-06-01")
                        .param("dateTo",   "2026-06-30"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.parseMediaType("text/csv")))
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"redemption-liability-trend.csv\""));
    }

    // ── 422: span > 365 days on export ────────────────────────────────────────

    @Test
    @WithMockUser
    void liabilityExport_GET_422_spanExceeds365Days() throws Exception {
        withPermission();
        withRateLimitAllowed();

        when(advancedAnalyticsService.exportLiabilityTrend(any(LocalDate.class), any(LocalDate.class)))
                .thenThrow(new BusinessRuleException("Date range must not exceed 365 days."));

        mockMvc.perform(get(LIABILITY_EXPORT_ENDPOINT)
                        .param("dateFrom", "2024-01-01")
                        .param("dateTo",   "2026-01-01"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorMessage").value("Date range must not exceed 365 days."));
    }

    // ── 429: rate limit exceeded ───────────────────────────────────────────────

    @Test
    @WithMockUser
    void liabilityExport_GET_429_rateLimitExceeded() throws Exception {
        withPermission();
        withRateLimitExceeded(45L);

        mockMvc.perform(get(LIABILITY_EXPORT_ENDPOINT)
                        .param("dateFrom", "2026-06-01")
                        .param("dateTo",   "2026-06-30"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "45"))
                .andExpect(jsonPath("$.errorCode").value("RATE_LIMIT_EXCEEDED"));
    }

    // ── 403: missing permission on export ─────────────────────────────────────

    @Test
    @WithMockUser
    void liabilityExport_GET_403_missingPermission() throws Exception {
        withoutPermission();

        mockMvc.perform(get(LIABILITY_EXPORT_ENDPOINT)
                        .param("dateFrom", "2026-06-01")
                        .param("dateTo",   "2026-06-30"))
                .andExpect(status().isForbidden());
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Failure Breakdown  GET /failure-breakdown  (US-07)
    // ═══════════════════════════════════════════════════════════════════════════

    private static final String FAILURE_BREAKDOWN_ENDPOINT =
            "/api/v1/redemption/analytics/advanced/failure-breakdown";

    // ── 200: happy path with rows sorted by failureRate desc ──────────────────

    @Test
    @WithMockUser
    void failureBreakdown_GET_200_returnsRowsSortedByFailureRateDesc() throws Exception {
        withPermission();

        LocalDate dateFrom = LocalDate.of(2026, 1, 1);
        LocalDate dateTo   = LocalDate.of(2026, 1, 31);
        Instant lastRefreshedAt = Instant.now().minus(1, ChronoUnit.HOURS);

        FailureModeDto row1 = new FailureModeDto(
                "APPROVAL_REQUIRED", "a1000000-0000-0000-0000-000000000001", "Gold Ring",
                "POINTS", 30L, 5L, 100L, 35.0);
        FailureModeDto row2 = new FailureModeDto(
                "BATCH", "a2000000-0000-0000-0000-000000000002", "Silver Coin",
                "POINTS", 10L, 2L, 100L, 12.0);
        FailureBreakdownResponse response = new FailureBreakdownResponse(
                DateWindowDto.of(dateFrom, dateTo), List.of(row1, row2), lastRefreshedAt);

        when(advancedAnalyticsService.getFailureBreakdown(any(AdvancedAnalyticsFilter.class)))
                .thenReturn(response);

        mockMvc.perform(get(FAILURE_BREAKDOWN_ENDPOINT)
                        .param("dateFrom", "2026-01-01")
                        .param("dateTo",   "2026-01-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.failureModes").isArray())
                .andExpect(jsonPath("$.data.failureModes.length()").value(2))
                .andExpect(jsonPath("$.data.failureModes[0].processingMode").value("APPROVAL_REQUIRED"))
                .andExpect(jsonPath("$.data.failureModes[0].catalogItemName").value("Gold Ring"))
                .andExpect(jsonPath("$.data.failureModes[0].currencyId").value("POINTS"))
                .andExpect(jsonPath("$.data.failureModes[0].failedCount").value(30))
                .andExpect(jsonPath("$.data.failureModes[0].cancelledCount").value(5))
                .andExpect(jsonPath("$.data.failureModes[0].totalCount").value(100))
                .andExpect(jsonPath("$.data.failureModes[0].failureRate").value(35.0))
                .andExpect(jsonPath("$.data.failureModes[1].processingMode").value("BATCH"))
                .andExpect(jsonPath("$.data.failureModes[1].failureRate").value(12.0))
                .andExpect(jsonPath("$.data.lastRefreshedAt").exists())
                .andExpect(jsonPath("$.data.dateWindow.from").value("2026-01-01"))
                .andExpect(jsonPath("$.data.dateWindow.to").value("2026-01-31"));
    }

    // ── 422: date range exceeds 365 days ──────────────────────────────────────

    @Test
    @WithMockUser
    void failureBreakdown_GET_422_spanExceeds365Days() throws Exception {
        withPermission();

        when(advancedAnalyticsService.getFailureBreakdown(any(AdvancedAnalyticsFilter.class)))
                .thenThrow(new BusinessRuleException("Date range must not exceed 365 days."));

        mockMvc.perform(get(FAILURE_BREAKDOWN_ENDPOINT)
                        .param("dateFrom", "2024-01-01")
                        .param("dateTo",   "2026-01-01"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorMessage").value("Date range must not exceed 365 days."));
    }

    // ── 403: missing permission ────────────────────────────────────────────────

    @Test
    @WithMockUser
    void failureBreakdown_GET_403_missingPermission() throws Exception {
        withoutPermission();

        mockMvc.perform(get(FAILURE_BREAKDOWN_ENDPOINT)
                        .param("dateFrom", "2026-01-01")
                        .param("dateTo",   "2026-01-31"))
                .andExpect(status().isForbidden());
    }
}
