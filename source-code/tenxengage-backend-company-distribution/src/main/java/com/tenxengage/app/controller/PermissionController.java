package com.tenxengage.app.controller;

import com.tenxengage.app.dto.response.PermissionResponse;
import com.tenxengage.app.security.RequiresPermission;
import com.tenxengage.app.security.TenantValidator;
import com.tenxengage.app.service.PermissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/permissions")
@Tag(name = "Permissions", description = "Permission catalog and effective permissions")
public class PermissionController {

    private final PermissionService permissionService;
    private final TenantValidator tenantValidator;

    public PermissionController(PermissionService permissionService, TenantValidator tenantValidator) {
        this.permissionService = permissionService;
        this.tenantValidator = tenantValidator;
    }

    @GetMapping
    @Operation(summary = "List all permissions in catalog")
    @RequiresPermission("action.permissions.view")
    public ResponseEntity<List<PermissionResponse>> getAllPermissions() {
        List<PermissionResponse> permissions = permissionService.getAllPermissions().stream()
                .map(PermissionResponse::from)
                .toList();
        return ResponseEntity.ok(permissions);
    }

    @GetMapping("/effective")
    @Operation(summary = "Get current user's effective permissions")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Set<String>> getMyEffectivePermissions() {
        UUID userId = tenantValidator.getCurrentUserId();
        Set<String> permissions = permissionService.resolveEffectivePermissions(userId);
        return ResponseEntity.ok(permissions);
    }

    @GetMapping("/effective/{userId}")
    @Operation(summary = "Get a specific user's effective permissions")
    @RequiresPermission("action.permissions.view")
    public ResponseEntity<Set<String>> getUserEffectivePermissions(@PathVariable UUID userId) {
        Set<String> permissions = permissionService.resolveEffectivePermissions(userId);
        return ResponseEntity.ok(permissions);
    }
}
