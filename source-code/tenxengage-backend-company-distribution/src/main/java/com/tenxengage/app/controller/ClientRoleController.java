package com.tenxengage.app.controller;

import com.tenxengage.app.dto.request.AssignHomeDashboardTemplateRequest;
import com.tenxengage.app.dto.request.CloneClientRoleRequest;
import com.tenxengage.app.dto.request.CreateClientRoleRequest;
import com.tenxengage.app.dto.request.UpdateClientRoleRequest;
import com.tenxengage.app.dto.request.UpdatePermissionsRequest;
import com.tenxengage.app.dto.response.ClientRoleResponse;
import com.tenxengage.app.entity.ClientRole;
import com.tenxengage.app.entity.ClientRolePermission;
import com.tenxengage.app.security.RequiresPermission;
import com.tenxengage.app.security.TenantValidator;
import com.tenxengage.app.service.HomeDashboardTemplateService;
import com.tenxengage.app.service.PermissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
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
@RequestMapping("/api/v1/client-roles")
@Tag(name = "Client Roles", description = "Per-client role management")
public class ClientRoleController {

    private final PermissionService permissionService;
    private final TenantValidator tenantValidator;
    private final HomeDashboardTemplateService homeDashboardTemplateService;

    public ClientRoleController(PermissionService permissionService,
                                TenantValidator tenantValidator,
                                HomeDashboardTemplateService homeDashboardTemplateService) {
        this.permissionService = permissionService;
        this.tenantValidator = tenantValidator;
        this.homeDashboardTemplateService = homeDashboardTemplateService;
    }

    @GetMapping
    @Operation(summary = "List all roles for current client")
    @RequiresPermission("action.roles.view")
    public ResponseEntity<List<ClientRoleResponse>> listRoles() {
        UUID clientId = tenantValidator.getCurrentClientId();
        List<ClientRole> roles = permissionService.getClientRoles(clientId);
        List<ClientRoleResponse> responses = roles.stream()
                .map(role -> {
                    List<ClientRolePermission> perms = permissionService.getRolePermissions(role.getId());
                    return ClientRoleResponse.from(role, perms);
                })
                .toList();
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a role with its permissions")
    @RequiresPermission("action.roles.view")
    public ResponseEntity<ClientRoleResponse> getRole(@PathVariable UUID id) {
        ClientRole role = permissionService.getClientRole(id);
        tenantValidator.validateClientAccess(role.getClientId());
        List<ClientRolePermission> perms = permissionService.getRolePermissions(id);
        return ResponseEntity.ok(ClientRoleResponse.from(role, perms));
    }

    @PostMapping
    @Operation(summary = "Create a custom role")
    @RequiresPermission("action.roles.create")
    public ResponseEntity<ClientRoleResponse> createRole(
            @Valid @RequestBody CreateClientRoleRequest request) {
        UUID clientId = tenantValidator.getCurrentClientId();
        ClientRole created = permissionService.createClientRole(
                clientId, request.name(), request.description(),
                request.roleType(), request.permissions());
        List<ClientRolePermission> perms = permissionService.getRolePermissions(created.getId());
        ClientRoleResponse response = ClientRoleResponse.from(created, perms);
        return ResponseEntity.created(URI.create("/api/v1/client-roles/" + created.getId()))
                .body(response);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update role name and description")
    @RequiresPermission("action.roles.edit")
    public ResponseEntity<ClientRoleResponse> updateRole(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateClientRoleRequest request) {
        ClientRole role = permissionService.getClientRole(id);
        tenantValidator.validateClientAccess(role.getClientId());
        ClientRole updated = permissionService.updateClientRole(id, request.name(), request.description());
        List<ClientRolePermission> perms = permissionService.getRolePermissions(id);
        return ResponseEntity.ok(ClientRoleResponse.from(updated, perms));
    }

    @PutMapping("/{id}/permissions")
    @Operation(summary = "Update role permission grants")
    @RequiresPermission("action.roles.edit")
    public ResponseEntity<ClientRoleResponse> updateRolePermissions(
            @PathVariable UUID id,
            @Valid @RequestBody UpdatePermissionsRequest request) {
        ClientRole role = permissionService.getClientRole(id);
        tenantValidator.validateClientAccess(role.getClientId());
        permissionService.updateRolePermissions(id, request.permissions());
        List<ClientRolePermission> perms = permissionService.getRolePermissions(id);
        return ResponseEntity.ok(ClientRoleResponse.from(role, perms));
    }

    @PutMapping("/{id}/dashboard-template")
    @Operation(summary = "Assign a home dashboard template to a role")
    @RequiresPermission("action.roles.assign_dashboard_template")
    public ResponseEntity<ClientRoleResponse> assignDashboardTemplate(
            @PathVariable UUID id,
            @Valid @RequestBody AssignHomeDashboardTemplateRequest request) {
        ClientRole role = permissionService.getClientRole(id);
        tenantValidator.validateClientAccess(role.getClientId());
        ClientRole updated = homeDashboardTemplateService.assignToRole(id, request.templateId());
        List<ClientRolePermission> perms = permissionService.getRolePermissions(id);
        return ResponseEntity.ok(ClientRoleResponse.from(updated, perms));
    }

    @DeleteMapping("/{id}/dashboard-template")
    @Operation(summary = "Clear the home dashboard template assignment (falls back to default)")
    @RequiresPermission("action.roles.assign_dashboard_template")
    public ResponseEntity<ClientRoleResponse> clearDashboardTemplate(@PathVariable UUID id) {
        ClientRole role = permissionService.getClientRole(id);
        tenantValidator.validateClientAccess(role.getClientId());
        ClientRole updated = homeDashboardTemplateService.clearFromRole(id);
        List<ClientRolePermission> perms = permissionService.getRolePermissions(id);
        return ResponseEntity.ok(ClientRoleResponse.from(updated, perms));
    }

    @PostMapping("/{id}/clone")
    @Operation(summary = "Clone an existing role with all its permissions")
    @RequiresPermission("action.roles.create")
    public ResponseEntity<ClientRoleResponse> cloneRole(
            @PathVariable UUID id,
            @Valid @RequestBody CloneClientRoleRequest request) {
        ClientRole sourceRole = permissionService.getClientRole(id);
        tenantValidator.validateClientAccess(sourceRole.getClientId());
        ClientRole cloned = permissionService.cloneClientRole(
                id, request.name(), request.description());
        List<ClientRolePermission> perms = permissionService.getRolePermissions(cloned.getId());
        ClientRoleResponse response = ClientRoleResponse.from(cloned, perms);
        return ResponseEntity.created(URI.create("/api/v1/client-roles/" + cloned.getId()))
                .body(response);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a custom (non-system) role")
    @RequiresPermission("action.roles.delete")
    public ResponseEntity<Void> deleteRole(@PathVariable UUID id) {
        ClientRole role = permissionService.getClientRole(id);
        tenantValidator.validateClientAccess(role.getClientId());
        permissionService.deleteClientRole(id);
        return ResponseEntity.noContent().build();
    }
}
