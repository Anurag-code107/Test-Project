package com.tenxengage.app.controller;

import com.tenxengage.app.dto.request.UpdatePermissionsRequest;
import com.tenxengage.app.dto.response.TenantPermissionResponse;
import com.tenxengage.app.entity.Client;
import com.tenxengage.app.entity.ClientPermissionGrant;
import com.tenxengage.app.entity.Permission;
import com.tenxengage.app.exception.ResourceNotFoundException;
import com.tenxengage.app.repository.ClientRepository;
import com.tenxengage.app.security.RequiresPermission;
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
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/tenant-permissions")
@Tag(name = "Tenant Permissions", description = "TenX Admin: manage which permissions are available per tenant")
public class TenantPermissionController {

    private final PermissionService permissionService;
    private final ClientRepository clientRepository;

    public TenantPermissionController(PermissionService permissionService,
                                       ClientRepository clientRepository) {
        this.permissionService = permissionService;
        this.clientRepository = clientRepository;
    }

    @GetMapping("/{clientId}")
    @Operation(summary = "Get all permission grants for a tenant")
    @RequiresPermission("action.tenx.permissions.manage")
    public ResponseEntity<TenantPermissionResponse> getTenantPermissions(@PathVariable UUID clientId) {
        Client client = clientRepository.findById(clientId)
                .orElseThrow(() -> new ResourceNotFoundException("Client", "id", clientId));

        List<Permission> allPermissions = permissionService.getAllPermissions();
        Set<String> grantedKeys = permissionService.getTenantPermissionKeys(clientId);

        return ResponseEntity.ok(TenantPermissionResponse.from(client, allPermissions, grantedKeys));
    }

    @PutMapping("/{clientId}")
    @Operation(summary = "Update permission grants for a tenant")
    @RequiresPermission("action.tenx.permissions.manage")
    public ResponseEntity<TenantPermissionResponse> updateTenantPermissions(
            @PathVariable UUID clientId,
            @Valid @RequestBody UpdatePermissionsRequest request) {
        Client client = clientRepository.findById(clientId)
                .orElseThrow(() -> new ResourceNotFoundException("Client", "id", clientId));

        permissionService.updateTenantPermissions(clientId, request.permissions());

        List<Permission> allPermissions = permissionService.getAllPermissions();
        Set<String> grantedKeys = permissionService.getTenantPermissionKeys(clientId);

        return ResponseEntity.ok(TenantPermissionResponse.from(client, allPermissions, grantedKeys));
    }
}
