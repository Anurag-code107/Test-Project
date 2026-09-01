package com.tenxengage.app.controller;

import com.tenxengage.app.audit.Audited;
import com.tenxengage.app.dto.request.UpdateTenantRedemptionSettingsRequest;
import com.tenxengage.app.entity.enums.RedemptionCategory;
import com.tenxengage.app.dto.request.UpsertClientCatalogItemConfigRequest;
import com.tenxengage.app.dto.request.UpsertRegionConfigRequest;
import com.tenxengage.app.dto.response.ClientCatalogItemConfigResponse;
import com.tenxengage.app.dto.response.ClientCatalogRegionConfigResponse;
import com.tenxengage.app.dto.response.PaginatedResponse;
import com.tenxengage.app.dto.response.TenantCatalogItemResponse;
import com.tenxengage.app.dto.response.TenantRedemptionSettingsResponse;
import com.tenxengage.app.security.RequiresPermission;
import com.tenxengage.app.service.TenantRedemptionCatalogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.PageRequest;
import org.springframework.validation.annotation.Validated;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/redemption")
@Tag(name = "Redemption Config", description = "Tenant catalog configuration and settings")
@Validated
public class RedemptionConfigController {

    private final TenantRedemptionCatalogService tenantCatalogService;

    public RedemptionConfigController(TenantRedemptionCatalogService tenantCatalogService) {
        this.tenantCatalogService = tenantCatalogService;
    }

    @GetMapping("/settings")
    @RequiresPermission("action.redemption.configure")
    @Operation(summary = "Get tenant redemption settings", description = "Auto-creates with DAILY default on first access")
    public ResponseEntity<TenantRedemptionSettingsResponse> getTenantSettings() {
        return ResponseEntity.ok(tenantCatalogService.getTenantSettings());
    }

    @PutMapping("/settings")
    @RequiresPermission("action.redemption.configure")
    @Audited(action = "UPDATED", resourceType = "TENANT_REDEMPTION_SETTINGS",
            description = "Updated tenant redemption settings")
    @Operation(summary = "Update tenant redemption settings")
    public ResponseEntity<TenantRedemptionSettingsResponse> updateTenantSettings(
            @Valid @RequestBody UpdateTenantRedemptionSettingsRequest request) {
        return ResponseEntity.ok(tenantCatalogService.updateTenantSettings(request));
    }

    @GetMapping("/catalog/config")
    @RequiresPermission("action.redemption.configure")
    @Operation(summary = "List tenant catalog config", description = "All globally active items with tenant config overlay")
    public ResponseEntity<PaginatedResponse<TenantCatalogItemResponse>> getTenantCatalog(
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(required = false) RedemptionCategory category,
            @Size(max = 200) @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        if (pageSize > 50) {
            throw new IllegalArgumentException("pageSize must not exceed 50");
        }
        Pageable pageable = PageRequest.of(page, pageSize);
        return ResponseEntity.ok(PaginatedResponse.from(
                tenantCatalogService.getTenantCatalog(enabled, category != null ? category.name() : null, search, pageable)));
    }

    @PutMapping("/catalog/config/{catalogItemId}")
    @RequiresPermission("action.redemption.configure")
    @Audited(action = "UPDATED", resourceType = "TENANT_CATALOG_CONFIG",
            description = "Upserted tenant catalog item config")
    @Operation(summary = "Upsert catalog item config")
    public ResponseEntity<ClientCatalogItemConfigResponse> upsertItemConfig(
            @PathVariable UUID catalogItemId,
            @Valid @RequestBody UpsertClientCatalogItemConfigRequest request) {
        return ResponseEntity.ok(tenantCatalogService.upsertItemConfig(catalogItemId, request));
    }

    @GetMapping("/catalog/config/{catalogItemId}/regions")
    @RequiresPermission("action.redemption.configure")
    @Operation(summary = "List regional config for a catalog item")
    public ResponseEntity<List<ClientCatalogRegionConfigResponse>> getRegionalConfigs(
            @PathVariable UUID catalogItemId) {
        return ResponseEntity.ok(tenantCatalogService.getRegionalConfigs(catalogItemId));
    }

    @PutMapping("/catalog/config/{catalogItemId}/regions/{regionCode}")
    @RequiresPermission("action.redemption.configure")
    @Audited(action = "UPDATED", resourceType = "TENANT_CATALOG_CONFIG",
            description = "Upserted regional catalog config")
    @Operation(summary = "Upsert regional config for a catalog item")
    public ResponseEntity<ClientCatalogRegionConfigResponse> upsertRegionConfig(
            @PathVariable UUID catalogItemId,
            @Pattern(regexp = "^[A-Z0-9]{2,10}$") @PathVariable String regionCode,
            @Valid @RequestBody UpsertRegionConfigRequest request) {
        return ResponseEntity.ok(tenantCatalogService.upsertRegionConfig(catalogItemId, regionCode, request));
    }

    @DeleteMapping("/catalog/config/{catalogItemId}/regions/{regionCode}")
    @RequiresPermission("action.redemption.configure")
    @Audited(action = "DELETED", resourceType = "TENANT_CATALOG_CONFIG",
            description = "Deleted regional catalog config")
    @Operation(summary = "Delete regional config for a catalog item — idempotent 204")
    public ResponseEntity<Void> deleteRegionConfig(
            @PathVariable UUID catalogItemId,
            @Pattern(regexp = "^[A-Z0-9]{2,10}$") @PathVariable String regionCode) {
        tenantCatalogService.deleteRegionConfig(catalogItemId, regionCode);
        return ResponseEntity.noContent().build();
    }
}
