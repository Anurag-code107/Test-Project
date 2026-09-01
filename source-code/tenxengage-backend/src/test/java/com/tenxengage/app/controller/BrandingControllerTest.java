package com.tenxengage.app.controller;

import com.tenxengage.app.config.SecurityConfig;
import com.tenxengage.app.security.CustomUserDetailsService;
import com.tenxengage.app.security.JwtTokenProvider;
import com.tenxengage.app.service.BrandingService;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BrandingController.class)
@Import(SecurityConfig.class)
class BrandingControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private BrandingService brandingService;
    @MockBean private PermissionService permissionService;
    @MockBean private JwtTokenProvider jwtTokenProvider;
    @MockBean private CustomUserDetailsService customUserDetailsService;
    @MockBean private ClientService clientService;
    @MockBean private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    // GET /api/v1/branding — public (no @RequiresPermission), still requires auth per SecurityConfig

    @Test
    void getBranding_returns401ForUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/branding"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void getBranding_returns200ForAuthenticatedUser() throws Exception {
        when(brandingService.getBranding()).thenReturn(null);
        mockMvc.perform(get("/api/v1/branding"))
                .andExpect(status().isOk());
    }

    @Test
    void updateBranding_returns401ForUnauthenticated() throws Exception {
        mockMvc.perform(put("/api/v1/branding")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    private static final String BRANDING_BODY =
            "{\"primary\":\"#000000\",\"primaryLight\":\"#111111\",\"secondary\":\"#222222\"," +
            "\"accent\":\"#333333\",\"success\":\"#444444\",\"warning\":\"#555555\"," +
            "\"destructive\":\"#666666\",\"background\":\"#777777\",\"foreground\":\"#888888\"," +
            "\"muted\":\"#999999\",\"mutedForeground\":\"#aaaaaa\",\"card\":\"#bbbbbb\"," +
            "\"cardForeground\":\"#cccccc\",\"border\":\"#dddddd\"," +
            "\"headingFont\":\"Inter\",\"bodyFont\":\"Inter\"}";

    @Test
    @WithMockUser
    void updateBranding_returns200ForAuthenticatedUser() throws Exception {
        when(brandingService.saveBranding(any())).thenReturn(null);
        mockMvc.perform(put("/api/v1/branding")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BRANDING_BODY))
                .andExpect(status().isOk());
    }

    @Test
    void removeLogo_returns401ForUnauthenticated() throws Exception {
        mockMvc.perform(delete("/api/v1/branding/logo"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void removeLogo_returns200ForAuthenticatedUser() throws Exception {
        when(brandingService.removeLogo()).thenReturn(null);
        mockMvc.perform(delete("/api/v1/branding/logo"))
                .andExpect(status().isOk());
    }

    @Test
    void streamLogo_returns401ForUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/branding/logo/file"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void streamLogo_returns200ForAuthenticatedUser() throws Exception {
        BrandingService.LogoStream logoStream = new BrandingService.LogoStream(
                new java.io.ByteArrayInputStream(new byte[0]), "image/png");
        when(brandingService.streamLogo()).thenReturn(logoStream);
        mockMvc.perform(get("/api/v1/branding/logo/file"))
                .andExpect(status().isOk());
    }
}
