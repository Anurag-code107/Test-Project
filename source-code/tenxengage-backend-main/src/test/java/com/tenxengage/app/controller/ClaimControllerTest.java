package com.tenxengage.app.controller;

import com.tenxengage.app.config.SecurityConfig;
import com.tenxengage.app.security.CustomUserDetailsService;
import com.tenxengage.app.security.JwtTokenProvider;
import com.tenxengage.app.service.ClaimService;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ClaimController.class)
@Import(SecurityConfig.class)
class ClaimControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private ClaimService claimService;
    @MockBean private PermissionService permissionService;
    @MockBean private JwtTokenProvider jwtTokenProvider;
    @MockBean private CustomUserDetailsService customUserDetailsService;
    @MockBean private ClientService clientService;
    @MockBean private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @Test
    void getClaims_returns401ForUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/claims")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "PARTNER_SELLER")
    void getClaims_returns200ForPartnerSeller() throws Exception {
        mockMvc.perform(get("/api/v1/claims")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "CLIENT_ADMIN")
    void getClaims_returns200ForClientAdmin() throws Exception {
        mockMvc.perform(get("/api/v1/claims")).andExpect(status().isOk());
    }

    @Test
    void claimDeal_returns401ForUnauthenticated() throws Exception {
        mockMvc.perform(post("/api/v1/claims/{poId}/claim", java.util.UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }
}
