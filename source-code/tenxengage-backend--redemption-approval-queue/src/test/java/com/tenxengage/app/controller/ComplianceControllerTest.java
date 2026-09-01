package com.tenxengage.app.controller;

import com.tenxengage.app.config.SecurityConfig;
import com.tenxengage.app.security.CustomUserDetailsService;
import com.tenxengage.app.security.JwtTokenProvider;
import com.tenxengage.app.security.TenantValidator;
import com.tenxengage.app.service.ClientService;
import com.tenxengage.app.service.BreachIncidentService;
import com.tenxengage.app.service.DataRetentionService;
import com.tenxengage.app.service.SubProcessorService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ComplianceController.class)
@Import(SecurityConfig.class)
class ComplianceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private CustomUserDetailsService customUserDetailsService;

    @MockBean
    private ClientService clientService;

    @MockBean
    private TenantValidator tenantValidator;

    @MockBean
    private DataRetentionService dataRetentionService;

    @MockBean
    private SubProcessorService subProcessorService;

    @MockBean
    private BreachIncidentService breachIncidentService;

    @MockBean
    private org.springframework.data.jpa.mapping.JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @Test
    @DisplayName("GET /api/v1/compliance/retention-policies requires auth - returns 401 without auth")
    void retentionPoliciesRequiresAuth() throws Exception {
        mockMvc.perform(get("/api/v1/compliance/retention-policies"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/v1/compliance/retention-policies returns 200 for authenticated user")
    void retentionPoliciesAccessibleForAuthenticatedUser() throws Exception {
        mockMvc.perform(get("/api/v1/compliance/retention-policies")
                .with(SecurityMockMvcRequestPostProcessors.user("seller@test.com").roles("USER")))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/v1/compliance/sub-processors returns list for authenticated user")
    void subProcessorsReturnsListForAuthenticatedUser() throws Exception {
        when(subProcessorService.getAll()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/compliance/sub-processors")
                .with(SecurityMockMvcRequestPostProcessors.user("admin@test.com").roles("USER")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @DisplayName("GET /api/v1/compliance/sub-processors requires auth - returns 401 without auth")
    void subProcessorsRequiresAuth() throws Exception {
        mockMvc.perform(get("/api/v1/compliance/sub-processors"))
            .andExpect(status().isUnauthorized());
    }
}
