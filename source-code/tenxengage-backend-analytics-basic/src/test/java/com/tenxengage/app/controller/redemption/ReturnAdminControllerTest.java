package com.tenxengage.app.controller.redemption;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tenxengage.app.config.SecurityConfig;
import com.tenxengage.app.dto.response.redemption.ReturnDetailResponse;
import com.tenxengage.app.dto.response.redemption.ReturnQueueItemResponse;
import com.tenxengage.app.entity.enums.ReturnResolution;
import com.tenxengage.app.entity.enums.ReturnStatus;
import com.tenxengage.app.exception.ResourceNotFoundException;
import com.tenxengage.app.exception.StateConflictException;
import com.tenxengage.app.security.CustomUserDetailsService;
import com.tenxengage.app.security.JwtTokenProvider;
import com.tenxengage.app.security.PermissionAspect;
import com.tenxengage.app.security.PermissionMetrics;
import com.tenxengage.app.security.TenantValidator;
import com.tenxengage.app.service.ClientService;
import com.tenxengage.app.service.PermissionService;
import com.tenxengage.app.service.redemption.ReturnService;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReturnAdminController.class)
@Import({SecurityConfig.class, PermissionAspect.class, AopAutoConfiguration.class})
class ReturnAdminControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private ReturnService returnService;
    @MockBean private PermissionService permissionService;
    @MockBean private TenantValidator tenantValidator;
    @MockBean private PermissionMetrics permissionMetrics;
    @MockBean private JwtTokenProvider jwtTokenProvider;
    @MockBean private CustomUserDetailsService customUserDetailsService;
    @MockBean private ClientService clientService;
    @MockBean private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    private static final UUID ADMIN_ID = UUID.randomUUID();
    private static final UUID CLIENT_ID = UUID.randomUUID();
    private static final UUID RETURN_ID = UUID.randomUUID();
    private static final UUID REDEMPTION_ID = UUID.randomUUID();

    private void withReviewPermission() {
        when(tenantValidator.getCurrentUserId()).thenReturn(ADMIN_ID);
        when(tenantValidator.getCurrentClientId()).thenReturn(CLIENT_ID);
        when(permissionService.resolveEffectivePermissions(ADMIN_ID))
                .thenReturn(Set.of("action.redemption.return.review"));
    }

    private void withoutPermission() {
        when(tenantValidator.getCurrentUserId()).thenReturn(ADMIN_ID);
        when(tenantValidator.getCurrentClientId()).thenReturn(CLIENT_ID);
        when(permissionService.resolveEffectivePermissions(ADMIN_ID)).thenReturn(Set.of());
    }

    private ReturnDetailResponse sampleApprovedDetail() {
        return new ReturnDetailResponse(
                RETURN_ID,
                REDEMPTION_ID,
                "Amazon Gift Card",
                "Jane Doe",
                new BigDecimal("100.00"),
                "cash",
                ReturnStatus.APPROVED,
                "Item was damaged",
                Instant.now(),
                "Admin reviewed",
                null,
                Instant.now(),
                null,
                null,
                null,
                null,
                Instant.now(),
                Instant.now()
        );
    }

    private ReturnDetailResponse sampleRejectedDetail() {
        return new ReturnDetailResponse(
                RETURN_ID,
                REDEMPTION_ID,
                "Amazon Gift Card",
                "Jane Doe",
                new BigDecimal("100.00"),
                "cash",
                ReturnStatus.RETURN_REJECTED,
                "Item was damaged",
                Instant.now(),
                "Item was already used by vendor",
                null,
                null,
                null,
                null,
                Instant.now(),
                null,
                Instant.now(),
                Instant.now()
        );
    }

    private ReturnDetailResponse samplePendingDetail() {
        return new ReturnDetailResponse(
                RETURN_ID,
                REDEMPTION_ID,
                "Amazon Gift Card",
                "Jane Doe",
                new BigDecimal("100.00"),
                "cash",
                ReturnStatus.PENDING_APPROVAL,
                "Item was damaged",
                null,
                "Admin internal note",
                null,
                null,
                null,
                null,
                null,
                null,
                Instant.now(),
                Instant.now()
        );
    }

    private ReturnQueueItemResponse sampleQueueItem() {
        return new ReturnQueueItemResponse(
                RETURN_ID,
                "Amazon Gift Card",
                "Jane Doe",
                "Acme Corp",
                new BigDecimal("100.00"),
                "cash",
                ReturnStatus.PENDING_APPROVAL,
                "Item was damaged",
                Instant.now(),
                Instant.now()
        );
    }

    // ── POST /{id}/approve tests ───────────────────────────────────────────────

    @Test
    @WithMockUser
    void approveReturn_returns200_withApprovedDetailResponse() throws Exception {
        withReviewPermission();
        when(returnService.approveReturn(eq(RETURN_ID), eq(ADMIN_ID), eq(CLIENT_ID)))
                .thenReturn(sampleApprovedDetail());

        mockMvc.perform(post("/api/v1/redemption/admin/returns/{id}/approve", RETURN_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(RETURN_ID.toString()))
                .andExpect(jsonPath("$.data.status").value("APPROVED"));
    }

    @Test
    @WithMockUser
    void approveReturn_returns409_whenWrongState() throws Exception {
        withReviewPermission();
        when(returnService.approveReturn(eq(RETURN_ID), eq(ADMIN_ID), eq(CLIENT_ID)))
                .thenThrow(new StateConflictException("Only PENDING_APPROVAL returns can be approved"));

        mockMvc.perform(post("/api/v1/redemption/admin/returns/{id}/approve", RETURN_ID))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorMessage").value("Only PENDING_APPROVAL returns can be approved"));
    }

    @Test
    @WithMockUser
    void approveReturn_returns404_whenReturnNotFound() throws Exception {
        withReviewPermission();
        when(returnService.approveReturn(eq(RETURN_ID), eq(ADMIN_ID), eq(CLIENT_ID)))
                .thenThrow(new ResourceNotFoundException("RedemptionReturn", "id", RETURN_ID));

        mockMvc.perform(post("/api/v1/redemption/admin/returns/{id}/approve", RETURN_ID))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser
    void approveReturn_returns403_whenPermissionMissing() throws Exception {
        withoutPermission();

        mockMvc.perform(post("/api/v1/redemption/admin/returns/{id}/approve", RETURN_ID))
                .andExpect(status().isForbidden());
    }

    // ── POST /{id}/reject tests ────────────────────────────────────────────────

    @Test
    @WithMockUser
    void rejectReturn_returns200_withRejectedDetailResponse() throws Exception {
        withReviewPermission();
        when(returnService.rejectReturn(eq(RETURN_ID), any(), eq(ADMIN_ID), eq(CLIENT_ID)))
                .thenReturn(sampleRejectedDetail());

        mockMvc.perform(post("/api/v1/redemption/admin/returns/{id}/reject", RETURN_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("rejectionReason", "Item was already used by vendor"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(RETURN_ID.toString()))
                .andExpect(jsonPath("$.data.status").value("RETURN_REJECTED"));
    }

    @Test
    @WithMockUser
    void rejectReturn_returns400_whenRejectionReasonIsBlank() throws Exception {
        withReviewPermission();

        mockMvc.perform(post("/api/v1/redemption/admin/returns/{id}/reject", RETURN_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("rejectionReason", ""))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser
    void rejectReturn_returns400_whenRejectionReasonMissing() throws Exception {
        withReviewPermission();

        mockMvc.perform(post("/api/v1/redemption/admin/returns/{id}/reject", RETURN_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser
    void rejectReturn_returns409_whenWrongState() throws Exception {
        withReviewPermission();
        when(returnService.rejectReturn(eq(RETURN_ID), any(), eq(ADMIN_ID), eq(CLIENT_ID)))
                .thenThrow(new StateConflictException("Only PENDING_APPROVAL returns can be rejected"));

        mockMvc.perform(post("/api/v1/redemption/admin/returns/{id}/reject", RETURN_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("rejectionReason", "reason"))))
                .andExpect(status().isConflict());
    }

    @Test
    @WithMockUser
    void rejectReturn_returns403_whenPermissionMissing() throws Exception {
        withoutPermission();

        mockMvc.perform(post("/api/v1/redemption/admin/returns/{id}/reject", RETURN_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("rejectionReason", "some reason"))))
                .andExpect(status().isForbidden());
    }

    // ── GET / (admin list) tests ───────────────────────────────────────────────

    @Test
    @WithMockUser
    void getAdminReturns_returns200_withPaginatedResults() throws Exception {
        withReviewPermission();
        Page<ReturnQueueItemResponse> page = new PageImpl<>(List.of(sampleQueueItem()));
        when(returnService.getAdminReturns(eq(CLIENT_ID), isNull(), isNull(), isNull(), any()))
                .thenReturn(page);

        mockMvc.perform(get("/api/v1/redemption/admin/returns"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.data[0].id").value(RETURN_ID.toString()))
                .andExpect(jsonPath("$.data.data[0].status").value("PENDING_APPROVAL"))
                .andExpect(jsonPath("$.data.data[0].partnerCompanyName").value("Acme Corp"))
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    @WithMockUser
    void getAdminReturns_returns400_whenSizeExceeds50() throws Exception {
        withReviewPermission();

        mockMvc.perform(get("/api/v1/redemption/admin/returns").param("size", "51"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser
    void getAdminReturns_returns400_whenInvalidSortField() throws Exception {
        withReviewPermission();

        mockMvc.perform(get("/api/v1/redemption/admin/returns").param("sortBy", "unknownField"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser
    void getAdminReturns_returns403_whenPermissionMissing() throws Exception {
        withoutPermission();

        mockMvc.perform(get("/api/v1/redemption/admin/returns"))
                .andExpect(status().isForbidden());
    }

    // ── GET /{id} (admin detail) tests ────────────────────────────────────────

    @Test
    @WithMockUser
    void getReturnById_admin_returns200_withReviewNotes() throws Exception {
        withReviewPermission();
        when(returnService.getReturnById(eq(RETURN_ID), isNull(), eq(CLIENT_ID), eq(true)))
                .thenReturn(samplePendingDetail());

        mockMvc.perform(get("/api/v1/redemption/admin/returns/{id}", RETURN_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(RETURN_ID.toString()))
                .andExpect(jsonPath("$.data.reviewNotes").value("Admin internal note"));
    }

    @Test
    @WithMockUser
    void getReturnById_admin_returns404_whenNotFound() throws Exception {
        withReviewPermission();
        when(returnService.getReturnById(eq(RETURN_ID), isNull(), eq(CLIENT_ID), eq(true)))
                .thenThrow(new ResourceNotFoundException("RedemptionReturn", "id", RETURN_ID));

        mockMvc.perform(get("/api/v1/redemption/admin/returns/{id}", RETURN_ID))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser
    void getReturnById_admin_returns403_whenPermissionMissing() throws Exception {
        withoutPermission();

        mockMvc.perform(get("/api/v1/redemption/admin/returns/{id}", RETURN_ID))
                .andExpect(status().isForbidden());
    }

    // ── POST /{id}/resolve tests ───────────────────────────────────────────────

    private ReturnDetailResponse sampleConfirmedDetail() {
        return new ReturnDetailResponse(
                RETURN_ID,
                REDEMPTION_ID,
                "Amazon Gift Card",
                "Jane Doe",
                new BigDecimal("100.00"),
                "cash",
                ReturnStatus.RETURN_CONFIRMED,
                "Item was damaged",
                Instant.now(),
                "Admin confirmed",
                null,
                Instant.now(),
                null,
                Instant.now(),
                null,
                null,
                Instant.now(),
                Instant.now()
        );
    }

    @Test
    @WithMockUser
    void resolveTimedOutReturn_confirm_returns200_withReturnConfirmedDetail() throws Exception {
        withReviewPermission();
        when(returnService.resolveTimedOut(
                eq(RETURN_ID), eq(ReturnResolution.CONFIRM), any(), eq(ADMIN_ID), eq(CLIENT_ID)))
                .thenReturn(sampleConfirmedDetail());

        mockMvc.perform(post("/api/v1/redemption/admin/returns/{id}/resolve", RETURN_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("resolution", "CONFIRM", "notes", "Admin confirms"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(RETURN_ID.toString()))
                .andExpect(jsonPath("$.data.status").value("RETURN_CONFIRMED"));
    }

    @Test
    @WithMockUser
    void resolveTimedOutReturn_reject_returns200_withReturnRejectedDetail() throws Exception {
        withReviewPermission();
        when(returnService.resolveTimedOut(
                eq(RETURN_ID), eq(ReturnResolution.REJECT), any(), eq(ADMIN_ID), eq(CLIENT_ID)))
                .thenReturn(sampleRejectedDetail());

        mockMvc.perform(post("/api/v1/redemption/admin/returns/{id}/resolve", RETURN_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("resolution", "REJECT"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(RETURN_ID.toString()))
                .andExpect(jsonPath("$.data.status").value("RETURN_REJECTED"));
    }

    @Test
    @WithMockUser
    void resolveTimedOutReturn_returns400_whenResolutionMissing() throws Exception {
        withReviewPermission();

        mockMvc.perform(post("/api/v1/redemption/admin/returns/{id}/resolve", RETURN_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser
    void resolveTimedOutReturn_returns409_whenNotReturnTimedOut() throws Exception {
        withReviewPermission();
        when(returnService.resolveTimedOut(
                eq(RETURN_ID), any(), any(), eq(ADMIN_ID), eq(CLIENT_ID)))
                .thenThrow(new StateConflictException("Only RETURN_TIMED_OUT returns can be resolved"));

        mockMvc.perform(post("/api/v1/redemption/admin/returns/{id}/resolve", RETURN_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("resolution", "CONFIRM"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorMessage").value("Only RETURN_TIMED_OUT returns can be resolved"));
    }

    @Test
    @WithMockUser
    void resolveTimedOutReturn_returns403_whenPermissionMissing() throws Exception {
        withoutPermission();

        mockMvc.perform(post("/api/v1/redemption/admin/returns/{id}/resolve", RETURN_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("resolution", "CONFIRM"))))
                .andExpect(status().isForbidden());
    }
}
