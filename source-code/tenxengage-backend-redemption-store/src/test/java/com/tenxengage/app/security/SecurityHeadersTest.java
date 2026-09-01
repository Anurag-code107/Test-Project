package com.tenxengage.app.security;

import com.tenxengage.app.config.SecurityConfig;
import com.tenxengage.app.controller.HealthController;
import com.tenxengage.app.service.ClientService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase K: Verifies security headers are set on all responses.
 */
@WebMvcTest(HealthController.class)
@Import(SecurityConfig.class)
class SecurityHeadersTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;
    @MockBean
    private CustomUserDetailsService customUserDetailsService;
    @MockBean
    private ClientService clientService;
    @MockBean
    private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @Test
    void response_containsXFrameOptionsDeny() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Frame-Options", "DENY"));
    }

    @Test
    void response_containsNoSniff() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }

    @Test
    void response_containsContentSecurityPolicy() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(header().string("Content-Security-Policy",
                        "default-src 'self'; frame-ancestors 'none'"));
    }

    @Test
    void response_containsReferrerPolicy() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(header().string("Referrer-Policy", "strict-origin-when-cross-origin"));
    }

    @Test
    void response_containsPermissionsPolicy() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(header().string("Permissions-Policy",
                        "camera=(), microphone=(), geolocation=()"));
    }

    @Test
    void response_containsHstsHeader() throws Exception {
        // HSTS is only sent over HTTPS; use secure request
        mockMvc.perform(get("/api/v1/health").secure(true))
                .andExpect(header().exists("Strict-Transport-Security"));
    }
}
