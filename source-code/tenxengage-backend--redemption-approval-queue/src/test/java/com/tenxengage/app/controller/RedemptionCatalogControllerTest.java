package com.tenxengage.app.controller;

import com.tenxengage.app.config.SecurityConfig;
import com.tenxengage.app.dto.response.CatalogBrowseItemResponse;
import com.tenxengage.app.entity.enums.RedemptionCategory;
import com.tenxengage.app.entity.enums.RedemptionProcessingMode;
import com.tenxengage.app.exception.ResourceNotFoundException;
import com.tenxengage.app.security.CustomUserDetailsService;
import com.tenxengage.app.security.JwtTokenProvider;
import com.tenxengage.app.security.PermissionAspect;
import com.tenxengage.app.security.PermissionMetrics;
import com.tenxengage.app.security.TenantValidator;
import com.tenxengage.app.service.ClientService;
import com.tenxengage.app.service.PermissionService;
import com.tenxengage.app.service.RedemptionCatalogBrowseService;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RedemptionCatalogController.class)
@Import({SecurityConfig.class, PermissionAspect.class, AopAutoConfiguration.class})
class RedemptionCatalogControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockBean private RedemptionCatalogBrowseService browseService;
    @MockBean private PermissionService permissionService;
    @MockBean private TenantValidator tenantValidator;
    @MockBean private PermissionMetrics permissionMetrics;
    @MockBean private JwtTokenProvider jwtTokenProvider;
    @MockBean private CustomUserDetailsService customUserDetailsService;
    @MockBean private ClientService clientService;
    @MockBean private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    private static final UUID ITEM_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();

    private void withPermission() {
        when(tenantValidator.getCurrentUserId()).thenReturn(USER_ID);
        when(permissionService.resolveEffectivePermissions(USER_ID))
                .thenReturn(Set.of("module.redemption_store"));
    }

    private void withoutPermission() {
        when(tenantValidator.getCurrentUserId()).thenReturn(USER_ID);
        when(permissionService.resolveEffectivePermissions(USER_ID)).thenReturn(Set.of());
    }

    private CatalogBrowseItemResponse sampleBrowseItem() {
        return new CatalogBrowseItemResponse(
                ITEM_ID, "Amazon Gift Card", "Description", null, RedemptionCategory.NON_CASH,
                "USD", new BigDecimal("5.00"), RedemptionProcessingMode.INSTANT,
                "Available in minutes", true, BigDecimal.ZERO,
                new String[]{"US"});
    }

    @Test
    @WithMockUser
    void browseCatalog_returns200_forPartnerSeller() throws Exception {
        withPermission();
        when(browseService.browsePartnerCatalog(isNull(), isNull(), any()))
                .thenReturn(new PageImpl<>(List.of(sampleBrowseItem())));

        mockMvc.perform(get("/api/v1/redemption/catalog"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.data[0].name").value("Amazon Gift Card"))
                .andExpect(jsonPath("$.data.data[0].currencyId").value("USD"));
    }

    @Test
    @WithMockUser
    void browseCatalog_returns403_whenPermissionMissing() throws Exception {
        withoutPermission();

        mockMvc.perform(get("/api/v1/redemption/catalog"))
                .andExpect(status().isForbidden());
    }

    @Test
    void browseCatalog_returns401_whenUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/redemption/catalog"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void browseCatalog_returns400_whenPageSizeExceeds50() throws Exception {
        withPermission();

        mockMvc.perform(get("/api/v1/redemption/catalog").param("pageSize", "51"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser
    void getCatalogItem_returns404_whenItemDisabledForTenant() throws Exception {
        withPermission();
        when(browseService.getPartnerCatalogItem(eq(ITEM_ID), isNull()))
                .thenThrow(new ResourceNotFoundException("RedemptionCatalogItem", "id", ITEM_ID));

        mockMvc.perform(get("/api/v1/redemption/catalog/{id}", ITEM_ID))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser
    void browseCatalog_responseNeverIncludesProviderItemId() throws Exception {
        withPermission();
        when(browseService.browsePartnerCatalog(isNull(), isNull(), any()))
                .thenReturn(new PageImpl<>(List.of(sampleBrowseItem())));

        mockMvc.perform(get("/api/v1/redemption/catalog"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.data[0].providerItemId").doesNotExist())
                .andExpect(jsonPath("$.data.data[0].syncMetadata").doesNotExist())
                .andExpect(jsonPath("$.data.data[0].clientId").doesNotExist());
    }
}
