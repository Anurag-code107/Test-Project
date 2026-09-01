package com.tenxengage.app.controller;

import com.tenxengage.app.config.SecurityConfig;
import com.tenxengage.app.repository.KycRegionConfigRepository;
import com.tenxengage.app.repository.PartnerBeneficialOwnerRepository;
import com.tenxengage.app.security.CustomUserDetailsService;
import com.tenxengage.app.security.JwtTokenProvider;
import com.tenxengage.app.security.PermissionAspect;
import com.tenxengage.app.security.PermissionMetrics;
import com.tenxengage.app.security.TenantValidator;
import com.tenxengage.app.service.ClientService;
import com.tenxengage.app.service.ComplianceAlertService;
import com.tenxengage.app.service.PartnerKycService;
import com.tenxengage.app.service.PermissionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(KycController.class)
@Import({SecurityConfig.class, PermissionAspect.class, AopAutoConfiguration.class})
class KycControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean private PartnerKycService partnerKycService;
    @MockBean private ComplianceAlertService complianceAlertService;
    @MockBean private KycRegionConfigRepository kycRegionConfigRepository;
    @MockBean private PartnerBeneficialOwnerRepository beneficialOwnerRepository;
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
    private static final UUID ALERT_ID = UUID.randomUUID();

    private void withPermission(String permission) {
        when(tenantValidator.getCurrentUserId()).thenReturn(USER_ID);
        when(tenantValidator.getCurrentClientId()).thenReturn(CLIENT_ID);
        when(permissionService.resolveEffectivePermissions(USER_ID)).thenReturn(Set.of(permission));
    }

    // ── POST /partner/{partnerCompanyId} ─────────────────────────────────────

    @Test
    void submitKyc_returns401_whenUnauthenticated() throws Exception {
        mockMvc.perform(post("/api/v1/compliance/kyc/partner/" + PARTNER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void submitKyc_returns200_whenAuthenticated() throws Exception {
        withPermission("action.compliance.kyc.submit");
        com.tenxengage.app.entity.PartnerKycRecord record = new com.tenxengage.app.entity.PartnerKycRecord();
        record.setId(UUID.randomUUID());
        when(partnerKycService.initiateKyc(eq(PARTNER_ID), eq(CLIENT_ID), any()))
                .thenReturn(record);
        when(beneficialOwnerRepository.findByKycRecordId(any())).thenReturn(List.of());

        mockMvc.perform(post("/api/v1/compliance/kyc/partner/" + PARTNER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"legalEntityName\": \"Acme Corp\", \"incorporationCountry\": \"US\", " +
                                 "\"registrationNumber\": \"123456\", \"beneficialOwners\": []}"))
                .andExpect(status().isOk());
    }

    // ── GET /partner/{partnerCompanyId} ──────────────────────────────────────

    @Test
    void getKycStatus_returns401_whenUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/compliance/kyc/partner/" + PARTNER_ID))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void getKycStatus_returns200_whenAuthenticated() throws Exception {
        withPermission("action.compliance.kyc.view");
        com.tenxengage.app.entity.PartnerKycRecord record = new com.tenxengage.app.entity.PartnerKycRecord();
        record.setId(UUID.randomUUID());
        when(partnerKycService.getKycRecord(PARTNER_ID)).thenReturn(Optional.of(record));
        when(beneficialOwnerRepository.findByKycRecordId(any())).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/compliance/kyc/partner/" + PARTNER_ID))
                .andExpect(status().isOk());
    }

    // ── POST /partner/{partnerCompanyId}/approve ─────────────────────────────

    @Test
    void approveKyc_returns401_whenUnauthenticated() throws Exception {
        mockMvc.perform(post("/api/v1/compliance/kyc/partner/" + PARTNER_ID + "/approve"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void approveKyc_returns200_whenAuthenticated() throws Exception {
        withPermission("action.compliance.kyc.review");
        com.tenxengage.app.entity.PartnerKycRecord record = new com.tenxengage.app.entity.PartnerKycRecord();
        record.setId(UUID.randomUUID());
        when(partnerKycService.approveKyc(eq(PARTNER_ID), eq(USER_ID))).thenReturn(record);
        when(beneficialOwnerRepository.findByKycRecordId(any())).thenReturn(List.of());

        mockMvc.perform(post("/api/v1/compliance/kyc/partner/" + PARTNER_ID + "/approve"))
                .andExpect(status().isOk());
    }

    // ── POST /partner/{partnerCompanyId}/reject ──────────────────────────────

    @Test
    void rejectKyc_returns401_whenUnauthenticated() throws Exception {
        mockMvc.perform(post("/api/v1/compliance/kyc/partner/" + PARTNER_ID + "/reject")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void rejectKyc_returns200_whenAuthenticated() throws Exception {
        withPermission("action.compliance.kyc.review");
        com.tenxengage.app.entity.PartnerKycRecord record = new com.tenxengage.app.entity.PartnerKycRecord();
        record.setId(UUID.randomUUID());
        when(partnerKycService.rejectKyc(eq(PARTNER_ID), any())).thenReturn(record);
        when(beneficialOwnerRepository.findByKycRecordId(any())).thenReturn(List.of());

        mockMvc.perform(post("/api/v1/compliance/kyc/partner/" + PARTNER_ID + "/reject")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\": \"Incomplete documents\"}"))
                .andExpect(status().isOk());
    }

    // ── GET /alerts ──────────────────────────────────────────────────────────

    @Test
    void getAlerts_returns401_whenUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/compliance/kyc/alerts"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void getAlerts_returns200_whenAuthenticated() throws Exception {
        withPermission("action.compliance.kyc.review");
        when(complianceAlertService.getAlerts(eq(CLIENT_ID), any()))
                .thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/v1/compliance/kyc/alerts"))
                .andExpect(status().isOk());
    }

    // ── POST /alerts/{alertId}/resolve ───────────────────────────────────────

    @Test
    void resolveAlert_returns401_whenUnauthenticated() throws Exception {
        mockMvc.perform(post("/api/v1/compliance/kyc/alerts/" + ALERT_ID + "/resolve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void resolveAlert_returns200_whenAuthenticated() throws Exception {
        withPermission("action.compliance.kyc.review");
        com.tenxengage.app.entity.ComplianceAlert alert = new com.tenxengage.app.entity.ComplianceAlert();
        alert.setAlertType(com.tenxengage.app.entity.enums.ComplianceAlertType.KYC_EXPIRED);
        alert.setStatus(com.tenxengage.app.entity.enums.ComplianceAlertStatus.RESOLVED);
        when(complianceAlertService.resolveAlert(eq(ALERT_ID), eq(USER_ID), any()))
                .thenReturn(alert);

        mockMvc.perform(post("/api/v1/compliance/kyc/alerts/" + ALERT_ID + "/resolve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"notes\": \"Resolved after review\"}"))
                .andExpect(status().isOk());
    }

    // ── GET /risk-config ─────────────────────────────────────────────────────

    @Test
    void getRiskConfig_returns401_whenUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/compliance/kyc/risk-config"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void getRiskConfig_returns200_whenAuthenticated() throws Exception {
        withPermission("action.compliance.kyc.view");
        when(kycRegionConfigRepository.findAll()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/compliance/kyc/risk-config"))
                .andExpect(status().isOk());
    }
}
