package com.tenxengage.app.controller;

import com.tenxengage.app.config.SecurityConfig;
import com.tenxengage.app.dto.request.UpdateSelfProfileRequest;
import com.tenxengage.app.repository.UserRepository;
import com.tenxengage.app.security.CustomUserDetailsService;
import com.tenxengage.app.security.JwtTokenProvider;
import com.tenxengage.app.security.TenantValidator;
import com.tenxengage.app.service.ClientService;
import com.tenxengage.app.service.DataExportService;
import com.tenxengage.app.service.ProfileFieldService;
import com.tenxengage.app.service.UserAnonymizationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DataSubjectRightsController.class)
@Import(SecurityConfig.class)
class DataSubjectRightsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private CustomUserDetailsService customUserDetailsService;

    @MockBean
    private ClientService clientService;

    @MockBean
    private TenantValidator tenantValidator;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private DataExportService dataExportService;

    @MockBean
    private UserAnonymizationService userAnonymizationService;

    @MockBean
    private ProfileFieldService profileFieldService;

    @MockBean
    private org.springframework.data.jpa.mapping.JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @Test
    @DisplayName("PATCH /api/v1/me/profile requires authentication - returns 401 without auth")
    void updateSelfProfileRequiresAuth() throws Exception {
        mockMvc.perform(patch("/api/v1/me/profile")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"firstName\": \"Jane\"}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/v1/me/data-export requires authentication - returns 401 without auth")
    void exportOwnDataRequiresAuth() throws Exception {
        mockMvc.perform(get("/api/v1/me/data-export"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/v1/users/{id}/anonymize returns 200 for authenticated user")
    void anonymizeUserAllowedForAuthenticatedUser() throws Exception {
        UUID userId = UUID.randomUUID();
        mockMvc.perform(post("/api/v1/users/{id}/anonymize", userId)
                .with(SecurityMockMvcRequestPostProcessors.user("admin@test.com").roles("USER")))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("BUG-078 regression: PATCH /me/profile must be @Transactional so UserResponse.from(saved) "
            + "can lazy-load client/partnerCompany during response construction (open-in-view is false)")
    void updateSelfProfileMustBeTransactional() throws NoSuchMethodException {
        Method method = DataSubjectRightsController.class.getMethod(
                "updateSelfProfile", UpdateSelfProfileRequest.class);

        Transactional annotation = method.getAnnotation(Transactional.class);

        assertThat(annotation)
                .as("updateSelfProfile must carry @Transactional. Without it, userRepository.save() "
                        + "commits and closes its auto-transaction before the controller calls "
                        + "UserResponse.from(saved), which dereferences LAZY-loaded relationships "
                        + "(client, partnerCompany) — Hibernate then throws "
                        + "LazyInitializationException and GlobalExceptionHandler returns 500.")
                .isNotNull();
    }
}
