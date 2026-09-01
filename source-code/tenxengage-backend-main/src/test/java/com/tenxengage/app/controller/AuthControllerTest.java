package com.tenxengage.app.controller;

import com.tenxengage.app.config.SecurityConfig;
import com.tenxengage.app.dto.response.LoginResponse;
import com.tenxengage.app.dto.response.UserResponse;
import com.tenxengage.app.security.CookieUtil;
import com.tenxengage.app.security.CustomUserDetailsService;
import com.tenxengage.app.security.JwtTokenProvider;
import com.tenxengage.app.service.AuthService;
import com.tenxengage.app.service.ClientService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import com.tenxengage.app.exception.AuthenticationFailedException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import(SecurityConfig.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuthService authService;
    @MockBean
    private CookieUtil cookieUtil;
    @MockBean
    private JwtTokenProvider jwtTokenProvider;
    @MockBean
    private CustomUserDetailsService customUserDetailsService;
    @MockBean
    private ClientService clientService;
    @MockBean
    private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @Test
    void login_returns200WithValidCredentials() throws Exception {
        LoginResponse loginResponse = new LoginResponse(3600000L, null, List.of("AI_CHAT"));
        AuthService.AuthResult authResult = new AuthService.AuthResult(loginResponse, "access", "refresh");

        when(authService.login(any())).thenReturn(authResult);
        when(jwtTokenProvider.getAccessTokenExpirationMs()).thenReturn(3600000L);
        when(jwtTokenProvider.getRefreshTokenExpirationMs()).thenReturn(604800000L);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"user@test.com\",\"password\":\"password123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expiresIn").value(3600000));
    }

    @Test
    void login_returns400WhenEmailMissing() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"password123\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void login_returns400WhenPasswordTooShort() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"user@test.com\",\"password\":\"short\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void refresh_returns401WhenNoCookie() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logout_returns401WhenUnauthenticated() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void logout_returns200WhenAuthenticated() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout"))
                .andExpect(status().isOk());

        verify(cookieUtil).clearAuthCookies(any());
    }

    @Test
    void me_returns401WhenUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void me_returns200WhenAuthenticated() throws Exception {
        when(authService.getCurrentUser()).thenReturn(null);

        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isOk());
    }

    // -------------------------------------------------------------------------
    // Phase I: Input validation / injection prevention
    // SQL injection payloads pass @NotBlank but are safely handled (401, not 500)
    // -------------------------------------------------------------------------

    @Test
    void login_sqlInjectionInEmail_doesNotCause500() throws Exception {
        when(authService.login(any())).thenThrow(new AuthenticationFailedException("Invalid"));

        int status = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"' OR '1'='1\",\"password\":\"password123\"}"))
                .andReturn().getResponse().getStatus();
        assertThat(status).isNotEqualTo(500);
    }

    @Test
    void login_xssInEmail_doesNotCause500() throws Exception {
        when(authService.login(any())).thenThrow(new AuthenticationFailedException("Invalid"));

        int status = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"<script>alert('xss')</script>\",\"password\":\"password123\"}"))
                .andReturn().getResponse().getStatus();
        assertThat(status).isNotEqualTo(500);
    }

    @Test
    void login_emptyBody_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void login_nullBody_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }
}
