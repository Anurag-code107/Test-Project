package com.tenxengage.app.controller;

import com.tenxengage.app.config.SecurityConfig;
import com.tenxengage.app.security.CustomUserDetailsService;
import com.tenxengage.app.security.JwtTokenProvider;
import com.tenxengage.app.security.TenantValidator;
import com.tenxengage.app.service.ClientService;
import com.tenxengage.app.service.HomeDashboardTemplateService;
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

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ClientRoleController.class)
@Import(SecurityConfig.class)
class ClientRoleControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PermissionService permissionService;
    @MockBean
    private TenantValidator tenantValidator;
    @MockBean
    private JwtTokenProvider jwtTokenProvider;
    @MockBean
    private CustomUserDetailsService customUserDetailsService;
    @MockBean
    private ClientService clientService;
    @MockBean
    private HomeDashboardTemplateService homeDashboardTemplateService;
    @MockBean
    private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    // -------------------------------------------------------------------------
    // POST /api/v1/client-roles (create)
    // -------------------------------------------------------------------------

    @Test
    void createRole_returns401ForUnauthenticated() throws Exception {
        mockMvc.perform(post("/api/v1/client-roles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Test\",\"roleType\":\"EXTERNAL\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "USER")
    void createRole_returns400ForMissingName() throws Exception {
        mockMvc.perform(post("/api/v1/client-roles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleType\":\"EXTERNAL\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "USER")
    void createRole_returns400ForMissingRoleType() throws Exception {
        mockMvc.perform(post("/api/v1/client-roles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Test\"}"))
                .andExpect(status().isBadRequest());
    }

    // -------------------------------------------------------------------------
    // PUT /api/v1/client-roles/{id}/dashboard-template
    // -------------------------------------------------------------------------

    @Test
    void assignDashboardTemplate_returns401ForUnauthenticated() throws Exception {
        mockMvc.perform(put("/api/v1/client-roles/{id}/dashboard-template", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"templateId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "USER")
    void assignDashboardTemplate_returns400ForMissingTemplateId() throws Exception {
        mockMvc.perform(put("/api/v1/client-roles/{id}/dashboard-template", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void clearDashboardTemplate_returns401ForUnauthenticated() throws Exception {
        mockMvc.perform(delete("/api/v1/client-roles/{id}/dashboard-template", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    // -------------------------------------------------------------------------
    // POST /api/v1/client-roles/{id}/clone
    // -------------------------------------------------------------------------

    @Test
    void cloneRole_returns401ForUnauthenticated() throws Exception {
        mockMvc.perform(post("/api/v1/client-roles/{id}/clone", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Cloned Role\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "USER")
    void cloneRole_returns400ForMissingName() throws Exception {
        mockMvc.perform(post("/api/v1/client-roles/{id}/clone", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"no name\"}"))
                .andExpect(status().isBadRequest());
    }

    // -------------------------------------------------------------------------
    // DELETE /api/v1/client-roles/{id}
    // -------------------------------------------------------------------------

    @Test
    void deleteRole_returns401ForUnauthenticated() throws Exception {
        mockMvc.perform(delete("/api/v1/client-roles/{id}", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }
}
