package com.tenxengage.app.controller;

import com.tenxengage.app.audit.Audited;
import com.tenxengage.app.dto.request.CreateRedemptionCatalogItemRequest;
import com.tenxengage.app.dto.request.UpdateRedemptionCatalogItemRequest;
import com.tenxengage.app.dto.response.IntegrationHealthResponse;
import com.tenxengage.app.dto.response.PaginatedResponse;
import com.tenxengage.app.dto.response.RedemptionCatalogItemDetailResponse;
import com.tenxengage.app.dto.response.RedemptionCatalogItemResponse;
import com.tenxengage.app.dto.response.SyncJobResponse;
import com.tenxengage.app.entity.enums.RedemptionCategory;
import com.tenxengage.app.security.RequiresPermission;
import com.tenxengage.app.security.SyncRateLimiter;
import com.tenxengage.app.security.TenantValidator;
import com.tenxengage.app.service.RedemptionCatalogAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.core.io.InputStreamResource;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.CacheControl;
import org.springframework.validation.annotation.Validated;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/v1/admin/redemption-catalog")
@Tag(name = "Redemption Catalog Admin", description = "Platform Admin — global catalog item management")
@Validated
public class RedemptionCatalogAdminController {

    private final RedemptionCatalogAdminService adminService;
    private final SyncRateLimiter syncRateLimiter;
    private final TenantValidator tenantValidator;

    public RedemptionCatalogAdminController(RedemptionCatalogAdminService adminService,
                                             SyncRateLimiter syncRateLimiter,
                                             TenantValidator tenantValidator) {
        this.adminService = adminService;
        this.syncRateLimiter = syncRateLimiter;
        this.tenantValidator = tenantValidator;
    }

    @GetMapping
    @Operation(summary = "List global catalog items")
    @RequiresPermission("action.redemption.catalog.manage")
    public ResponseEntity<PaginatedResponse<RedemptionCatalogItemResponse>> listCatalogItems(
            @RequestParam(required = false) RedemptionCategory category,
            @RequestParam(required = false) Boolean isActive,
            @Size(max = 200) @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ResponseEntity.ok(PaginatedResponse.from(
                adminService.listCatalogItems(category, isActive, search, PageRequest.of(page, pageSize))));
    }

    @PostMapping
    @Operation(summary = "Create a global catalog item")
    @RequiresPermission("action.redemption.catalog.manage")
    @Audited(action = "CREATED", resourceType = "REDEMPTION_CATALOG_ITEM",
            resourceId = "#result.body.id().toString()", description = "Created catalog item")
    public ResponseEntity<RedemptionCatalogItemDetailResponse> createCatalogItem(
            @Valid @RequestBody CreateRedemptionCatalogItemRequest request) {
        RedemptionCatalogItemDetailResponse created = adminService.createCatalogItem(request);
        return ResponseEntity.created(URI.create("/api/v1/admin/redemption-catalog/" + created.id())).body(created);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get catalog item detail")
    @RequiresPermission("action.redemption.catalog.manage")
    public ResponseEntity<RedemptionCatalogItemDetailResponse> getCatalogItemDetail(@PathVariable UUID id) {
        return ResponseEntity.ok(adminService.getCatalogItemDetail(id));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a global catalog item")
    @RequiresPermission("action.redemption.catalog.manage")
    @Audited(action = "UPDATED", resourceType = "REDEMPTION_CATALOG_ITEM",
            resourceId = "#id.toString()", description = "Updated catalog item")
    public ResponseEntity<RedemptionCatalogItemDetailResponse> updateCatalogItem(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateRedemptionCatalogItemRequest request) {
        return ResponseEntity.ok(adminService.updateCatalogItem(id, request));
    }

    @PatchMapping("/{id}/activate")
    @Operation(summary = "Activate a catalog item")
    @RequiresPermission("action.redemption.catalog.manage")
    @Audited(action = "ACTIVATED", resourceType = "REDEMPTION_CATALOG_ITEM",
            resourceId = "#id.toString()", description = "Activated catalog item")
    public ResponseEntity<RedemptionCatalogItemResponse> activateCatalogItem(@PathVariable UUID id) {
        return ResponseEntity.ok(adminService.activateCatalogItem(id));
    }

    @PatchMapping("/{id}/deactivate")
    @Operation(summary = "Deactivate a catalog item")
    @RequiresPermission("action.redemption.catalog.manage")
    @Audited(action = "DEACTIVATED", resourceType = "REDEMPTION_CATALOG_ITEM",
            resourceId = "#id.toString()", description = "Deactivated catalog item")
    public ResponseEntity<RedemptionCatalogItemResponse> deactivateCatalogItem(@PathVariable UUID id) {
        return ResponseEntity.ok(adminService.deactivateCatalogItem(id));
    }

    @PostMapping("/sync")
    @Operation(summary = "Trigger Xoxoday catalog sync")
    @RequiresPermission("action.redemption.catalog.manage")
    @Audited(action = "SYNCED", resourceType = "REDEMPTION_CATALOG_ITEM",
            resourceId = "#result.body.jobId().toString()", description = "Triggered Xoxoday sync")
    public ResponseEntity<SyncJobResponse> triggerXoxodaySync() {
        UUID userId = tenantValidator.getCurrentUserId();
        if (!syncRateLimiter.tryAcquire(userId)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Sync rate limit exceeded. Retry in 1 minute.");
        }
        return ResponseEntity.accepted().body(adminService.triggerXoxodaySync());
    }

    @GetMapping("/integration-health")
    @Operation(summary = "Get Xoxoday integration health")
    @RequiresPermission("action.redemption.catalog.manage")
    public ResponseEntity<IntegrationHealthResponse> getIntegrationHealth() {
        return ResponseEntity.ok(adminService.getIntegrationHealth());
    }

    @PostMapping(value = "/{id}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload image for a catalog item")
    @RequiresPermission("action.redemption.catalog.manage")
    @Audited(action = "UPDATED", resourceType = "REDEMPTION_CATALOG_ITEM",
            resourceId = "#id.toString()", description = "Uploaded catalog item image")
    public ResponseEntity<RedemptionCatalogItemResponse> uploadCatalogItemImage(
            @PathVariable UUID id,
            @RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(adminService.uploadCatalogItemImage(id, file));
    }

    @GetMapping("/{id}/image")
    @Operation(summary = "Stream catalog item image binary")
    public ResponseEntity<InputStreamResource> streamCatalogItemImage(@PathVariable UUID id) {
        RedemptionCatalogAdminService.ImageStream img = adminService.streamCatalogItemImage(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(img.contentType()))
                .cacheControl(CacheControl.maxAge(7, TimeUnit.DAYS).cachePrivate())
                .body(new InputStreamResource(img.content()));
    }
}
