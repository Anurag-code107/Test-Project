package com.tenxengage.app.controller;

import com.tenxengage.app.config.SecurityConfig;
import com.tenxengage.app.security.CustomUserDetailsService;
import com.tenxengage.app.security.JwtTokenProvider;
import com.tenxengage.app.service.ClientService;
import com.tenxengage.app.service.ConnectorService;
import com.tenxengage.app.service.PermissionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ConnectorController.class)
@Import(SecurityConfig.class)
class ConnectorControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private ConnectorService connectorService;
    @MockBean private PermissionService permissionService;
    @MockBean private JwtTokenProvider jwtTokenProvider;
    @MockBean private CustomUserDetailsService customUserDetailsService;
    @MockBean private ClientService clientService;
    @MockBean private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @Test
    void getConnectors_returns401ForUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/connectors")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "USER")
    void createConnector_returns201ForAuthenticatedUser() throws Exception {
        mockMvc.perform(post("/api/v1/connectors")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{\"connectorType\":\"SALESFORCE\",\"name\":\"Test\",\"config\":{}}"))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(roles = "USER")
    void getConnectors_returns200ForAuthenticatedUser() throws Exception {
        mockMvc.perform(get("/api/v1/connectors")).andExpect(status().isOk());
    }
}
