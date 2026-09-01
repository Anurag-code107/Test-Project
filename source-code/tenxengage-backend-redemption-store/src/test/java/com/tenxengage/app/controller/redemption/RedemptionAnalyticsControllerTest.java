package com.tenxengage.app.controller.redemption;

import com.tenxengage.app.config.SecurityConfig;
import com.tenxengage.app.dto.response.redemption.CurrencyTypeBalanceDto;
import com.tenxengage.app.dto.response.redemption.CurrencyTypeRateDto;
import com.tenxengage.app.dto.response.redemption.DateWindowDto;
import com.tenxengage.app.dto.response.redemption.RedemptionAnalyticsSummaryResponse;
import com.tenxengage.app.dto.response.redemption.RedemptionCountDto;
import com.tenxengage.app.exception.BusinessRuleException;
import com.tenxengage.app.security.AnalyticsExportRateLimiter;
import com.tenxengage.app.security.CustomUserDetailsService;
import com.tenxengage.app.security.JwtTokenProvider;
import com.tenxengage.app.security.PermissionAspect;
import com.tenxengage.app.security.PermissionMetrics;
import com.tenxengage.app.security.TenantValidator;
import com.tenxengage.app.service.ClientService;
import com.tenxengage.app.service.PermissionService;
import com.tenxengage.app.service.redemption.RedemptionAnalyticsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import java.time.ZoneOffset;

import com.tenxengage.app.security.AnalyticsExportRateLimiter.RateLimitResult;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RedemptionAnalyticsController.class)
@Import({SecurityConfig.class, PermissionAspect.class, AopAutoConfiguration.class,
         com.tenxengage.app.config.JacksonConfig.class})
class RedemptionAnalyticsControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockBean private RedemptionAnalyticsService analyticsService;
    @MockBean private AnalyticsExportRateLimiter exportRateLimiter;
    @MockBean private PermissionService permissionService;
    @MockBean private TenantValidator tenantValidator;
    @MockBean private PermissionMetrics permissionMetrics;
    @MockBean private JwtTokenProvider jwtTokenProvider;
    @MockBean private CustomUserDetailsService customUserDetailsService;
    @MockBean private ClientService clientService;
    @MockBean private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    private static final UUID USER_ID = UUID.randomUUID();

    private void withPermission(String... permissions) {
        when(tenantValidator.getCurrentUserId()).thenReturn(USER_ID);
        when(permissionService.resolveEffectivePermissions(USER_ID)).thenReturn(Set.of(permissions));
    }

    private void withoutPermission() {
        when(tenantValidator.getCurrentUserId()).thenReturn(USER_ID);
        when(permissionService.resolveEffectivePermissions(USER_ID)).thenReturn(Set.of());
    }

    private RedemptionAnalyticsSummaryResponse sampleResponse(LocalDate from, LocalDate to) {
        CurrencyTypeRateDto rateDto = new CurrencyTypeRateDto(
                "CASH", 50L, 200L, "25.00", true);
        CurrencyTypeBalanceDto balanceDto = new CurrencyTypeBalanceDto(
                "CASH", 1000L, 200L, 1200L);
        RedemptionCountDto countDto = new RedemptionCountDto(
                10L, Map.of("PENDING", 2L, "PROCESSING", 1L, "COMPLETED", 7L,
                            "FAILED", 0L, "CANCELLED", 0L), true);
        return new RedemptionAnalyticsSummaryResponse(
                DateWindowDto.of(from, to),
                List.of(rateDto),
                List.of(balanceDto),
                List.of(rateDto),
                countDto
        );
    }

    // ── 200: valid dateFrom / dateTo ──────────────────────────────────────────

    @Test
    @WithMockUser
    void GET_200_withExplicitDateParams() throws Exception {
        withPermission("action.redemption.view_analytics");
        LocalDate from = LocalDate.of(2026, 5, 1);
        LocalDate to = LocalDate.of(2026, 5, 31);
        when(analyticsService.getAnalyticsSummary(from, to)).thenReturn(sampleResponse(from, to));

        mockMvc.perform(get("/api/v1/redemption/analytics")
                        .param("dateFrom", "2026-05-01")
                        .param("dateTo", "2026-05-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.dateWindow.from").value("2026-05-01"))
                .andExpect(jsonPath("$.data.dateWindow.to").value("2026-05-31"))
                .andExpect(jsonPath("$.data.redemptionRates[0].currencyId").value("CASH"))
                .andExpect(jsonPath("$.data.redemptionRates[0].ratePercentage").value("25.00"))
                .andExpect(jsonPath("$.data.totalRedemptionCount.total").value(10));
    }

    // ── 200: no params — defaults applied ────────────────────────────────────

    @Test
    @WithMockUser
    void GET_200_withDefaultDates_noParamsSupplied() throws Exception {
        withPermission("action.redemption.view_analytics");
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate minus30 = today.minusDays(30);
        when(analyticsService.getAnalyticsSummary(eq(minus30), eq(today)))
                .thenReturn(sampleResponse(minus30, today));

        mockMvc.perform(get("/api/v1/redemption/analytics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.dateWindow").exists());
    }

    // ── 400: invalid date format ──────────────────────────────────────────────

    @Test
    @WithMockUser
    void GET_400_invalidDateFormat() throws Exception {
        withPermission("action.redemption.view_analytics");

        mockMvc.perform(get("/api/v1/redemption/analytics")
                        .param("dateFrom", "not-a-date")
                        .param("dateTo", "2026-05-31"))
                .andExpect(status().isBadRequest());
    }

    // ── 422: dateFrom after dateTo ────────────────────────────────────────────

    @Test
    @WithMockUser
    void GET_422_dateFrom_afterDateTo() throws Exception {
        withPermission("action.redemption.view_analytics");
        when(analyticsService.getAnalyticsSummary(any(), any()))
                .thenThrow(new BusinessRuleException("dateFrom must not be after dateTo."));

        mockMvc.perform(get("/api/v1/redemption/analytics")
                        .param("dateFrom", "2026-05-31")
                        .param("dateTo", "2026-05-01"))
                .andExpect(status().isUnprocessableEntity());
    }

    // ── 422: span > 730 days ──────────────────────────────────────────────────

    @Test
    @WithMockUser
    void GET_422_spanExceeds730Days() throws Exception {
        withPermission("action.redemption.view_analytics");
        when(analyticsService.getAnalyticsSummary(any(), any()))
                .thenThrow(new BusinessRuleException("Date range must not exceed 730 days."));

        mockMvc.perform(get("/api/v1/redemption/analytics")
                        .param("dateFrom", "2023-01-01")
                        .param("dateTo", "2026-05-31"))
                .andExpect(status().isUnprocessableEntity());
    }

    // ── 403: insufficient permission ──────────────────────────────────────────

    @Test
    @WithMockUser
    void GET_403_insufficientPermission() throws Exception {
        withoutPermission();

        mockMvc.perform(get("/api/v1/redemption/analytics")
                        .param("dateFrom", "2026-05-01")
                        .param("dateTo", "2026-05-31"))
                .andExpect(status().isForbidden());
    }

    // ── 401: no authentication token ─────────────────────────────────────────

    @Test
    void GET_401_noToken() throws Exception {
        mockMvc.perform(get("/api/v1/redemption/analytics"))
                .andExpect(status().isUnauthorized());
    }

    // ──────────────────────────────────────────────────────────────────────────────
    // Export endpoint: GET /api/v1/redemption/analytics/export
    // ──────────────────────────────────────────────────────────────────────────────

    // ── 200: correct Content-Type and Content-Disposition ─────────────────────

    @Test
    @WithMockUser
    void EXPORT_200_correctContentTypeAndDisposition() throws Exception {
        withPermission("action.redemption.view_analytics");
        when(tenantValidator.getCurrentClientId()).thenReturn(USER_ID);
        when(exportRateLimiter.tryAcquireWithRetryAfter(USER_ID))
                .thenReturn(new RateLimitResult(true, 0L));

        String csvContent = "userId,userName,companyId,companyName,currencyType,availableBalance,reservedBalance\n";
        when(analyticsService.exportUnredeemedBalances())
                .thenReturn(csvContent.getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(get("/api/v1/redemption/analytics/export"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type",
                        org.hamcrest.Matchers.startsWith("text/csv")))
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"redemption-unredeemed-balances.csv\""));
    }

    // ── 200: response body contains CSV header row ────────────────────────────

    @Test
    @WithMockUser
    void EXPORT_200_bodyContainsHeaderRow() throws Exception {
        withPermission("action.redemption.view_analytics");
        when(tenantValidator.getCurrentClientId()).thenReturn(USER_ID);
        when(exportRateLimiter.tryAcquireWithRetryAfter(USER_ID))
                .thenReturn(new RateLimitResult(true, 0L));

        String csvContent = "userId,userName,companyId,companyName,currencyType,availableBalance,reservedBalance\n";
        when(analyticsService.exportUnredeemedBalances())
                .thenReturn(csvContent.getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(get("/api/v1/redemption/analytics/export"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("userId,userName,companyId")));
    }

    // ── 403: insufficient permission — audit NOT written ──────────────────────

    @Test
    @WithMockUser
    void EXPORT_403_insufficientPermission_auditNotWritten() throws Exception {
        withoutPermission();

        mockMvc.perform(get("/api/v1/redemption/analytics/export"))
                .andExpect(status().isForbidden());

        // Service (and therefore export + audit path) must never be called on 403
        verify(analyticsService, never()).exportUnredeemedBalances();
    }

    // ── 401: no authentication token ─────────────────────────────────────────

    @Test
    void EXPORT_401_noToken() throws Exception {
        mockMvc.perform(get("/api/v1/redemption/analytics/export"))
                .andExpect(status().isUnauthorized());
    }

    // ── 429: rate limit exceeded — Retry-After header present ────────────────

    @Test
    @WithMockUser
    void EXPORT_429_rateLimitExceeded_includesRetryAfterHeader() throws Exception {
        withPermission("action.redemption.view_analytics");
        when(tenantValidator.getCurrentClientId()).thenReturn(USER_ID);
        when(exportRateLimiter.tryAcquireWithRetryAfter(USER_ID))
                .thenReturn(new RateLimitResult(false, 45L));

        mockMvc.perform(get("/api/v1/redemption/analytics/export"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(header().string("Retry-After", "45"));

        // Service must not be called when rate limited
        verify(analyticsService, never()).exportUnredeemedBalances();
    }
}
