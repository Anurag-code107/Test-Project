package com.tenxengage.app.controller;

import com.tenxengage.app.config.SecurityConfig;
import com.tenxengage.app.security.CustomUserDetailsService;
import com.tenxengage.app.security.JwtTokenProvider;
import com.tenxengage.app.service.ApprovalService;
import com.tenxengage.app.service.ApprovalTokenService;
import com.tenxengage.app.service.ClientService;
import com.tenxengage.app.service.PermissionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ApprovalController.class)
@Import(SecurityConfig.class)
class ApprovalControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ApprovalService approvalService;
    @MockBean
    private ApprovalTokenService approvalTokenService;
    @MockBean
    private PermissionService permissionService;
    @MockBean
    private JwtTokenProvider jwtTokenProvider;
    @MockBean
    private CustomUserDetailsService customUserDetailsService;
    @MockBean
    private ClientService clientService;
    @MockBean
    private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @Test
    void getIncentiveForApproval_doesNotRequireAuthentication() throws Exception {
        // Approval endpoints are public -- verify no 401/403
        int status = mockMvc.perform(get("/api/v1/approvals/incentive")
                        .param("token", "valid-token-value"))
                .andReturn().getResponse().getStatus();

        assertThat(status).isNotEqualTo(401);
        assertThat(status).isNotEqualTo(403);
    }

    @Test
    void processDecision_isPublicNoAuth() throws Exception {
        when(approvalService.processApproval(eq("test-token"), eq("approved"), any()))
                .thenReturn(new ApprovalService.ApprovalResult(true, "Approved", "approved"));

        mockMvc.perform(post("/api/v1/approvals/decide")
                        .param("token", "test-token")
                        .param("action", "approved"))
                .andExpect(status().isOk());
    }

    @Test
    void browserDecision_isPublicNoAuth() throws Exception {
        when(approvalService.processApproval(eq("test-token"), eq("approved"), any()))
                .thenReturn(new ApprovalService.ApprovalResult(true, "Approved", "approved"));

        mockMvc.perform(post("/api/v1/approvals/decide/browser")
                        .param("token", "test-token")
                        .param("action", "approved"))
                .andExpect(status().isOk());
    }
}
