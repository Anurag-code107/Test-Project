package com.tenxengage.app.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tenxengage.app.config.SecurityConfig;
import com.tenxengage.app.dto.request.SubmitPersonalRedemptionRequest;
import com.tenxengage.app.dto.response.RedemptionRequestDetailResponse;
import com.tenxengage.app.dto.response.RedemptionRequestResponse;
import com.tenxengage.app.dto.response.RedemptionSubmissionConfirmationResponse;
import com.tenxengage.app.entity.enums.RedemptionProcessingMode;
import com.tenxengage.app.entity.enums.RedemptionStatus;
import com.tenxengage.app.entity.enums.WalletType;
import com.tenxengage.app.exception.ResourceNotFoundException;
import com.tenxengage.app.security.CustomUserDetailsService;
import com.tenxengage.app.security.JwtTokenProvider;
import com.tenxengage.app.security.PermissionAspect;
import com.tenxengage.app.security.PermissionMetrics;
import com.tenxengage.app.security.TenantValidator;
import com.tenxengage.app.service.ClientService;
import com.tenxengage.app.service.PermissionService;
import com.tenxengage.app.service.RedemptionSubmissionService;
import com.tenxengage.app.service.redemption.RedemptionHistoryService;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RedemptionRequestController.class)
@Import({SecurityConfig.class, PermissionAspect.class, AopAutoConfiguration.class})
class RedemptionRequestControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private RedemptionSubmissionService submissionService;
    @MockBean private RedemptionHistoryService historyService;
    @MockBean private PermissionService permissionService;
    @MockBean private TenantValidator tenantValidator;
    @MockBean private PermissionMetrics permissionMetrics;
    @MockBean private JwtTokenProvider jwtTokenProvider;
    @MockBean private CustomUserDetailsService customUserDetailsService;
    @MockBean private ClientService clientService;
    @MockBean private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID REQUEST_ID = UUID.randomUUID();
    private static final UUID CATALOG_ID = UUID.randomUUID();
    private static final UUID WALLET_ID = UUID.randomUUID();

    private void withPermission(String... permissions) {
        when(tenantValidator.getCurrentUserId()).thenReturn(USER_ID);
        when(permissionService.resolveEffectivePermissions(USER_ID)).thenReturn(Set.of(permissions));
    }

    private void withoutPermission() {
        when(tenantValidator.getCurrentUserId()).thenReturn(USER_ID);
        when(permissionService.resolveEffectivePermissions(USER_ID)).thenReturn(Set.of());
    }

    private SubmitPersonalRedemptionRequest validRequest() {
        return new SubmitPersonalRedemptionRequest(CATALOG_ID, WALLET_ID, new BigDecimal("50.00"), "cash", null);
    }

    private RedemptionSubmissionConfirmationResponse confirmationResponse() {
        return new RedemptionSubmissionConfirmationResponse(
                REQUEST_ID, RedemptionStatus.RESERVED.name(),
                RedemptionProcessingMode.INSTANT.name(),
                "Available in minutes", null, Instant.now());
    }

    @Test
    @WithMockUser
    void POST_201_personalRedemption_happyPath() throws Exception {
        withPermission("action.redemption.redeem");
        when(submissionService.submitPersonalRedemption(any(), eq(USER_ID)))
                .thenReturn(confirmationResponse());

        mockMvc.perform(post("/api/v1/redemption/requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.data.id").value(REQUEST_ID.toString()))
                .andExpect(jsonPath("$.data.status").value("RESERVED"))
                .andExpect(jsonPath("$.data.processingMode").value("INSTANT"))
                .andExpect(jsonPath("$.data.estimatedDelivery").value("Available in minutes"));
    }

    @Test
    @WithMockUser
    void POST_422_amountBelowMinimum() throws Exception {
        withPermission("action.redemption.redeem");
        when(submissionService.submitPersonalRedemption(any(), eq(USER_ID)))
                .thenThrow(new com.tenxengage.app.exception.BusinessRuleException(
                        "Amount is below the minimum allowed: 10.00"));

        mockMvc.perform(post("/api/v1/redemption/requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorMessage").value("Amount is below the minimum allowed: 10.00"));
    }

    @Test
    @WithMockUser
    void POST_409_inFlightCapReached() throws Exception {
        withPermission("action.redemption.redeem");
        when(submissionService.submitPersonalRedemption(any(), eq(USER_ID)))
                .thenThrow(new ResponseStatusException(CONFLICT, "Maximum in-flight redemptions reached"));

        mockMvc.perform(post("/api/v1/redemption/requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isConflict());
    }

    @Test
    @WithMockUser
    void POST_403_missingPermission() throws Exception {
        withoutPermission();

        mockMvc.perform(post("/api/v1/redemption/requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser
    void POST_404_catalogItemWrongTenant() throws Exception {
        withPermission("action.redemption.redeem");
        when(submissionService.submitPersonalRedemption(any(), eq(USER_ID)))
                .thenThrow(new ResourceNotFoundException("RedemptionCatalogItem", "id", CATALOG_ID));

        mockMvc.perform(post("/api/v1/redemption/requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isNotFound());
    }




    @Test
    @WithMockUser
    void GET_200_listRedemptions_paginated() throws Exception {
        withPermission("action.redemption.view_history");
        RedemptionRequestResponse item = new RedemptionRequestResponse(
                REQUEST_ID, "RESERVED", new BigDecimal("50.00"), "cash",
                "NON_CASH", "INSTANT", "Amazon Gift Card", Instant.now(), null, null, null, false);
        when(historyService.getPersonalHistory(eq(USER_ID), any(), any()))
                .thenReturn(new PageImpl<>(List.of(item)));

        mockMvc.perform(get("/api/v1/redemption/requests"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.data[0].id").value(REQUEST_ID.toString()))
                .andExpect(jsonPath("$.data.data[0].status").value("RESERVED"))
                .andExpect(jsonPath("$.data.data[0].catalogItemName").value("Amazon Gift Card"));
    }

    @Test
    @WithMockUser
    void GET_200_listRedemptions_withStatusFilter() throws Exception {
        withPermission("action.redemption.view_history");
        when(historyService.getPersonalHistory(eq(USER_ID), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/v1/redemption/requests?status=COMPLETED"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser
    void GET_400_listRedemptions_invalidStatus() throws Exception {
        withPermission("action.redemption.view_history");

        mockMvc.perform(get("/api/v1/redemption/requests?status=INVALID_STATUS"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser
    void GET_422_listRedemptions_dateFromAfterDateTo() throws Exception {
        withPermission("action.redemption.view_history");

        mockMvc.perform(get("/api/v1/redemption/requests?dateFrom=2026-06-10&dateTo=2026-06-01"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @WithMockUser
    void GET_400_listRedemptions_pageSizeTooLarge() throws Exception {
        withPermission("action.redemption.view_history");

        mockMvc.perform(get("/api/v1/redemption/requests?pageSize=51"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser
    void GET_403_listRedemptions_missingPermission() throws Exception {
        withoutPermission();

        mockMvc.perform(get("/api/v1/redemption/requests"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser
    void GET_200_getRedemptionById_linkedReturnIdNull() throws Exception {
        withPermission("action.redemption.view_history");
        RedemptionRequestDetailResponse detail = new RedemptionRequestDetailResponse(
                REQUEST_ID, "RESERVED", new BigDecimal("50.00"), "cash",
                null, "Test Reward", null, null, "NON_CASH", "INSTANT", WalletType.INDIVIDUAL, null, null, Instant.now(), null, null,
                null, null, null, null, null);
        when(historyService.getRedemptionDetail(eq(REQUEST_ID), any(UUID.class))).thenReturn(detail);

        mockMvc.perform(get("/api/v1/redemption/requests/{id}", REQUEST_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(REQUEST_ID.toString()))
                .andExpect(jsonPath("$.data.linkedReturnId").doesNotExist());
    }

    @Test
    @WithMockUser
    void GET_403_getRedemptionById_missingPermission() throws Exception {
        withoutPermission();

        mockMvc.perform(get("/api/v1/redemption/requests/{id}", REQUEST_ID))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser
    void GET_404_getRedemptionById_wrongTenant() throws Exception {
        withPermission("action.redemption.view_history");
        when(historyService.getRedemptionDetail(eq(REQUEST_ID), any(UUID.class)))
                .thenThrow(new ResourceNotFoundException("RedemptionRequest", "id", REQUEST_ID));

        mockMvc.perform(get("/api/v1/redemption/requests/{id}", REQUEST_ID))
                .andExpect(status().isNotFound());
    }
}
