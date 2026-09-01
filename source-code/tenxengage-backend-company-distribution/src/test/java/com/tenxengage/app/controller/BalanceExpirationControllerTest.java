package com.tenxengage.app.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tenxengage.app.config.SecurityConfig;
import com.tenxengage.app.dto.request.UpsertBalanceExpirationPolicyRequest;
import com.tenxengage.app.dto.response.BalanceBreakageReportResponse;
import com.tenxengage.app.dto.response.BalanceExpirationPolicyResponse;
import com.tenxengage.app.dto.response.BreakageRowDto;
import com.tenxengage.app.dto.response.ExpiringBalancePreviewResponse;
import com.tenxengage.app.entity.enums.ExpirationMode;
import com.tenxengage.app.entity.enums.Granularity;
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
import com.tenxengage.app.service.redemption.BalanceBreakageReportService;
import com.tenxengage.app.service.redemption.BalanceExpirationPolicyService;
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
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BalanceExpirationController.class)
@Import({SecurityConfig.class, PermissionAspect.class, AopAutoConfiguration.class})
class BalanceExpirationControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private BalanceExpirationPolicyService policyService;
    @MockBean private BalanceBreakageReportService breakageReportService;
    @MockBean private AnalyticsExportRateLimiter exportRateLimiter;
    @MockBean private PermissionService permissionService;
    @MockBean private TenantValidator tenantValidator;
    @MockBean private PermissionMetrics permissionMetrics;
    @MockBean private JwtTokenProvider jwtTokenProvider;
    @MockBean private CustomUserDetailsService customUserDetailsService;
    @MockBean private ClientService clientService;
    @MockBean private JpaMetamodelMappingContext jpaMetamodelMappingContext;
    @MockBean private FeatureFlagService featureFlagService;

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID CLIENT_ID = UUID.randomUUID();
    private static final String CONFIGURE_PERMISSION = "action.redemption.expiration.configure";
    private static final String BREAKAGE_PERMISSION = "action.redemption.expiration.view_breakage";

    private void withPermission() {
        when(tenantValidator.getCurrentUserId()).thenReturn(USER_ID);
        when(permissionService.resolveEffectivePermissions(USER_ID))
                .thenReturn(Set.of(CONFIGURE_PERMISSION));
    }

    private void withBreakagePermission() {
        when(tenantValidator.getCurrentUserId()).thenReturn(USER_ID);
        when(tenantValidator.getCurrentClientId()).thenReturn(CLIENT_ID);
        when(permissionService.resolveEffectivePermissions(USER_ID))
                .thenReturn(Set.of(BREAKAGE_PERMISSION));
    }

    private void withoutPermission() {
        when(tenantValidator.getCurrentUserId()).thenReturn(USER_ID);
        when(permissionService.resolveEffectivePermissions(USER_ID)).thenReturn(Set.of());
    }

    private BalanceExpirationPolicyResponse samplePolicyResponse(String currencyId) {
        return new BalanceExpirationPolicyResponse(
                currencyId, "Points", true, ExpirationMode.INACTIVITY,
                365, null, 30, Instant.now(), Instant.now());
    }

    private ExpiringBalancePreviewResponse samplePreviewResponse() {
        return new ExpiringBalancePreviewResponse(
                "points", "Points", LocalDate.now().plusDays(20),
                12L, new BigDecimal("3400.00"));
    }

    // ── GET /policies ─────────────────────────────────────────────────────────

    @Test
    @WithMockUser
    void getPolicies_returns200_withPolicies() throws Exception {
        withPermission();
        when(policyService.getPolicies()).thenReturn(List.of(samplePolicyResponse("points")));

        mockMvc.perform(get("/api/v1/redemption/expiration/policies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].currencyId").value("points"))
                .andExpect(jsonPath("$.data[0].enabled").value(true))
                .andExpect(jsonPath("$.data[0].expirationMode").value("INACTIVITY"))
                .andExpect(jsonPath("$.data[0].leadTimeDays").value(30));
    }

    @Test
    @WithMockUser
    void getPolicies_returns403_missingPermission() throws Exception {
        withoutPermission();

        mockMvc.perform(get("/api/v1/redemption/expiration/policies"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser
    void getPolicies_returns200_emptyList() throws Exception {
        withPermission();
        when(policyService.getPolicies()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/redemption/expiration/policies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    // ── PUT /policies/{currencyId} ─────────────────────────────────────────────

    @Test
    @WithMockUser
    void upsertPolicy_returns200_withValidBody() throws Exception {
        withPermission();
        when(policyService.upsertPolicy(eq("points"), any())).thenReturn(samplePolicyResponse("points"));

        UpsertBalanceExpirationPolicyRequest req = new UpsertBalanceExpirationPolicyRequest();
        req.setEnabled(true);
        req.setExpirationMode(ExpirationMode.INACTIVITY);
        req.setInactivityDays(365);
        req.setLeadTimeDays(30);

        mockMvc.perform(put("/api/v1/redemption/expiration/policies/points")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currencyId").value("points"))
                .andExpect(jsonPath("$.data.enabled").value(true));
    }

    @Test
    @WithMockUser
    void upsertPolicy_returns422_onBusinessRuleViolation() throws Exception {
        withPermission();
        when(policyService.upsertPolicy(eq("points"), any()))
                .thenThrow(new BusinessRuleException("ERR_LEAD_TIME_GTE_INACTIVITY",
                        "Lead time must be less than the inactivity period"));

        UpsertBalanceExpirationPolicyRequest req = new UpsertBalanceExpirationPolicyRequest();
        req.setEnabled(true);
        req.setExpirationMode(ExpirationMode.INACTIVITY);
        req.setInactivityDays(90);
        req.setLeadTimeDays(120);

        mockMvc.perform(put("/api/v1/redemption/expiration/policies/points")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("ERR_LEAD_TIME_GTE_INACTIVITY"));
    }

    @Test
    @WithMockUser
    void upsertPolicy_returns403_missingPermission() throws Exception {
        withoutPermission();

        UpsertBalanceExpirationPolicyRequest req = new UpsertBalanceExpirationPolicyRequest();
        req.setEnabled(true);
        req.setExpirationMode(ExpirationMode.INACTIVITY);
        req.setInactivityDays(365);
        req.setLeadTimeDays(30);

        mockMvc.perform(put("/api/v1/redemption/expiration/policies/points")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser
    void upsertPolicy_returns400_whenRequiredFieldMissing() throws Exception {
        withPermission();

        // Missing expirationMode (required @NotNull)
        UpsertBalanceExpirationPolicyRequest req = new UpsertBalanceExpirationPolicyRequest();
        req.setEnabled(true);
        req.setLeadTimeDays(30);
        // expirationMode is null — should fail @NotNull

        mockMvc.perform(put("/api/v1/redemption/expiration/policies/points")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    // ── GET /expiring-soon ────────────────────────────────────────────────────

    @Test
    @WithMockUser
    void getExpiringSoon_returns200_withPreviewData() throws Exception {
        withPermission();
        when(policyService.getExpiringSoon(eq(30), eq(null))).thenReturn(List.of(samplePreviewResponse()));

        mockMvc.perform(get("/api/v1/redemption/expiration/expiring-soon")
                        .param("withinDays", "30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].currencyId").value("points"))
                .andExpect(jsonPath("$.data[0].affectedWalletCount").value(12));
    }

    @Test
    @WithMockUser
    void getExpiringSoon_returns403_missingPermission() throws Exception {
        withoutPermission();

        mockMvc.perform(get("/api/v1/redemption/expiration/expiring-soon"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser
    void getExpiringSoon_returns200_withCurrencyFilter() throws Exception {
        withPermission();
        when(policyService.getExpiringSoon(eq(null), eq("points"))).thenReturn(List.of(samplePreviewResponse()));

        mockMvc.perform(get("/api/v1/redemption/expiration/expiring-soon")
                        .param("currencyId", "points"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].currencyId").value("points"));
    }

    // ── GET /breakage ─────────────────────────────────────────────────────────

    @Test
    @WithMockUser
    void getBreakage_returns200_withReport() throws Exception {
        withBreakagePermission();
        BalanceBreakageReportResponse report = sampleBreakageReport();
        when(breakageReportService.getBreakage(
                eq(LocalDate.of(2025, 1, 1)), eq(LocalDate.of(2025, 6, 30)),
                isNull(), isNull()))
                .thenReturn(report);

        mockMvc.perform(get("/api/v1/redemption/expiration/breakage")
                        .param("from", "2025-01-01")
                        .param("to", "2025-06-30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.granularity").value("MONTH"))
                .andExpect(jsonPath("$.data.rows[0].currencyId").value("points"))
                .andExpect(jsonPath("$.data.rows[0].expiredCount").value(5));
    }

    @Test
    @WithMockUser
    void getBreakage_returns403_missingPermission() throws Exception {
        withoutPermission();

        mockMvc.perform(get("/api/v1/redemption/expiration/breakage")
                        .param("from", "2025-01-01")
                        .param("to", "2025-06-30"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser
    void getBreakage_returns400_whenDateRangeInvalid() throws Exception {
        withBreakagePermission();
        when(breakageReportService.getBreakage(any(), any(), any(), any()))
                .thenThrow(new BusinessRuleException("ERR_INVALID_DATE_RANGE",
                        "End date must be on or after start date"));

        mockMvc.perform(get("/api/v1/redemption/expiration/breakage")
                        .param("from", "2025-06-30")
                        .param("to", "2025-01-01"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("ERR_INVALID_DATE_RANGE"));
    }

    // ── GET /breakage/export ───────────────────────────────────────────────────

    @Test
    @WithMockUser
    void exportBreakageCsv_returns200_csvContentType() throws Exception {
        withBreakagePermission();
        when(exportRateLimiter.tryAcquireWithRetryAfter(CLIENT_ID))
                .thenReturn(new AnalyticsExportRateLimiter.RateLimitResult(true, 0L));
        when(breakageReportService.exportBreakageCsv(any(), any(), any(), any()))
                .thenReturn("period_start,period_end,currency_id,expired_count,total_expired_amount\n" +
                        "2025-01-01,2025-01-31,points,5,1500.00\n");

        mockMvc.perform(get("/api/v1/redemption/expiration/breakage/export")
                        .param("from", "2025-01-01")
                        .param("to", "2025-06-30"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"balance-expiration-breakage.csv\""));
    }

    @Test
    @WithMockUser
    void exportBreakageCsv_returns403_missingPermission() throws Exception {
        withoutPermission();

        mockMvc.perform(get("/api/v1/redemption/expiration/breakage/export")
                        .param("from", "2025-01-01")
                        .param("to", "2025-06-30"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser
    void exportBreakageCsv_returns429_whenRateLimited() throws Exception {
        withBreakagePermission();
        when(exportRateLimiter.tryAcquireWithRetryAfter(CLIENT_ID))
                .thenReturn(new AnalyticsExportRateLimiter.RateLimitResult(false, 45L));

        mockMvc.perform(get("/api/v1/redemption/expiration/breakage/export")
                        .param("from", "2025-01-01")
                        .param("to", "2025-06-30"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "45"));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private BalanceBreakageReportResponse sampleBreakageReport() {
        BreakageRowDto row = new BreakageRowDto(
                LocalDate.of(2025, 1, 1),
                LocalDate.of(2025, 1, 31),
                "points",
                "Points",
                5L,
                new BigDecimal("1500.00")
        );
        return BalanceBreakageReportResponse.from(
                LocalDate.of(2025, 1, 1),
                LocalDate.of(2025, 6, 30),
                Granularity.MONTH,
                List.of(row)
        );
    }
}
