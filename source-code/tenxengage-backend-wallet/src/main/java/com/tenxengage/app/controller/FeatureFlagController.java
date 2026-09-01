package com.tenxengage.app.controller;

import com.tenxengage.app.dto.request.CreateFeatureFlagRequest;
import com.tenxengage.app.dto.request.UpdateFeatureFlagRequest;
import com.tenxengage.app.dto.response.FeatureFlagResponse;
import com.tenxengage.app.security.RequiresPermission;
import com.tenxengage.app.security.TenantValidator;
import com.tenxengage.app.service.FeatureFlagService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/feature-flags")
@Tag(name = "Feature Flags", description = "Feature flag management")
public class FeatureFlagController {

    private final FeatureFlagService featureFlagService;
    private final TenantValidator tenantValidator;

    public FeatureFlagController(FeatureFlagService featureFlagService,
                                  TenantValidator tenantValidator) {
        this.featureFlagService = featureFlagService;
        this.tenantValidator = tenantValidator;
    }

    @GetMapping
    @Operation(summary = "List all feature flags", description = "TENX_ADMIN: full details")
    @RequiresPermission("action.tenx.features.view")
    public ResponseEntity<List<FeatureFlagResponse>> getAllFeatureFlags() {
        List<FeatureFlagResponse> flags = featureFlagService.getAllFeatureFlags();
        return ResponseEntity.ok(flags);
    }

    @PostMapping
    @Operation(summary = "Create a feature flag")
    @RequiresPermission("action.tenx.features.manage")
    public ResponseEntity<FeatureFlagResponse> createFeatureFlag(
            @Valid @RequestBody CreateFeatureFlagRequest request) {
        FeatureFlagResponse created = featureFlagService.createFeatureFlag(request);
        return ResponseEntity.created(URI.create("/api/v1/feature-flags/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a feature flag")
    @RequiresPermission("action.tenx.features.manage")
    public ResponseEntity<FeatureFlagResponse> updateFeatureFlag(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateFeatureFlagRequest request) {
        FeatureFlagResponse updated = featureFlagService.updateFeatureFlag(id, request);
        return ResponseEntity.ok(updated);
    }

    @GetMapping("/my-features")
    @Operation(summary = "Get enabled features", description = "Returns enabled feature keys for current user's client")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<String>> getMyFeatures() {
        List<String> features = featureFlagService.getEnabledFeatures(
            tenantValidator.getCurrentClientId()
        );
        return ResponseEntity.ok(features);
    }
}
