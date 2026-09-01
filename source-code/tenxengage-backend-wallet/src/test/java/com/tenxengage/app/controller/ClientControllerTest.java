package com.tenxengage.app.controller;

import com.tenxengage.app.config.SecurityConfig;
import com.tenxengage.app.security.CustomUserDetailsService;
import com.tenxengage.app.security.JwtTokenProvider;
import com.tenxengage.app.service.ClientService;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ClientController.class)
@Import(SecurityConfig.class)
class ClientControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private ClientService clientService;
    @MockBean private com.tenxengage.app.service.FeatureFlagService featureFlagService;
    @MockBean private PermissionService permissionService;
    @MockBean private JwtTokenProvider jwtTokenProvider;
    @MockBean private CustomUserDetailsService customUserDetailsService;
    @MockBean private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @Test
    void getClients_returns401ForUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/clients")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "USER")
    void getClients_returns200ForAuthenticatedUser() throws Exception {
        mockMvc.perform(get("/api/v1/clients")).andExpect(status().isOk());
    }

    @Test
    void createClient_returns401ForUnauthenticated() throws Exception {
        mockMvc.perform(post("/api/v1/clients")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Test\",\"subdomain\":\"test\",\"subscriptionTier\":\"ENTERPRISE\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deleteClient_returns401ForUnauthenticated() throws Exception {
        mockMvc.perform(delete("/api/v1/clients/{id}", java.util.UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }
}
