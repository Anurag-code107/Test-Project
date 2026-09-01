package com.tenxengage.app.controller;

import com.tenxengage.app.dto.request.UpdatePermissionsRequest;
import com.tenxengage.app.entity.CompanyPermissionOverride;
import com.tenxengage.app.entity.UserPermissionOverride;
import com.tenxengage.app.security.RequiresPermission;
import com.tenxengage.app.security.TenantValidator;
import com.tenxengage.app.service.PermissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/company-permissions")
@Tag(name = "Company Permissions", description = "Company and user permission override management")
public class CompanyPermissionController {

    private final PermissionService permissionService;
    private final TenantValidator tenantValidator;

    public CompanyPermissionController(PermissionService permissionService,
                                       TenantValidator tenantValidator) {
        this.permissionService = permissionService;
        this.tenantValidator = tenantValidator;
    }

    @GetMapping("/{companyId}")
    @Operation(summary = "Get company permission overrides")
    @RequiresPermission("action.permissions.view")
    public ResponseEntity<Map<String, Boolean>> getCompanyOverrides(@PathVariable UUID companyId) {
        UUID clientId = tenantValidator.getCurrentClientId();
        List<CompanyPermissionOverride> overrides = permissionService.getCompanyOverrides(clientId, companyId);
        Map<String, Boolean> result = overrides.stream()
                .collect(Collectors.toMap(
                        CompanyPermissionOverride::getPermissionKey,
                        CompanyPermissionOverride::isGranted
                ));
        return ResponseEntity.ok(result);
    }

    @PutMapping("/{companyId}")
    @Operation(summary = "Update company permission overrides")
    @RequiresPermission("action.permissions.manage")
    public ResponseEntity<Map<String, Boolean>> updateCompanyOverrides(
            @PathVariable UUID companyId,
            @Valid @RequestBody UpdatePermissionsRequest request) {
        UUID clientId = tenantValidator.getCurrentClientId();
        permissionService.updateCompanyOverrides(clientId, companyId, request.permissions());
        List<CompanyPermissionOverride> overrides = permissionService.getCompanyOverrides(clientId, companyId);
        Map<String, Boolean> result = overrides.stream()
                .collect(Collectors.toMap(
                        CompanyPermissionOverride::getPermissionKey,
                        CompanyPermissionOverride::isGranted
                ));
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{companyId}/users/{userId}")
    @Operation(summary = "Get user-level permission overrides")
    @RequiresPermission("action.permissions.view")
    public ResponseEntity<Map<String, Boolean>> getUserOverrides(
            @PathVariable UUID companyId,
            @PathVariable UUID userId) {
        UUID clientId = tenantValidator.getCurrentClientId();
        List<UserPermissionOverride> overrides = permissionService.getUserOverrides(clientId, userId);
        Map<String, Boolean> result = overrides.stream()
                .collect(Collectors.toMap(
                        UserPermissionOverride::getPermissionKey,
                        UserPermissionOverride::isGranted
                ));
        return ResponseEntity.ok(result);
    }

    @PutMapping("/{companyId}/users/{userId}")
    @Operation(summary = "Update user-level permission overrides")
    @RequiresPermission("action.permissions.assign")
    public ResponseEntity<Map<String, Boolean>> updateUserOverrides(
            @PathVariable UUID companyId,
            @PathVariable UUID userId,
            @Valid @RequestBody UpdatePermissionsRequest request) {
        UUID clientId = tenantValidator.getCurrentClientId();
        permissionService.updateUserOverrides(clientId, userId, request.permissions());
        List<UserPermissionOverride> overrides = permissionService.getUserOverrides(clientId, userId);
        Map<String, Boolean> result = overrides.stream()
                .collect(Collectors.toMap(
                        UserPermissionOverride::getPermissionKey,
                        UserPermissionOverride::isGranted
                ));
        return ResponseEntity.ok(result);
    }
}
