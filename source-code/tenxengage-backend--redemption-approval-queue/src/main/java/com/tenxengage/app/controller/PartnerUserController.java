package com.tenxengage.app.controller;

import com.tenxengage.app.dto.request.UpdatePermissionsRequest;
import com.tenxengage.app.dto.response.UserResponse;
import com.tenxengage.app.entity.User;
import com.tenxengage.app.exception.ResourceNotFoundException;
import com.tenxengage.app.repository.UserRepository;
import com.tenxengage.app.security.RequiresPermission;
import com.tenxengage.app.security.TenantValidator;
import com.tenxengage.app.service.PermissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
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

@RestController
@RequestMapping("/api/v1/partner-users")
@Tag(name = "Partner Users", description = "Partner Admin: manage seller permissions within own company")
public class PartnerUserController {

    private final UserRepository userRepository;
    private final PermissionService permissionService;
    private final TenantValidator tenantValidator;

    public PartnerUserController(UserRepository userRepository,
                                  PermissionService permissionService,
                                  TenantValidator tenantValidator) {
        this.userRepository = userRepository;
        this.permissionService = permissionService;
        this.tenantValidator = tenantValidator;
    }

    @GetMapping
    @Operation(summary = "List users in the partner admin's own company")
    @RequiresPermission("action.users.view")
    public ResponseEntity<List<UserResponse>> listPartnerUsers() {
        UUID clientId = tenantValidator.getCurrentClientId();
        UUID partnerCompanyId = tenantValidator.getCurrentUserDetails().getPartnerCompanyId();
        if (partnerCompanyId == null) {
            throw new AccessDeniedException("Only partner users can access this endpoint");
        }
        List<User> users = userRepository.findByClientIdAndPartnerCompanyId(clientId, partnerCompanyId);
        List<UserResponse> responses = users.stream().map(UserResponse::from).toList();
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/{userId}/permissions")
    @Operation(summary = "Get effective permissions for a user in the partner admin's company")
    @RequiresPermission("action.permissions.view")
    public ResponseEntity<Set<String>> getSellerPermissions(@PathVariable UUID userId) {
        validateSameCompany(userId);
        Set<String> effective = permissionService.resolveEffectivePermissions(userId);
        return ResponseEntity.ok(effective);
    }

    @PutMapping("/{userId}/permissions")
    @Operation(summary = "Update permission overrides for a seller (can only restrict)")
    @RequiresPermission("action.permissions.assign")
    public ResponseEntity<Set<String>> updateSellerPermissions(
            @PathVariable UUID userId,
            @Valid @RequestBody UpdatePermissionsRequest request) {
        validateSameCompany(userId);

        UUID currentUserId = tenantValidator.getCurrentUserId();
        UUID clientId = tenantValidator.getCurrentClientId();

        // Partner admin can only restrict, not expand beyond their own permissions
        Set<String> myPermissions = permissionService.resolveEffectivePermissions(currentUserId);
        for (Map.Entry<String, Boolean> entry : request.permissions().entrySet()) {
            if (entry.getValue() && !myPermissions.contains(entry.getKey())) {
                throw new AccessDeniedException(
                        "Cannot grant permission you don't have: " + entry.getKey());
            }
        }

        permissionService.updateUserOverrides(clientId, userId, request.permissions());
        Set<String> effective = permissionService.resolveEffectivePermissions(userId);
        return ResponseEntity.ok(effective);
    }

    private void validateSameCompany(UUID targetUserId) {
        UUID myPartnerCompanyId = tenantValidator.getCurrentUserDetails().getPartnerCompanyId();
        if (myPartnerCompanyId == null) {
            throw new AccessDeniedException("Only partner users can access this endpoint");
        }

        User target = userRepository.findById(targetUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", targetUserId));

        if (!myPartnerCompanyId.equals(target.getPartnerCompanyId())) {
            throw new AccessDeniedException("Cannot manage users outside your company");
        }
        if (!tenantValidator.getCurrentClientId().equals(target.getClientId())) {
            throw new AccessDeniedException("Cross-tenant access denied");
        }
    }
}
