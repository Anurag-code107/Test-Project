package com.tenxengage.app.controller;

import com.tenxengage.app.config.SecurityConfig;
import com.tenxengage.app.repository.UserRepository;
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
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PartnerUserController.class)
@Import(SecurityConfig.class)
class PartnerUserControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private UserRepository userRepository;
    @MockBean private PermissionService permissionService;
    @MockBean private TenantValidator tenantValidator;
    @MockBean private JwtTokenProvider jwtTokenProvider;
    @MockBean private CustomUserDetailsService customUserDetailsService;
    @MockBean private ClientService clientService;
    @MockBean private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    private static final UUID CLIENT_ID = UUID.randomUUID();

    // GET /api/v1/partner-users
    @Test
    void listPartnerUsers_returns401ForUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/partner-users"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void listPartnerUsers_returns200ForAuthenticatedUser() throws Exception {
        com.tenxengage.app.security.CustomUserDetails mockDetails =
                org.mockito.Mockito.mock(com.tenxengage.app.security.CustomUserDetails.class);
        when(mockDetails.getPartnerCompanyId()).thenReturn(UUID.randomUUID());
        when(tenantValidator.getCurrentClientId()).thenReturn(UUID.randomUUID());
        when(tenantValidator.getCurrentUserDetails()).thenReturn(mockDetails);
        when(userRepository.findByClientIdAndPartnerCompanyId(any(), any()))
                .thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/v1/partner-users"))
                .andExpect(status().isOk());
    }

    // GET /api/v1/partner-users/{userId}/permissions
    @Test
    void getSellerPermissions_returns401ForUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/partner-users/{userId}/permissions", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void getSellerPermissions_returns200ForAuthenticatedUser() throws Exception {
        UUID userId = UUID.randomUUID();
        com.tenxengage.app.security.CustomUserDetails mockDetails =
                org.mockito.Mockito.mock(com.tenxengage.app.security.CustomUserDetails.class);
        UUID partnerCompanyId = UUID.randomUUID();
        when(mockDetails.getPartnerCompanyId()).thenReturn(partnerCompanyId);
        when(tenantValidator.getCurrentClientId()).thenReturn(CLIENT_ID);
        when(tenantValidator.getCurrentUserDetails()).thenReturn(mockDetails);

        com.tenxengage.app.entity.User targetUser = org.mockito.Mockito.mock(com.tenxengage.app.entity.User.class);
        when(targetUser.getPartnerCompanyId()).thenReturn(partnerCompanyId);
        when(targetUser.getClientId()).thenReturn(CLIENT_ID);
        when(userRepository.findById(userId)).thenReturn(java.util.Optional.of(targetUser));
        when(permissionService.resolveEffectivePermissions(userId))
                .thenReturn(java.util.Collections.emptySet());

        mockMvc.perform(get("/api/v1/partner-users/{userId}/permissions", userId))
                .andExpect(status().isOk());
    }

    // PUT /api/v1/partner-users/{userId}/permissions
    @Test
    void updateSellerPermissions_returns401ForUnauthenticated() throws Exception {
        mockMvc.perform(put("/api/v1/partner-users/{userId}/permissions", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"permissions\":{\"action.data.view\":true}}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void updateSellerPermissions_returns400ForInvalidBody() throws Exception {
        mockMvc.perform(put("/api/v1/partner-users/{userId}/permissions", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }
}
