package com.tenxengage.app.controller.redemption;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tenxengage.app.config.SecurityConfig;
import com.tenxengage.app.dto.response.redemption.ReturnDetailResponse;
import com.tenxengage.app.dto.response.redemption.ReturnSummaryResponse;
import com.tenxengage.app.entity.enums.ReturnStatus;
import com.tenxengage.app.exception.ResourceNotFoundException;
import com.tenxengage.app.exception.StateConflictException;
import com.tenxengage.app.exception.BusinessRuleException;
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
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReturnController.class)
@Import({SecurityConfig.class, PermissionAspect.class, AopAutoConfiguration.class})
class ReturnControllerTest {

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

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID CLIENT_ID = UUID.randomUUID();
    private static final UUID RETURN_ID = UUID.randomUUID();
    private static final UUID REDEMPTION_ID = UUID.randomUUID();

    private void withReturnRequestPermission() {
        when(tenantValidator.getCurrentUserId()).thenReturn(USER_ID);
        when(tenantValidator.getCurrentClientId()).thenReturn(CLIENT_ID);
        when(permissionService.resolveEffectivePermissions(USER_ID))
                .thenReturn(Set.of("action.redemption.return.request"));
    }

    private void withoutPermission() {
        when(tenantValidator.getCurrentUserId()).thenReturn(USER_ID);
        when(tenantValidator.getCurrentClientId()).thenReturn(CLIENT_ID);
        when(permissionService.resolveEffectivePermissions(USER_ID)).thenReturn(Set.of());
    }

    private ReturnDetailResponse sampleDetail() {
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
                null,
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

    private ReturnSummaryResponse sampleSummary() {
        return new ReturnSummaryResponse(
                RETURN_ID,
                REDEMPTION_ID,
                "Amazon Gift Card",
                new BigDecimal("100.00"),
                "cash",
                ReturnStatus.PENDING_APPROVAL,
                "Item was damaged",
                null,
                Instant.now(),
                Instant.now()
        );
    }

    // ── POST /returns tests ────────────────────────────────────────────────────

    @Test
    @WithMockUser
    void submitReturn_returns201_withDetailResponseAndLocation() throws Exception {
        withReturnRequestPermission();
        when(returnService.submitReturn(any(), eq(USER_ID), eq(CLIENT_ID)))
                .thenReturn(sampleDetail());

        mockMvc.perform(post("/api/v1/redemption/returns")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("redemptionId", REDEMPTION_ID.toString(),
                                       "reason", "Item was damaged"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value(RETURN_ID.toString()))
                .andExpect(jsonPath("$.data.status").value("PENDING_APPROVAL"))
                .andExpect(jsonPath("$.data.catalogItemName").value("Amazon Gift Card"))
                .andExpect(header().exists("Location"));
    }

    @Test
    @WithMockUser
    void submitReturn_returns422_whenEligibilityFails() throws Exception {
        withReturnRequestPermission();
        when(returnService.submitReturn(any(), eq(USER_ID), eq(CLIENT_ID)))
                .thenThrow(new BusinessRuleException("Cash redemptions cannot be returned"));

        mockMvc.perform(post("/api/v1/redemption/returns")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("redemptionId", REDEMPTION_ID.toString()))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorMessage").value("Cash redemptions cannot be returned"));
    }

    @Test
    @WithMockUser
    void submitReturn_returns409_whenDuplicateActiveReturn() throws Exception {
        withReturnRequestPermission();
        when(returnService.submitReturn(any(), eq(USER_ID), eq(CLIENT_ID)))
                .thenThrow(new StateConflictException("A return request is already active for this redemption"));

        mockMvc.perform(post("/api/v1/redemption/returns")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("redemptionId", REDEMPTION_ID.toString()))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorMessage").value("A return request is already active for this redemption"));
    }

    @Test
    @WithMockUser
    void submitReturn_returns400_whenRedemptionIdMissing() throws Exception {
        withReturnRequestPermission();

        mockMvc.perform(post("/api/v1/redemption/returns")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("reason", "damaged"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser
    void submitReturn_returns403_whenPermissionMissing() throws Exception {
        withoutPermission();

        mockMvc.perform(post("/api/v1/redemption/returns")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("redemptionId", REDEMPTION_ID.toString()))))
                .andExpect(status().isForbidden());
    }

    @Test
    void submitReturn_returns401_whenUnauthenticated() throws Exception {
        mockMvc.perform(post("/api/v1/redemption/returns")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("redemptionId", REDEMPTION_ID.toString()))))
                .andExpect(status().isUnauthorized());
    }

    // ── GET /returns tests ─────────────────────────────────────────────────────

    @Test
    @WithMockUser
    void listReturns_returns200_withPaginatedResults() throws Exception {
        withReturnRequestPermission();
        Page<ReturnSummaryResponse> page = new PageImpl<>(List.of(sampleSummary()));
        when(returnService.getPartnerReturns(eq(USER_ID), eq(CLIENT_ID), any(), any()))
                .thenReturn(page);

        mockMvc.perform(get("/api/v1/redemption/returns"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.data[0].id").value(RETURN_ID.toString()))
                .andExpect(jsonPath("$.data.data[0].status").value("PENDING_APPROVAL"))
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    @WithMockUser
    void listReturns_returns400_whenSizeExceeds50() throws Exception {
        withReturnRequestPermission();

        mockMvc.perform(get("/api/v1/redemption/returns").param("size", "51"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser
    void listReturns_returns400_whenInvalidSortField() throws Exception {
        withReturnRequestPermission();

        mockMvc.perform(get("/api/v1/redemption/returns").param("sortBy", "invalidField"))
                .andExpect(status().isBadRequest());
    }

    // ── GET /returns/{id} tests ───────────────────────────────────────────────

    @Test
    @WithMockUser
    void getReturn_returns200_withDetailResponse() throws Exception {
        withReturnRequestPermission();
        when(returnService.getReturnById(eq(RETURN_ID), eq(USER_ID), eq(CLIENT_ID), eq(false)))
                .thenReturn(sampleDetail());

        mockMvc.perform(get("/api/v1/redemption/returns/{id}", RETURN_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(RETURN_ID.toString()))
                .andExpect(jsonPath("$.data.status").value("PENDING_APPROVAL"));
    }

    @Test
    @WithMockUser
    void getReturn_returns404_whenNotFound() throws Exception {
        withReturnRequestPermission();
        when(returnService.getReturnById(eq(RETURN_ID), eq(USER_ID), eq(CLIENT_ID), eq(false)))
                .thenThrow(new ResourceNotFoundException("RedemptionReturn", "id", RETURN_ID));

        mockMvc.perform(get("/api/v1/redemption/returns/{id}", RETURN_ID))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser
    void getReturn_returns403_whenPermissionMissing() throws Exception {
        withoutPermission();

        mockMvc.perform(get("/api/v1/redemption/returns/{id}", RETURN_ID))
                .andExpect(status().isForbidden());
    }

    // ── DELETE /returns/{id} tests ─────────────────────────────────────────────

    @Test
    @WithMockUser
    void cancelReturn_returns204_onSuccess() throws Exception {
        withReturnRequestPermission();
        doNothing().when(returnService).cancelReturn(eq(RETURN_ID), eq(USER_ID), eq(CLIENT_ID));

        mockMvc.perform(delete("/api/v1/redemption/returns/{id}", RETURN_ID))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser
    void cancelReturn_returns409_whenWrongState() throws Exception {
        withReturnRequestPermission();
        doThrow(new StateConflictException("Only PENDING_APPROVAL returns can be cancelled"))
                .when(returnService).cancelReturn(eq(RETURN_ID), eq(USER_ID), eq(CLIENT_ID));

        mockMvc.perform(delete("/api/v1/redemption/returns/{id}", RETURN_ID))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorMessage").value("Only PENDING_APPROVAL returns can be cancelled"));
    }

    @Test
    @WithMockUser
    void cancelReturn_returns404_whenNotFound() throws Exception {
        withReturnRequestPermission();
        doThrow(new ResourceNotFoundException("RedemptionReturn", "id", RETURN_ID))
                .when(returnService).cancelReturn(eq(RETURN_ID), eq(USER_ID), eq(CLIENT_ID));

        mockMvc.perform(delete("/api/v1/redemption/returns/{id}", RETURN_ID))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser
    void cancelReturn_returns403_whenPermissionMissing() throws Exception {
        withoutPermission();

        mockMvc.perform(delete("/api/v1/redemption/returns/{id}", RETURN_ID))
                .andExpect(status().isForbidden());
    }
}
