package com.tenxengage.app.controller.redemption;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tenxengage.app.config.SecurityConfig;
import com.tenxengage.app.dto.response.RedemptionRequestDetailResponse;
import com.tenxengage.app.dto.response.redemption.ApprovalQueueItemResponse;
import com.tenxengage.app.entity.enums.WalletType;
import com.tenxengage.app.exception.ResourceNotFoundException;
import com.tenxengage.app.exception.StateConflictException;
import com.tenxengage.app.security.CustomUserDetailsService;
import com.tenxengage.app.security.JwtTokenProvider;
import com.tenxengage.app.security.PermissionAspect;
import com.tenxengage.app.security.PermissionMetrics;
import com.tenxengage.app.security.TenantValidator;
import com.tenxengage.app.service.ClientService;
import com.tenxengage.app.service.PermissionService;
import com.tenxengage.app.service.redemption.RedemptionApprovalService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RedemptionApprovalController.class)
@Import({SecurityConfig.class, PermissionAspect.class, AopAutoConfiguration.class})
class RedemptionApprovalControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private RedemptionApprovalService approvalService;
    @MockBean private PermissionService permissionService;
    @MockBean private TenantValidator tenantValidator;
    @MockBean private PermissionMetrics permissionMetrics;
    @MockBean private JwtTokenProvider jwtTokenProvider;
    @MockBean private CustomUserDetailsService customUserDetailsService;
    @MockBean private ClientService clientService;
    @MockBean private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID REDEMPTION_ID = UUID.randomUUID();

    private void withApprovePermission() {
        when(tenantValidator.getCurrentUserId()).thenReturn(USER_ID);
        when(permissionService.resolveEffectivePermissions(USER_ID))
                .thenReturn(Set.of("action.redemption.approve"));
    }

    private void withoutApprovePermission() {
        when(tenantValidator.getCurrentUserId()).thenReturn(USER_ID);
        when(permissionService.resolveEffectivePermissions(USER_ID)).thenReturn(Set.of());
    }

    private ApprovalQueueItemResponse sampleItem() {
        return new ApprovalQueueItemResponse(
                UUID.randomUUID(), "Jane Doe", "Amazon Gift Card",
                "cash", new BigDecimal("100.00"), WalletType.INDIVIDUAL,
                Instant.now());
    }

    private RedemptionRequestDetailResponse sampleApprovedDetail() {
        return new RedemptionRequestDetailResponse(
                REDEMPTION_ID, "RESERVED", new BigDecimal("100.00"), "cash",
                null, "Amazon Gift Card", null, "NON_CASH", "INSTANT", WalletType.INDIVIDUAL, null, null,
                Instant.now(), null, null,
                USER_ID, Instant.now(), null, null);
    }

    private RedemptionRequestDetailResponse sampleRejectedDetail() {
        return new RedemptionRequestDetailResponse(
                REDEMPTION_ID, "CANCELLED", new BigDecimal("100.00"), "cash",
                null, "Amazon Gift Card", null, "NON_CASH", "INSTANT", WalletType.INDIVIDUAL, null, null,
                Instant.now(), null, null,
                USER_ID, Instant.now(), "Duplicate request", null);
    }

    // ── GET /approval-queue tests ───────────────────────────────────────────

    @Test
    @WithMockUser
    void getApprovalQueue_returns200_withValidPermission() throws Exception {
        withApprovePermission();
        Page<ApprovalQueueItemResponse> page = new PageImpl<>(List.of(sampleItem()));
        when(approvalService.getApprovalQueue(any(), any(), any(), any(), any(), any()))
                .thenReturn(page);

        mockMvc.perform(get("/api/v1/redemption/requests/approval-queue"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.data[0].requesterDisplayName").value("Jane Doe"))
                .andExpect(jsonPath("$.data.data[0].catalogItemName").value("Amazon Gift Card"))
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    @WithMockUser
    void getApprovalQueue_returns403_whenPermissionMissing() throws Exception {
        withoutApprovePermission();

        mockMvc.perform(get("/api/v1/redemption/requests/approval-queue"))
                .andExpect(status().isForbidden());
    }

    @Test
    void getApprovalQueue_returns401_whenUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/redemption/requests/approval-queue"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void getApprovalQueue_returns400_whenSizeExceeds50() throws Exception {
        withApprovePermission();

        mockMvc.perform(get("/api/v1/redemption/requests/approval-queue").param("size", "51"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser
    void getApprovalQueue_returns400_whenUnknownRequestType() throws Exception {
        withApprovePermission();

        mockMvc.perform(get("/api/v1/redemption/requests/approval-queue")
                        .param("requestType", "INVALID_TYPE"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser
    void getApprovalQueue_returns200WithEmptyData_whenReturnType() throws Exception {
        withApprovePermission();
        when(approvalService.getApprovalQueue(any(), any(), any(), any(), any(), any()))
                .thenReturn(Page.empty());

        mockMvc.perform(get("/api/v1/redemption/requests/approval-queue")
                        .param("requestType", "RETURN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.data").isEmpty());
    }

    // ── POST /{id}/approve tests ────────────────────────────────────────────

    @Test
    @WithMockUser
    void approveRedemption_returns200_withReservedStatus() throws Exception {
        withApprovePermission();
        when(approvalService.approveRedemption(any(), any())).thenReturn(sampleApprovedDetail());

        mockMvc.perform(post("/api/v1/redemption/requests/{id}/approve", REDEMPTION_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("RESERVED"))
                .andExpect(jsonPath("$.data.reviewedBy").value(USER_ID.toString()));
    }

    @Test
    @WithMockUser
    void approveRedemption_returns409_whenStateConflict() throws Exception {
        withApprovePermission();
        when(approvalService.approveRedemption(any(), any()))
                .thenThrow(new StateConflictException("Redemption is not in PENDING_APPROVAL state"));

        mockMvc.perform(post("/api/v1/redemption/requests/{id}/approve", REDEMPTION_ID))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorMessage").value("Redemption is not in PENDING_APPROVAL state"));
    }

    @Test
    @WithMockUser
    void approveRedemption_returns404_whenNotFound() throws Exception {
        withApprovePermission();
        when(approvalService.approveRedemption(any(), any()))
                .thenThrow(new ResourceNotFoundException("RedemptionRequest", "id", REDEMPTION_ID));

        mockMvc.perform(post("/api/v1/redemption/requests/{id}/approve", REDEMPTION_ID))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser
    void approveRedemption_returns403_whenPermissionMissing() throws Exception {
        withoutApprovePermission();

        mockMvc.perform(post("/api/v1/redemption/requests/{id}/approve", REDEMPTION_ID))
                .andExpect(status().isForbidden());
    }

    @Test
    void approveRedemption_returns401_whenUnauthenticated() throws Exception {
        mockMvc.perform(post("/api/v1/redemption/requests/{id}/approve", REDEMPTION_ID))
                .andExpect(status().isUnauthorized());
    }

    // ── POST /{id}/reject tests ─────────────────────────────────────────────

    @Test
    @WithMockUser
    void rejectRedemption_returns200_withCancelledStatusAndRejectionReason() throws Exception {
        withApprovePermission();
        when(approvalService.rejectRedemption(any(), any(), any())).thenReturn(sampleRejectedDetail());

        mockMvc.perform(post("/api/v1/redemption/requests/{id}/reject", REDEMPTION_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("rejectionReason", "Duplicate request"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"))
                .andExpect(jsonPath("$.data.rejectionReason").value("Duplicate request"));
    }

    @Test
    @WithMockUser
    void rejectRedemption_returns400_whenRejectionReasonBlank() throws Exception {
        withApprovePermission();

        mockMvc.perform(post("/api/v1/redemption/requests/{id}/reject", REDEMPTION_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("rejectionReason", ""))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.rejectionReason").exists());
    }

    @Test
    @WithMockUser
    void rejectRedemption_returns400_whenBodyMissing() throws Exception {
        withApprovePermission();

        mockMvc.perform(post("/api/v1/redemption/requests/{id}/reject", REDEMPTION_ID)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser
    void rejectRedemption_returns409_whenStateConflict() throws Exception {
        withApprovePermission();
        when(approvalService.rejectRedemption(any(), any(), any()))
                .thenThrow(new StateConflictException("Redemption is not in PENDING_APPROVAL state"));

        mockMvc.perform(post("/api/v1/redemption/requests/{id}/reject", REDEMPTION_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("rejectionReason", "Duplicate"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorMessage").value("Redemption is not in PENDING_APPROVAL state"));
    }

    @Test
    @WithMockUser
    void rejectRedemption_returns404_whenNotFound() throws Exception {
        withApprovePermission();
        when(approvalService.rejectRedemption(any(), any(), any()))
                .thenThrow(new ResourceNotFoundException("RedemptionRequest", "id", REDEMPTION_ID));

        mockMvc.perform(post("/api/v1/redemption/requests/{id}/reject", REDEMPTION_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("rejectionReason", "Duplicate"))))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser
    void rejectRedemption_returns403_whenPermissionMissing() throws Exception {
        withoutApprovePermission();

        mockMvc.perform(post("/api/v1/redemption/requests/{id}/reject", REDEMPTION_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("rejectionReason", "Duplicate"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectRedemption_returns401_whenUnauthenticated() throws Exception {
        mockMvc.perform(post("/api/v1/redemption/requests/{id}/reject", REDEMPTION_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("rejectionReason", "Duplicate"))))
                .andExpect(status().isUnauthorized());
    }
}
