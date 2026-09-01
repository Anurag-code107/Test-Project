package com.tenxengage.app.controller;

import com.tenxengage.app.config.SecurityConfig;
import com.tenxengage.app.repository.ComplianceValueCapRepository;
import com.tenxengage.app.repository.GovernmentSegmentConfigRepository;
import com.tenxengage.app.security.CustomUserDetailsService;
import com.tenxengage.app.security.JwtTokenProvider;
import com.tenxengage.app.security.PermissionAspect;
import com.tenxengage.app.security.PermissionMetrics;
import com.tenxengage.app.security.TenantValidator;
import com.tenxengage.app.service.ClientService;
import com.tenxengage.app.service.ComplianceCapValidator;
import com.tenxengage.app.service.GovernmentDealService;
import com.tenxengage.app.service.PartnerAcknowledgmentService;
import com.tenxengage.app.service.PermissionService;
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

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AntiBriberyController.class)
@Import({SecurityConfig.class, PermissionAspect.class, AopAutoConfiguration.class})
class AntiBriberyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean private PartnerAcknowledgmentService partnerAcknowledgmentService;
    @MockBean private ComplianceCapValidator complianceCapValidator;
    @MockBean private ComplianceValueCapRepository complianceValueCapRepository;
    @MockBean private GovernmentSegmentConfigRepository governmentSegmentConfigRepository;
    @MockBean private GovernmentDealService governmentDealService;
    @MockBean private TenantValidator tenantValidator;
    @MockBean private PermissionService permissionService;
    @MockBean private PermissionMetrics permissionMetrics;
    @MockBean private JwtTokenProvider jwtTokenProvider;
    @MockBean private CustomUserDetailsService customUserDetailsService;
    @MockBean private ClientService clientService;
    @MockBean private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID CLIENT_ID = UUID.randomUUID();
    private static final UUID PARTNER_ID = UUID.randomUUID();
    private static final UUID INCENTIVE_ID = UUID.randomUUID();

    private void withPermission(String permission) {
        when(tenantValidator.getCurrentUserId()).thenReturn(USER_ID);
        when(tenantValidator.getCurrentClientId()).thenReturn(CLIENT_ID);
        when(permissionService.resolveEffectivePermissions(USER_ID)).thenReturn(Set.of(permission));
    }

    // ── POST /acknowledgments ────────────────────────────────────────────────

    @Test
    void acknowledgeProgram_returns401_whenUnauthenticated() throws Exception {
        mockMvc.perform(post("/api/v1/compliance/anti-bribery/acknowledgments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void acknowledgeProgram_returns200_whenAuthenticated() throws Exception {
        withPermission("action.compliance.anti_bribery.acknowledge");
        when(tenantValidator.getCurrentClientId()).thenReturn(CLIENT_ID);
        when(tenantValidator.getCurrentUserId()).thenReturn(USER_ID);
        when(partnerAcknowledgmentService.acknowledgeProgram(any(), any(), any(), any()))
                .thenReturn(new com.tenxengage.app.entity.PartnerProgramAcknowledgment());

        mockMvc.perform(post("/api/v1/compliance/anti-bribery/acknowledgments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"partnerCompanyId\": \"" + PARTNER_ID + "\", \"incentiveId\": \"" + INCENTIVE_ID + "\"}"))
                .andExpect(status().isOk());
    }

    // ── GET /acknowledgments/incentive/{incentiveId} ─────────────────────────

    @Test
    void getAcknowledgmentsForIncentive_returns401_whenUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/compliance/anti-bribery/acknowledgments/incentive/" + INCENTIVE_ID))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void getAcknowledgmentsForIncentive_returns200_whenAuthenticated() throws Exception {
        withPermission("action.compliance.anti_bribery.view");
        when(partnerAcknowledgmentService.getAcknowledgmentsForIncentive(INCENTIVE_ID))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/v1/compliance/anti-bribery/acknowledgments/incentive/" + INCENTIVE_ID))
                .andExpect(status().isOk());
    }

    // ── GET /acknowledgments/partner/{partnerCompanyId} ──────────────────────

    @Test
    void getAcknowledgmentsForPartner_returns401_whenUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/compliance/anti-bribery/acknowledgments/partner/" + PARTNER_ID))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void getAcknowledgmentsForPartner_returns200_whenAuthenticated() throws Exception {
        withPermission("action.compliance.anti_bribery.view");
        when(partnerAcknowledgmentService.getAcknowledgmentsForPartner(PARTNER_ID))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/v1/compliance/anti-bribery/acknowledgments/partner/" + PARTNER_ID))
                .andExpect(status().isOk());
    }

    // ── GET /value-caps ──────────────────────────────────────────────────────

    @Test
    void getValueCaps_returns401_whenUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/compliance/anti-bribery/value-caps"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void getValueCaps_returns200_whenAuthenticated() throws Exception {
        withPermission("action.compliance.anti_bribery.view");
        when(complianceValueCapRepository.findByClientIdIsNull()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/compliance/anti-bribery/value-caps"))
                .andExpect(status().isOk());
    }

    // ── GET /value-caps/defaults ─────────────────────────────────────────────

    @Test
    void getDefaultValueCaps_returns401_whenUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/compliance/anti-bribery/value-caps/defaults"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void getDefaultValueCaps_returns200_whenAuthenticated() throws Exception {
        withPermission("action.tenx.compliance.manage");
        when(complianceValueCapRepository.findByClientIdIsNull()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/compliance/anti-bribery/value-caps/defaults"))
                .andExpect(status().isOk());
    }

    // ── PUT /value-caps/{countryCode} ────────────────────────────────────────

    @Test
    void updateValueCap_returns401_whenUnauthenticated() throws Exception {
        mockMvc.perform(put("/api/v1/compliance/anti-bribery/value-caps/US")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void updateValueCap_returns200_whenAuthenticated() throws Exception {
        withPermission("action.compliance.anti_bribery.manage");
        when(complianceValueCapRepository.findByCountryCodeAndClientIdIsNull("US"))
                .thenReturn(java.util.Optional.empty());
        when(complianceValueCapRepository.findByCountryCodeAndClientId("US", CLIENT_ID))
                .thenReturn(java.util.Optional.empty());
        when(complianceValueCapRepository.save(any())).thenReturn(new com.tenxengage.app.entity.ComplianceValueCap());

        mockMvc.perform(put("/api/v1/compliance/anti-bribery/value-caps/US")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"annualCapAmount\": 100.00, \"enhancedApprovalThreshold\": 50.00}"))
                .andExpect(status().isOk());
    }

    // ── GET /government-segments ─────────────────────────────────────────────

    @Test
    void getGovernmentSegments_returns401_whenUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/compliance/anti-bribery/government-segments"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void getGovernmentSegments_returns200_whenAuthenticated() throws Exception {
        withPermission("action.compliance.anti_bribery.view");
        when(governmentSegmentConfigRepository.findByClientId(CLIENT_ID)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/compliance/anti-bribery/government-segments"))
                .andExpect(status().isOk());
    }

    // ── PUT /government-segments ─────────────────────────────────────────────

    @Test
    void updateGovernmentSegments_returns401_whenUnauthenticated() throws Exception {
        mockMvc.perform(put("/api/v1/compliance/anti-bribery/government-segments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void updateGovernmentSegments_returns200_whenAuthenticated() throws Exception {
        withPermission("action.compliance.anti_bribery.manage");
        when(governmentDealService.updateGovernmentSegments(any(), any())).thenReturn(List.of());

        mockMvc.perform(put("/api/v1/compliance/anti-bribery/government-segments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"segmentValues\": [\"GOV\", \"PUBLIC_SECTOR\"]}"))
                .andExpect(status().isOk());
    }
}
