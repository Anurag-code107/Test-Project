package com.tenxengage.app.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tenxengage.app.config.SecurityConfig;
import com.tenxengage.app.dto.request.UpdateTenantRedemptionSettingsRequest;
import com.tenxengage.app.dto.request.UpsertClientCatalogItemConfigRequest;
import com.tenxengage.app.dto.request.UpsertRegionConfigRequest;
import com.tenxengage.app.dto.response.ClientCatalogItemConfigResponse;
import com.tenxengage.app.dto.response.ClientCatalogRegionConfigResponse;
import com.tenxengage.app.dto.response.PaginatedResponse;
import com.tenxengage.app.dto.response.TenantCatalogItemResponse;
import com.tenxengage.app.dto.response.TenantRedemptionSettingsResponse;
import com.tenxengage.app.entity.enums.BatchCadence;
import com.tenxengage.app.entity.enums.RedemptionCategory;
import com.tenxengage.app.entity.enums.RedemptionProcessingMode;
import com.tenxengage.app.exception.BusinessRuleException;
import com.tenxengage.app.exception.ResourceNotFoundException;
import com.tenxengage.app.security.CustomUserDetailsService;
import com.tenxengage.app.security.JwtTokenProvider;
import com.tenxengage.app.security.PermissionAspect;
import com.tenxengage.app.security.PermissionMetrics;
import com.tenxengage.app.security.TenantValidator;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import com.tenxengage.app.service.ClientService;
import com.tenxengage.app.service.PermissionService;
import com.tenxengage.app.service.TenantRedemptionCatalogService;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RedemptionConfigController.class)
@Import({SecurityConfig.class, PermissionAspect.class, AopAutoConfiguration.class})
class RedemptionConfigControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private TenantRedemptionCatalogService tenantCatalogService;
    @MockBean private PermissionService permissionService;
    @MockBean private TenantValidator tenantValidator;
    @MockBean private PermissionMetrics permissionMetrics;
    @MockBean private JwtTokenProvider jwtTokenProvider;
    @MockBean private CustomUserDetailsService customUserDetailsService;
    @MockBean private ClientService clientService;
    @MockBean private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    private static final UUID CLIENT_ID = UUID.randomUUID();
    private static final UUID ITEM_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();

    private void withPermission() {
        when(tenantValidator.getCurrentUserId()).thenReturn(USER_ID);
        when(permissionService.resolveEffectivePermissions(USER_ID))
                .thenReturn(Set.of("action.redemption.configure"));
    }

    private void withoutPermission() {
        when(tenantValidator.getCurrentUserId()).thenReturn(USER_ID);
        when(permissionService.resolveEffectivePermissions(USER_ID)).thenReturn(Set.of());
    }

    private TenantRedemptionSettingsResponse sampleSettings() {
        return new TenantRedemptionSettingsResponse(UUID.randomUUID(), BatchCadence.DAILY,
                10, Instant.now(), Instant.now());
    }

    private ClientCatalogItemConfigResponse sampleConfig() {
        return new ClientCatalogItemConfigResponse(
                UUID.randomUUID(), CLIENT_ID, ITEM_ID, true,
                null, null, null, null, 0L, Instant.now(), Instant.now());
    }

    private TenantCatalogItemResponse sampleTenantItem(boolean isGloballyActive) {
        return new TenantCatalogItemResponse(
                ITEM_ID, "Gift Card", null, RedemptionCategory.NON_CASH, "USD",
                new BigDecimal("5.00"), RedemptionProcessingMode.INSTANT, new String[]{"US"},
                "AMZN-001", false, 30, isGloballyActive,
                null, false, null, null, null, null, Instant.now(), Instant.now());
    }

    @Test
    @WithMockUser
    void getTenantSettings_returns200() throws Exception {
        withPermission();
        when(tenantCatalogService.getTenantSettings()).thenReturn(sampleSettings());

        mockMvc.perform(get("/api/v1/redemption/settings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.batchCadence").value("DAILY"));
    }

    @Test
    @WithMockUser
    void putSettings_returns403_forPartnerSeller() throws Exception {
        withoutPermission();

        UpdateTenantRedemptionSettingsRequest req =
                new UpdateTenantRedemptionSettingsRequest(BatchCadence.WEEKLY, 5);

        mockMvc.perform(put("/api/v1/redemption/settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser
    void putItemConfig_returns200_whenValid() throws Exception {
        withPermission();
        when(tenantCatalogService.upsertItemConfig(eq(ITEM_ID), any())).thenReturn(sampleConfig());

        UpsertClientCatalogItemConfigRequest req =
                new UpsertClientCatalogItemConfigRequest(true, null, null, null, null);

        mockMvc.perform(put("/api/v1/redemption/catalog/config/{id}", ITEM_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabled").value(true));
    }

    @Test
    @WithMockUser
    void putItemConfig_returns403_forPartnerSeller() throws Exception {
        withoutPermission();

        UpsertClientCatalogItemConfigRequest req =
                new UpsertClientCatalogItemConfigRequest(true, null, null, null, null);

        mockMvc.perform(put("/api/v1/redemption/catalog/config/{id}", ITEM_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser
    void putItemConfig_returns422_whenMinAmountBelowFloor() throws Exception {
        withPermission();
        when(tenantCatalogService.upsertItemConfig(eq(ITEM_ID), any()))
                .thenThrow(new BusinessRuleException(
                        "Minimum transaction amount cannot be set below the catalog item's platform minimum of 10.00"));

        UpsertClientCatalogItemConfigRequest req =
                new UpsertClientCatalogItemConfigRequest(true, null, new BigDecimal("5.00"), null, null);

        mockMvc.perform(put("/api/v1/redemption/catalog/config/{id}", ITEM_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @WithMockUser
    void putItemConfig_returns404_whenItemGloballyInactive() throws Exception {
        withPermission();
        when(tenantCatalogService.upsertItemConfig(eq(ITEM_ID), any()))
                .thenThrow(new ResourceNotFoundException("RedemptionCatalogItem", "id", ITEM_ID));

        UpsertClientCatalogItemConfigRequest req =
                new UpsertClientCatalogItemConfigRequest(true, null, null, null, null);

        mockMvc.perform(put("/api/v1/redemption/catalog/config/{id}", ITEM_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound());
    }

    private ClientCatalogRegionConfigResponse sampleRegionConfig(String regionCode) {
        return new ClientCatalogRegionConfigResponse(UUID.randomUUID(), regionCode, true,
                Instant.now(), Instant.now());
    }

    @Test
    @WithMockUser
    void putRegionConfig_returns200_whenValid() throws Exception {
        withPermission();
        when(tenantCatalogService.upsertRegionConfig(eq(ITEM_ID), eq("US"), any()))
                .thenReturn(sampleRegionConfig("US"));

        UpsertRegionConfigRequest req = new UpsertRegionConfigRequest(true);

        mockMvc.perform(put("/api/v1/redemption/catalog/config/{id}/regions/US", ITEM_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.regionCode").value("US"));
    }

    @Test
    @WithMockUser
    void putRegionConfig_returns422_whenRegionNotInScope() throws Exception {
        withPermission();
        when(tenantCatalogService.upsertRegionConfig(eq(ITEM_ID), eq("GB"), any()))
                .thenThrow(new BusinessRuleException("Region GB is not supported by this catalog item's vendor"));

        UpsertRegionConfigRequest req = new UpsertRegionConfigRequest(true);

        mockMvc.perform(put("/api/v1/redemption/catalog/config/{id}/regions/GB", ITEM_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @WithMockUser
    void putRegionConfig_returns403_forPartnerSeller() throws Exception {
        withoutPermission();

        UpsertRegionConfigRequest req = new UpsertRegionConfigRequest(true);

        mockMvc.perform(put("/api/v1/redemption/catalog/config/{id}/regions/US", ITEM_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser
    void deleteRegionConfig_returns204_whenExists() throws Exception {
        withPermission();

        mockMvc.perform(delete("/api/v1/redemption/catalog/config/{id}/regions/US", ITEM_ID))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser
    void deleteRegionConfig_returns204_whenAbsent() throws Exception {
        withPermission();

        mockMvc.perform(delete("/api/v1/redemption/catalog/config/{id}/regions/US", ITEM_ID))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser
    void getTenantCatalog_includesIsGloballyActiveFlag() throws Exception {
        withPermission();
        var page = new PageImpl<>(List.of(sampleTenantItem(true), sampleTenantItem(false)));
        when(tenantCatalogService.getTenantCatalog(any(), any(), any(), any()))
                .thenReturn(page);

        mockMvc.perform(get("/api/v1/redemption/catalog/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.data[0].isGloballyActive").value(true))
                .andExpect(jsonPath("$.data.data[1].isGloballyActive").value(false));
    }
}
