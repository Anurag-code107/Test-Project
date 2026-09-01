package com.tenxengage.app.controller;

import com.tenxengage.app.config.SecurityConfig;
import com.tenxengage.app.repository.OnboardingTokenRepository;
import com.tenxengage.app.security.CustomUserDetailsService;
import com.tenxengage.app.security.JwtTokenProvider;
import com.tenxengage.app.service.ClientService;
import com.tenxengage.app.service.OnboardingService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OnboardingController.class)
@Import(SecurityConfig.class)
class OnboardingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private CustomUserDetailsService customUserDetailsService;

    @MockBean
    private ClientService clientService;

    @MockBean
    private OnboardingService onboardingService;

    @MockBean
    private OnboardingTokenRepository onboardingTokenRepository;

    @MockBean
    private org.springframework.data.jpa.mapping.JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @Test
    @DisplayName("POST /api/v1/onboarding/validate is public - returns non-401 with valid body")
    void validateTokenEndpointIsPublic() throws Exception {
        mockMvc.perform(post("/api/v1/onboarding/validate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\": \"test-token-123\"}"))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/v1/onboarding/policies is public - returns non-401 with token param")
    void getPoliciesEndpointIsPublic() throws Exception {
        // The mocked repository returns Optional.empty(), causing a 422 BusinessRuleException.
        // The key assertion is that we do NOT get 401 -- the request passes the security filter.
        mockMvc.perform(get("/api/v1/onboarding/policies")
                .param("token", "abc"))
            .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("POST /api/v1/onboarding/set-password is public - no auth required")
    void setPasswordEndpointIsPublic() throws Exception {
        mockMvc.perform(post("/api/v1/onboarding/set-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\": \"test-token\", \"password\": \"StrongPass1!\"}"))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /api/v1/onboarding/complete is public - no auth required")
    void completeOnboardingEndpointIsPublic() throws Exception {
        mockMvc.perform(post("/api/v1/onboarding/complete")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\": \"test-token\"}"))
            .andExpect(status().isOk());
    }
}
