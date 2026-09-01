package com.tenxengage.app.controller;

import com.tenxengage.app.config.SecurityConfig;
import com.tenxengage.app.entity.HomeDashboardTemplate;
import com.tenxengage.app.security.CustomUserDetailsService;
import com.tenxengage.app.security.JwtTokenProvider;
import com.tenxengage.app.security.TenantValidator;
import com.tenxengage.app.service.ClientService;
import com.tenxengage.app.service.HomeDashboardTemplateService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(HomeDashboardTemplateController.class)
@Import(SecurityConfig.class)
class HomeDashboardTemplateControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private HomeDashboardTemplateService templateService;
    @MockBean
    private TenantValidator tenantValidator;
    @MockBean
    private JwtTokenProvider jwtTokenProvider;
    @MockBean
    private CustomUserDetailsService customUserDetailsService;
    @MockBean
    private ClientService clientService;
    @MockBean
    private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    private final UUID tenantId = UUID.fromString("a0000000-0000-0000-0000-000000000001");

    // -------------------------------------------------------------------------
    // GET /api/v1/home-dashboard-templates
    // -------------------------------------------------------------------------

    @Test
    void listTemplates_returns401ForUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/home-dashboard-templates"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "USER")
    void listTemplates_returnsTenantTemplates() throws Exception {
        when(tenantValidator.getCurrentClientId()).thenReturn(tenantId);
        when(templateService.listForTenant(tenantId)).thenReturn(List.of(seeded("Client Admin", "INTERNAL")));

        mockMvc.perform(get("/api/v1/home-dashboard-templates"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].name").value("Client Admin"))
                .andExpect(jsonPath("$.data[0].roleType").value("INTERNAL"));
    }

    @Test
    @WithMockUser(roles = "USER")
    void listTemplates_withRoleTypeFilter_delegatesToFilteredMethod() throws Exception {
        when(tenantValidator.getCurrentClientId()).thenReturn(tenantId);
        when(templateService.listForTenantAndRoleType(tenantId, "EXTERNAL"))
                .thenReturn(List.of(seeded("Partner User", "EXTERNAL")));

        mockMvc.perform(get("/api/v1/home-dashboard-templates").param("roleType", "EXTERNAL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].name").value("Partner User"))
                .andExpect(jsonPath("$.data[0].roleType").value("EXTERNAL"));
    }

    // -------------------------------------------------------------------------
    // GET /api/v1/home-dashboard-widgets
    // -------------------------------------------------------------------------

    @Test
    void listWidgets_returns401ForUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/home-dashboard-widgets"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "USER")
    void listWidgets_returnsFullCatalog() throws Exception {
        mockMvc.perform(get("/api/v1/home-dashboard-widgets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.key=='ai_assistant')].supportedRoleTypes").exists())
                .andExpect(jsonPath("$.data[?(@.key=='program_performance')]").exists())
                .andExpect(jsonPath("$.data[?(@.key=='rewards_balances')]").exists())
                .andExpect(jsonPath("$.data[?(@.key=='tenx_suggestions')]").exists())
                .andExpect(jsonPath("$.data[?(@.key=='approvals')]").exists());
    }

    // -------------------------------------------------------------------------
    // GET /api/v1/home-dashboard-layouts
    // -------------------------------------------------------------------------

    @Test
    void listLayouts_returns401ForUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/home-dashboard-layouts"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "USER")
    void listLayouts_returnsFullCatalog() throws Exception {
        mockMvc.perform(get("/api/v1/home-dashboard-layouts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.key=='full')].slotCount").value(1))
                .andExpect(jsonPath("$.data[?(@.key=='half-half')].slotCount").value(2));
    }

    private HomeDashboardTemplate seeded(String name, String roleType) {
        HomeDashboardTemplate t = HomeDashboardTemplate.builder()
                .clientId(tenantId)
                .name(name)
                .roleType(roleType)
                .layout("{\"rows\":[{\"layout\":\"full\",\"slots\":[{\"widgetKey\":\"ai_assistant\"}]}]}")
                .system(true)
                .build();
        t.setId(UUID.randomUUID());
        return t;
    }
}
