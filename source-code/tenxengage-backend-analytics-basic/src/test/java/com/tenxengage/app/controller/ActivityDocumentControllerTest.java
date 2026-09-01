package com.tenxengage.app.controller;

import com.tenxengage.app.config.SecurityConfig;
import com.tenxengage.app.security.CustomUserDetailsService;
import com.tenxengage.app.security.JwtTokenProvider;
import com.tenxengage.app.security.TenantValidator;
import com.tenxengage.app.service.ActivityDocumentService;
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

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ActivityDocumentController.class)
@Import(SecurityConfig.class)
class ActivityDocumentControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private ActivityDocumentService activityDocumentService;
    @MockBean private TenantValidator tenantValidator;
    @MockBean private PermissionService permissionService;
    @MockBean private JwtTokenProvider jwtTokenProvider;
    @MockBean private CustomUserDetailsService customUserDetailsService;
    @MockBean private ClientService clientService;
    @MockBean private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    private static final UUID ACTIVITY_ID = UUID.randomUUID();
    private static final UUID SUBMISSION_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();

    @Test
    void submitDocument_returns401ForUnauthenticated() throws Exception {
        mockMvc.perform(post("/api/v1/activity-documents/{id}/submit", ACTIVITY_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void submitDocument_returns200ForAuthenticatedUser() throws Exception {
        when(tenantValidator.getCurrentUserId()).thenReturn(USER_ID);
        when(activityDocumentService.submitDocument(any(), any(), any(), any(), any(), any()))
                .thenReturn(null);
        mockMvc.perform(post("/api/v1/activity-documents/{id}/submit", ACTIVITY_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"documentRequirementId\":\"" + UUID.randomUUID() + "\","
                                + "\"fileName\":\"test.pdf\","
                                + "\"filePath\":\"/files/test.pdf\","
                                + "\"fileSize\":1024}"))
                .andExpect(status().isOk());
    }

    @Test
    void getSubmissions_returns401ForUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/activity-documents/{id}/submissions", ACTIVITY_ID))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void getSubmissions_returns200ForAuthenticatedUser() throws Exception {
        when(tenantValidator.getCurrentUserId()).thenReturn(USER_ID);
        when(activityDocumentService.getSubmissions(any(), any())).thenReturn(List.of());
        mockMvc.perform(get("/api/v1/activity-documents/{id}/submissions", ACTIVITY_ID))
                .andExpect(status().isOk());
    }

    @Test
    void reviewSubmission_returns401ForUnauthenticated() throws Exception {
        mockMvc.perform(put("/api/v1/activity-documents/submissions/{id}/review", SUBMISSION_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"APPROVED\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void reviewSubmission_returns200ForAuthenticatedUser() throws Exception {
        when(tenantValidator.getCurrentUserId()).thenReturn(USER_ID);
        when(activityDocumentService.reviewSubmission(any(), any(), any(), any())).thenReturn(null);
        mockMvc.perform(put("/api/v1/activity-documents/submissions/{id}/review", SUBMISSION_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"APPROVED\"}"))
                .andExpect(status().isOk());
    }
}
