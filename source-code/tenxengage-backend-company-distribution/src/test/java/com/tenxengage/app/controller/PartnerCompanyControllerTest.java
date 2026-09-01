package com.tenxengage.app.controller;

import com.tenxengage.app.config.SecurityConfig;
import com.tenxengage.app.security.CustomUserDetailsService;
import com.tenxengage.app.security.JwtTokenProvider;
import com.tenxengage.app.service.ClientService;
import com.tenxengage.app.service.PartnerCompanyService;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PartnerCompanyController.class)
@Import(SecurityConfig.class)
class PartnerCompanyControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private PartnerCompanyService partnerCompanyService;
    @MockBean private PermissionService permissionService;
    @MockBean private JwtTokenProvider jwtTokenProvider;
    @MockBean private CustomUserDetailsService customUserDetailsService;
    @MockBean private ClientService clientService;
    @MockBean private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @Test
    void getPartnerCompanies_returns401ForUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/partner-companies")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "USER")
    void getPartnerCompanies_returns200ForAuthenticatedUser() throws Exception {
        mockMvc.perform(get("/api/v1/partner-companies")).andExpect(status().isOk());
    }
}
