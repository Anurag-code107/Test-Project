package com.tenxengage.app.controller;

import com.tenxengage.app.config.SecurityConfig;
import com.tenxengage.app.security.CustomUserDetailsService;
import com.tenxengage.app.security.JwtTokenProvider;
import com.tenxengage.app.service.ClientService;
import com.tenxengage.app.service.FiscalYearConfigService;
import com.tenxengage.app.service.PermissionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FiscalYearConfigController.class)
@Import(SecurityConfig.class)
class FiscalYearConfigControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private FiscalYearConfigService fiscalYearConfigService;
    @MockBean private PermissionService permissionService;
    @MockBean private JwtTokenProvider jwtTokenProvider;
    @MockBean private CustomUserDetailsService customUserDetailsService;
    @MockBean private ClientService clientService;
    @MockBean private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    // GET /api/v1/fiscal-year-configs
    @Test
    void listConfigs_returns401ForUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/fiscal-year-configs"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void listConfigs_returns200ForAuthenticatedUser() throws Exception {
        when(fiscalYearConfigService.listConfigs()).thenReturn(Collections.emptyList());
        mockMvc.perform(get("/api/v1/fiscal-year-configs"))
                .andExpect(status().isOk());
    }

    // GET /api/v1/fiscal-year-configs/labels
    @Test
    void listLabels_returns401ForUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/fiscal-year-configs/labels"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void listLabels_returns200ForAuthenticatedUser() throws Exception {
        when(fiscalYearConfigService.listLabels()).thenReturn(Collections.emptyList());
        mockMvc.perform(get("/api/v1/fiscal-year-configs/labels"))
                .andExpect(status().isOk());
    }

    // GET /api/v1/fiscal-year-configs/current
    @Test
    void getCurrentConfig_returns401ForUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/fiscal-year-configs/current"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void getCurrentConfig_returns200ForAuthenticatedUser() throws Exception {
        when(fiscalYearConfigService.getCurrentConfig()).thenReturn(null);
        mockMvc.perform(get("/api/v1/fiscal-year-configs/current"))
                .andExpect(status().isOk());
    }

    // GET /api/v1/fiscal-year-configs/by-label/{label}
    @Test
    void getConfigByLabel_returns401ForUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/fiscal-year-configs/by-label/FY2024"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void getConfigByLabel_returns200ForAuthenticatedUser() throws Exception {
        when(fiscalYearConfigService.getConfigByLabel("FY2024")).thenReturn(null);
        mockMvc.perform(get("/api/v1/fiscal-year-configs/by-label/FY2024"))
                .andExpect(status().isOk());
    }

    // GET /api/v1/fiscal-year-configs/{id}
    @Test
    void getConfig_returns401ForUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/fiscal-year-configs/{id}", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void getConfig_returns200ForAuthenticatedUser() throws Exception {
        when(fiscalYearConfigService.getConfig(any(UUID.class))).thenReturn(null);
        mockMvc.perform(get("/api/v1/fiscal-year-configs/{id}", UUID.randomUUID()))
                .andExpect(status().isOk());
    }

    // POST /api/v1/fiscal-year-configs
    @Test
    void createConfig_returns401ForUnauthenticated() throws Exception {
        mockMvc.perform(post("/api/v1/fiscal-year-configs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"label\":\"FY2024\",\"startDate\":\"2024-01-01\",\"endDate\":\"2024-12-31\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void createConfig_returns400ForInvalidBody() throws Exception {
        mockMvc.perform(post("/api/v1/fiscal-year-configs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    // PUT /api/v1/fiscal-year-configs/{id}
    @Test
    void updateConfig_returns401ForUnauthenticated() throws Exception {
        mockMvc.perform(put("/api/v1/fiscal-year-configs/{id}", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"label\":\"FY2024\",\"startDate\":\"2024-01-01\",\"endDate\":\"2024-12-31\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void updateConfig_returns400ForInvalidBody() throws Exception {
        mockMvc.perform(put("/api/v1/fiscal-year-configs/{id}", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    // DELETE /api/v1/fiscal-year-configs/{id}
    @Test
    void deleteConfig_returns401ForUnauthenticated() throws Exception {
        mockMvc.perform(delete("/api/v1/fiscal-year-configs/{id}", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void deleteConfig_returns204ForAuthenticatedUser() throws Exception {
        mockMvc.perform(delete("/api/v1/fiscal-year-configs/{id}", UUID.randomUUID()))
                .andExpect(status().isNoContent());
    }
}
