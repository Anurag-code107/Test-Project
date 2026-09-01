package com.tenxengage.app.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tenxengage.app.config.SecurityConfig;
import com.tenxengage.app.dto.request.CreateRedemptionCatalogItemRequest;
import com.tenxengage.app.dto.request.UpdateRedemptionCatalogItemRequest;
import com.tenxengage.app.dto.response.IntegrationHealthResponse;
import com.tenxengage.app.dto.response.PaginatedResponse;
import com.tenxengage.app.dto.response.RedemptionCatalogItemDetailResponse;
import com.tenxengage.app.dto.response.RedemptionCatalogItemResponse;
import com.tenxengage.app.dto.response.SyncJobResponse;
import com.tenxengage.app.entity.enums.RedemptionCategory;
import com.tenxengage.app.entity.enums.RedemptionProcessingMode;
import com.tenxengage.app.exception.BusinessRuleException;
import com.tenxengage.app.security.CustomUserDetailsService;
import com.tenxengage.app.security.JwtTokenProvider;
import com.tenxengage.app.security.PermissionAspect;
import com.tenxengage.app.security.PermissionMetrics;
import com.tenxengage.app.security.SyncRateLimiter;
import com.tenxengage.app.service.GiftCardCatalogService;
import com.tenxengage.app.security.TenantValidator;
import com.tenxengage.app.service.ClientService;
import com.tenxengage.app.service.PermissionService;
import com.tenxengage.app.service.RedemptionCatalogAdminService;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.multipart.MultipartFile;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RedemptionCatalogAdminController.class)
@Import({SecurityConfig.class, PermissionAspect.class, AopAutoConfiguration.class})
class RedemptionCatalogAdminControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private RedemptionCatalogAdminService adminService;
    @MockBean private GiftCardCatalogService giftCardCatalogService;
    @MockBean private PermissionService permissionService;
    @MockBean private TenantValidator tenantValidator;
    @MockBean private PermissionMetrics permissionMetrics;
    @MockBean private SyncRateLimiter syncRateLimiter;
    @MockBean private JwtTokenProvider jwtTokenProvider;
    @MockBean private CustomUserDetailsService customUserDetailsService;
    @MockBean private ClientService clientService;
    @MockBean private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    private static final UUID ITEM_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();

    private void withPermission() {
        when(tenantValidator.getCurrentUserId()).thenReturn(USER_ID);
        when(permissionService.resolveEffectivePermissions(USER_ID))
                .thenReturn(Set.of("action.redemption.catalog.manage"));
    }

    private void withoutPermission() {
        when(tenantValidator.getCurrentUserId()).thenReturn(USER_ID);
        when(permissionService.resolveEffectivePermissions(USER_ID)).thenReturn(Set.of());
    }

    private RedemptionCatalogItemDetailResponse sampleDetail() {
        return new RedemptionCatalogItemDetailResponse(
                ITEM_ID, "Gift Card", null, RedemptionCategory.NON_CASH,
                "USD", new BigDecimal("5.00"), RedemptionProcessingMode.INSTANT,
                new String[]{"US"}, "AMZN-001", false, 30, false,
                null, null, null, Instant.now(), Instant.now());
    }

    private RedemptionCatalogItemResponse sampleResponse() {
        return new RedemptionCatalogItemResponse(
                ITEM_ID, "Gift Card", null, RedemptionCategory.NON_CASH,
                "USD", new BigDecimal("5.00"), RedemptionProcessingMode.INSTANT,
                new String[]{"US"}, "AMZN-001", false, 30, true,
                null, null, Instant.now(), Instant.now());
    }

    @Test
    @WithMockUser
    void createItem_returns201_whenValid() throws Exception {
        withPermission();
        when(adminService.createCatalogItem(any())).thenReturn(sampleDetail());

        CreateRedemptionCatalogItemRequest req = new CreateRedemptionCatalogItemRequest(
                "Gift Card", null, RedemptionCategory.NON_CASH, "USD",
                new BigDecimal("5.00"), RedemptionProcessingMode.INSTANT,
                List.of("US"), "AMZN-001", false, 30, null);

        mockMvc.perform(post("/api/v1/admin/redemption-catalog")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").exists());
    }

    // BU-8: providerItemId (SKU) is mandatory for every form-created catalog — blank → 400.
    @Test
    @WithMockUser
    void createItem_returns400_whenProviderItemIdBlank() throws Exception {
        withPermission();

        CreateRedemptionCatalogItemRequest req = new CreateRedemptionCatalogItemRequest(
                "Gift Card", null, RedemptionCategory.NON_CASH, "USD",
                new BigDecimal("5.00"), RedemptionProcessingMode.INSTANT,
                List.of("US"), "   ", false, 30, null);

        mockMvc.perform(post("/api/v1/admin/redemption-catalog")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createItem_returns401_whenNotAuthenticated() throws Exception {
        CreateRedemptionCatalogItemRequest req = new CreateRedemptionCatalogItemRequest(
                "Gift Card", null, RedemptionCategory.NON_CASH, "USD",
                new BigDecimal("5.00"), null, List.of(), null, false, 0, null);

        mockMvc.perform(post("/api/v1/admin/redemption-catalog")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void activateItem_returns200() throws Exception {
        withPermission();
        when(adminService.activateCatalogItem(ITEM_ID)).thenReturn(sampleResponse());

        mockMvc.perform(patch("/api/v1/admin/redemption-catalog/{id}/activate", ITEM_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isActive").value(true));
    }

    @Test
    @WithMockUser
    void activateItem_returns422_whenNonCashMissingProviderItemId() throws Exception {
        withPermission();
        when(adminService.activateCatalogItem(ITEM_ID))
                .thenThrow(new BusinessRuleException(
                        "Cannot activate a non-cash catalog item without a provider item ID"));

        mockMvc.perform(patch("/api/v1/admin/redemption-catalog/{id}/activate", ITEM_ID))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @WithMockUser
    void deactivateItem_returns200() throws Exception {
        withPermission();
        RedemptionCatalogItemResponse deactivated = new RedemptionCatalogItemResponse(
                ITEM_ID, "Gift Card", null, RedemptionCategory.NON_CASH,
                "USD", new BigDecimal("5.00"), RedemptionProcessingMode.INSTANT,
                new String[]{"US"}, "AMZN-001", false, 30, false,
                null, null, Instant.now(), Instant.now());
        when(adminService.deactivateCatalogItem(ITEM_ID)).thenReturn(deactivated);

        mockMvc.perform(patch("/api/v1/admin/redemption-catalog/{id}/deactivate", ITEM_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isActive").value(false));
    }

    @Test
    @WithMockUser
    void listItems_returns400_whenPageSizeExceeds50() throws Exception {
        withPermission();
        when(adminService.listCatalogItems(any(), any(), any(), any()))
                .thenThrow(new IllegalArgumentException("pageSize must not exceed 50"));

        mockMvc.perform(get("/api/v1/admin/redemption-catalog").param("pageSize", "51"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser
    void updateItem_returns422_whenGeographicScopeNarrowed() throws Exception {
        withPermission();
        when(adminService.updateCatalogItem(eq(ITEM_ID), any()))
                .thenThrow(new BusinessRuleException(
                        "Cannot narrow geographic scope while tenant configurations exist"));

        UpdateRedemptionCatalogItemRequest req = new UpdateRedemptionCatalogItemRequest(
                null, null, null, null, null, null, List.of("US"), null, null, null, null);

        mockMvc.perform(put("/api/v1/admin/redemption-catalog/{id}", ITEM_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @WithMockUser
    void triggerSync_returns202_withJobId() throws Exception {
        withPermission();
        when(syncRateLimiter.tryAcquire(USER_ID)).thenReturn(true);
        when(adminService.triggerXoxodaySync()).thenReturn(new SyncJobResponse(UUID.randomUUID(), "QUEUED"));

        mockMvc.perform(post("/api/v1/admin/redemption-catalog/sync"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.status").value("QUEUED"))
                .andExpect(jsonPath("$.data.jobId").exists());
    }

    @Test
    @WithMockUser
    void triggerSync_returns403_forNonTenxAdmin() throws Exception {
        withoutPermission();

        mockMvc.perform(post("/api/v1/admin/redemption-catalog/sync"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser
    void triggerSync_returns429_whenRateLimitExceeded() throws Exception {
        withPermission();
        when(syncRateLimiter.tryAcquire(USER_ID)).thenReturn(false);

        mockMvc.perform(post("/api/v1/admin/redemption-catalog/sync"))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    @WithMockUser
    void getIntegrationHealth_returns200_withHealthData() throws Exception {
        withPermission();
        when(adminService.getIntegrationHealth()).thenReturn(
                new IntegrationHealthResponse("SUCCESS", OffsetDateTime.now(), 0, List.of()));

        mockMvc.perform(get("/api/v1/admin/redemption-catalog/integration-health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.syncStatus").value("SUCCESS"));
    }

    @Test
    @WithMockUser
    void uploadCatalogItemImage_validFile_returns200() throws Exception {
        withPermission();
        UUID id = UUID.randomUUID();
        RedemptionCatalogItemResponse mockResponse = new RedemptionCatalogItemResponse(
                id, "Test", null, RedemptionCategory.NON_CASH, "points",
                BigDecimal.TEN, RedemptionProcessingMode.INSTANT,
                new String[0], null, false, 0, true,
                "catalog/" + id + "/image-abc.png", null,
                Instant.now(), Instant.now());

        when(adminService.uploadCatalogItemImage(eq(id), any(MultipartFile.class)))
                .thenReturn(mockResponse);

        MockMultipartFile file = new MockMultipartFile("file", "img.png", "image/png", new byte[100]);

        mockMvc.perform(multipart("/api/v1/admin/redemption-catalog/" + id + "/image")
                        .file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.imageUrl").value("catalog/" + id + "/image-abc.png"));
    }

    @Test
    @WithMockUser
    void uploadCatalogItemImage_missingPermission_returns403() throws Exception {
        withoutPermission();
        MockMultipartFile file = new MockMultipartFile("file", "img.png", "image/png", new byte[100]);

        mockMvc.perform(multipart("/api/v1/admin/redemption-catalog/" + UUID.randomUUID() + "/image")
                        .file(file))
                .andExpect(status().isForbidden());
    }

    @Test
    void uploadCatalogItemImage_unauthenticated_returns401() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "img.png", "image/png", new byte[100]);
        mockMvc.perform(multipart("/api/v1/admin/redemption-catalog/" + UUID.randomUUID() + "/image")
                        .file(file))
                .andExpect(status().isUnauthorized());
    }
}
