package com.tenxengage.app.controller.redemption;

import com.tenxengage.app.config.SecurityConfig;
import com.tenxengage.app.dto.response.RedemptionRequestResponse;
import com.tenxengage.app.dto.response.redemption.RedemptionAdminHistoryResponse;
import com.tenxengage.app.exception.ResourceNotFoundException;
import com.tenxengage.app.security.CustomUserDetailsService;
import com.tenxengage.app.security.JwtTokenProvider;
import com.tenxengage.app.security.PermissionAspect;
import com.tenxengage.app.security.PermissionMetrics;
import com.tenxengage.app.security.TenantValidator;
import com.tenxengage.app.service.ClientService;
import com.tenxengage.app.service.PermissionService;
import com.tenxengage.app.service.redemption.RedemptionHistoryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RedemptionHistoryController.class)
@Import({SecurityConfig.class, PermissionAspect.class, AopAutoConfiguration.class})
class RedemptionHistoryControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockBean private RedemptionHistoryService historyService;
    @MockBean private PermissionService permissionService;
    @MockBean private TenantValidator tenantValidator;
    @MockBean private PermissionMetrics permissionMetrics;
    @MockBean private JwtTokenProvider jwtTokenProvider;
    @MockBean private CustomUserDetailsService customUserDetailsService;
    @MockBean private ClientService clientService;
    @MockBean private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID REQUEST_ID = UUID.randomUUID();

    private void withPermission(String... permissions) {
        when(tenantValidator.getCurrentUserId()).thenReturn(USER_ID);
        when(permissionService.resolveEffectivePermissions(USER_ID)).thenReturn(Set.of(permissions));
    }

    private void withoutPermission() {
        when(tenantValidator.getCurrentUserId()).thenReturn(USER_ID);
        when(permissionService.resolveEffectivePermissions(USER_ID)).thenReturn(Set.of());
    }

    private RedemptionRequestResponse sampleItem() {
        return new RedemptionRequestResponse(
                REQUEST_ID, "COMPLETED", new BigDecimal("100.00"), "cash",
                "CASH", "INSTANT", "Amazon Gift Card", Instant.now(), Instant.now(), null, null, false);
    }






    // ── /all endpoint tests ────────────────────────────────────────────────

    private RedemptionAdminHistoryResponse adminItem() {
        return new RedemptionAdminHistoryResponse(
                REQUEST_ID, "COMPLETED", new BigDecimal("100.00"), "cash",
                "CASH", "INSTANT", "Gift Card", Instant.now(), Instant.now(), null,
                USER_ID, "Alice Smith", null, "Acme Corp");
    }

    @Test
    @WithMockUser
    void GET_200_listTenantRedemptions_withUserAndCompanyNames() throws Exception {
        withPermission("action.redemption.view_all_history");
        when(historyService.getTenantHistory(any(), any()))
                .thenReturn(new PageImpl<>(List.of(adminItem())));

        mockMvc.perform(get("/api/v1/redemption/requests/all"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.data[0].userDisplayName").value("Alice Smith"))
                .andExpect(jsonPath("$.data.data[0].partnerCompanyName").value("Acme Corp"));
    }

    @Test
    @WithMockUser
    void GET_200_listTenantRedemptions_withUserIdFilter() throws Exception {
        withPermission("action.redemption.view_all_history");
        when(historyService.getTenantHistory(any(), any()))
                .thenReturn(new PageImpl<>(List.of(adminItem())));

        mockMvc.perform(get("/api/v1/redemption/requests/all?userId=" + USER_ID))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser
    void GET_403_listTenantRedemptions_partnerSeller() throws Exception {
        withPermission("action.redemption.view_history"); // partner permission, not view_all

        mockMvc.perform(get("/api/v1/redemption/requests/all"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser
    void GET_403_listTenantRedemptions_noPermission() throws Exception {
        withoutPermission();

        mockMvc.perform(get("/api/v1/redemption/requests/all"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser
    void GET_400_listTenantRedemptions_invalidStatus() throws Exception {
        withPermission("action.redemption.view_all_history");

        mockMvc.perform(get("/api/v1/redemption/requests/all?status=INVALID"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser
    void GET_404_listTenantRedemptions_wrongTenant() throws Exception {
        withPermission("action.redemption.view_all_history");
        when(historyService.getTenantHistory(any(), any()))
                .thenThrow(new ResourceNotFoundException("RedemptionRequest", "clientId", USER_ID));

        mockMvc.perform(get("/api/v1/redemption/requests/all"))
                .andExpect(status().isNotFound());
    }
}
