package com.tenxengage.app.controller;

import com.tenxengage.app.config.SecurityConfig;
import com.tenxengage.app.security.CustomUserDetailsService;
import com.tenxengage.app.security.JwtTokenProvider;
import com.tenxengage.app.security.TenantValidator;
import com.tenxengage.app.service.ClientService;
import com.tenxengage.app.service.PermissionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PermissionController.class)
@Import(SecurityConfig.class)
class PermissionControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private PermissionService permissionService;
    @MockBean private TenantValidator tenantValidator;
    @MockBean private JwtTokenProvider jwtTokenProvider;
    @MockBean private CustomUserDetailsService customUserDetailsService;
    @MockBean private ClientService clientService;
    @MockBean private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    // GET /api/v1/permissions
    @Test
    void getAllPermissions_returns401ForUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/permissions"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void getAllPermissions_returns200ForAuthenticatedUser() throws Exception {
        when(permissionService.getAllPermissions()).thenReturn(Collections.emptyList());
        mockMvc.perform(get("/api/v1/permissions"))
                .andExpect(status().isOk());
    }

    // GET /api/v1/permissions/effective
    @Test
    void getMyEffectivePermissions_returns401ForUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/permissions/effective"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void getMyEffectivePermissions_returns200ForAuthenticatedUser() throws Exception {
        when(tenantValidator.getCurrentUserId()).thenReturn(UUID.randomUUID());
        when(permissionService.resolveEffectivePermissions(any(UUID.class)))
                .thenReturn(Collections.emptySet());
        mockMvc.perform(get("/api/v1/permissions/effective"))
                .andExpect(status().isOk());
    }

    // GET /api/v1/permissions/effective/{userId}
    @Test
    void getUserEffectivePermissions_returns401ForUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/permissions/effective/{userId}", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void getUserEffectivePermissions_returns200ForAuthenticatedUser() throws Exception {
        when(permissionService.resolveEffectivePermissions(any(UUID.class)))
                .thenReturn(Collections.emptySet());
        mockMvc.perform(get("/api/v1/permissions/effective/{userId}", UUID.randomUUID()))
                .andExpect(status().isOk());
    }
}
