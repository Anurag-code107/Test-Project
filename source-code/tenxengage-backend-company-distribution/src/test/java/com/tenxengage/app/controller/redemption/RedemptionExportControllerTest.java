package com.tenxengage.app.controller.redemption;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tenxengage.app.config.SecurityConfig;
import com.tenxengage.app.dto.request.redemption.TriggerExportRequest;
import com.tenxengage.app.dto.response.redemption.RedemptionExportJobDetailResponse;
import com.tenxengage.app.dto.response.redemption.RedemptionExportJobResponse;
import com.tenxengage.app.entity.enums.redemption.ExportFormat;
import com.tenxengage.app.exception.BusinessRuleException;
import com.tenxengage.app.exception.ResourceNotFoundException;
import com.tenxengage.app.security.CustomUserDetailsService;
import com.tenxengage.app.security.ExportRateLimiter;
import com.tenxengage.app.security.JwtTokenProvider;
import com.tenxengage.app.security.PermissionAspect;
import com.tenxengage.app.security.PermissionMetrics;
import com.tenxengage.app.security.TenantValidator;
import com.tenxengage.app.service.ClientService;
import com.tenxengage.app.service.PermissionService;
import com.tenxengage.app.service.redemption.RedemptionExportService;
import com.tenxengage.app.service.redemption.RedemptionExportService.AsyncExportResult;
import com.tenxengage.app.service.redemption.RedemptionExportService.SyncExportResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RedemptionExportController.class)
@Import({SecurityConfig.class, PermissionAspect.class, AopAutoConfiguration.class})
class RedemptionExportControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private RedemptionExportService exportService;
    @MockBean private ExportRateLimiter rateLimiter;
    @MockBean private PermissionService permissionService;
    @MockBean private TenantValidator tenantValidator;
    @MockBean private PermissionMetrics permissionMetrics;
    @MockBean private JwtTokenProvider jwtTokenProvider;
    @MockBean private CustomUserDetailsService customUserDetailsService;
    @MockBean private ClientService clientService;
    @MockBean private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID JOB_ID  = UUID.randomUUID();

    private void withPermission(String... permissions) {
        when(tenantValidator.getCurrentUserId()).thenReturn(USER_ID);
        when(permissionService.resolveEffectivePermissions(USER_ID)).thenReturn(Set.of(permissions));
    }

    private void withoutPermission() {
        when(tenantValidator.getCurrentUserId()).thenReturn(USER_ID);
        when(permissionService.resolveEffectivePermissions(USER_ID)).thenReturn(Set.of());
    }

    private TriggerExportRequest validRequest() {
        return new TriggerExportRequest(ExportFormat.CSV, null, null, null, null);
    }

    private RedemptionExportJobResponse pendingJobResponse() {
        return new RedemptionExportJobResponse(JOB_ID, "PENDING", null, null);
    }

    private RedemptionExportJobResponse completedJobResponse() {
        return new RedemptionExportJobResponse(JOB_ID, "COMPLETED", 50, Instant.now());
    }

    @Test
    @WithMockUser
    void POST_200_syncExport() throws Exception {
        withPermission("action.redemption.export");
        when(rateLimiter.tryAcquire(USER_ID)).thenReturn(true);
        when(exportService.triggerExport(any(), eq(USER_ID)))
                .thenReturn(new SyncExportResult("id,status\n".getBytes(), ExportFormat.CSV));

        mockMvc.perform(post("/api/v1/redemption/requests/export")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("attachment")));
    }

    @Test
    @WithMockUser
    void POST_202_asyncExport() throws Exception {
        withPermission("action.redemption.export");
        when(rateLimiter.tryAcquire(USER_ID)).thenReturn(true);
        when(exportService.triggerExport(any(), eq(USER_ID)))
                .thenReturn(new AsyncExportResult(JOB_ID));
        when(exportService.getExportJob(eq(JOB_ID), eq(USER_ID))).thenReturn(pendingJobResponse());

        mockMvc.perform(post("/api/v1/redemption/requests/export")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.id").value(JOB_ID.toString()))
                .andExpect(jsonPath("$.data.status").value("PENDING"));
    }

    @Test
    @WithMockUser
    void POST_422_zeroResults() throws Exception {
        withPermission("action.redemption.export");
        when(rateLimiter.tryAcquire(USER_ID)).thenReturn(true);
        when(exportService.triggerExport(any(), eq(USER_ID)))
                .thenThrow(new BusinessRuleException("No records match the selected filters"));

        mockMvc.perform(post("/api/v1/redemption/requests/export")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @WithMockUser
    void POST_403_missingPermission() throws Exception {
        withoutPermission();

        mockMvc.perform(post("/api/v1/redemption/requests/export")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser
    void POST_429_rateLimitExceeded() throws Exception {
        withPermission("action.redemption.export");
        when(rateLimiter.tryAcquire(USER_ID)).thenReturn(false);

        mockMvc.perform(post("/api/v1/redemption/requests/export")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
    }

    @Test
    @WithMockUser
    void GET_200_getExportJob_completed() throws Exception {
        withPermission("action.redemption.export");
        when(exportService.getExportJob(eq(JOB_ID), eq(USER_ID))).thenReturn(completedJobResponse());

        mockMvc.perform(get("/api/v1/redemption/requests/export/{jobId}", JOB_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.rowCount").value(50));
    }

    @Test
    @WithMockUser
    void GET_404_getExportJob_nonOwner() throws Exception {
        withPermission("action.redemption.export");
        when(exportService.getExportJob(eq(JOB_ID), eq(USER_ID)))
                .thenThrow(new ResourceNotFoundException("RedemptionExportJob", "id", JOB_ID));

        mockMvc.perform(get("/api/v1/redemption/requests/export/{jobId}", JOB_ID))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser
    void GET_200_getExportJobDownload_completed_hasUrl() throws Exception {
        withPermission("action.redemption.export");
        RedemptionExportJobDetailResponse detail = new RedemptionExportJobDetailResponse(
                JOB_ID, "COMPLETED", 50, Instant.now(), "https://storage.example.com/file.csv");
        when(exportService.getExportJobWithDownloadUrl(eq(JOB_ID), eq(USER_ID))).thenReturn(detail);

        mockMvc.perform(get("/api/v1/redemption/requests/export/{jobId}/download", JOB_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.downloadUrl").value("https://storage.example.com/file.csv"));
    }

    @Test
    @WithMockUser
    void GET_200_getExportJobDownload_pending_downloadUrlNull() throws Exception {
        withPermission("action.redemption.export");
        RedemptionExportJobDetailResponse detail = new RedemptionExportJobDetailResponse(
                JOB_ID, "PENDING", null, null, null);
        when(exportService.getExportJobWithDownloadUrl(eq(JOB_ID), eq(USER_ID))).thenReturn(detail);

        mockMvc.perform(get("/api/v1/redemption/requests/export/{jobId}/download", JOB_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.downloadUrl").doesNotExist());
    }
}
