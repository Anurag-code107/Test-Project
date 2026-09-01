package com.tenxengage.app.controller;

import com.tenxengage.app.dto.request.UpdateSelfProfileRequest;
import com.tenxengage.app.dto.response.ApiResponse;
import com.tenxengage.app.dto.response.ProfileFieldResponse;
import com.tenxengage.app.dto.response.UserResponse;
import com.tenxengage.app.entity.User;
import com.tenxengage.app.exception.ResourceNotFoundException;
import com.tenxengage.app.repository.UserRepository;
import com.tenxengage.app.security.RequiresPermission;
import com.tenxengage.app.security.TenantValidator;
import com.tenxengage.app.service.DataExportService;
import com.tenxengage.app.service.ProfileFieldService;
import com.tenxengage.app.service.UserAnonymizationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Data Subject Rights", description = "GDPR data subject rights: profile management, data export, anonymization")
public class DataSubjectRightsController {

    private static final Logger log = LoggerFactory.getLogger(DataSubjectRightsController.class);

    private final TenantValidator tenantValidator;
    private final UserRepository userRepository;
    private final DataExportService dataExportService;
    private final UserAnonymizationService userAnonymizationService;
    private final ProfileFieldService profileFieldService;

    public DataSubjectRightsController(TenantValidator tenantValidator,
                                       UserRepository userRepository,
                                       DataExportService dataExportService,
                                       UserAnonymizationService userAnonymizationService,
                                       ProfileFieldService profileFieldService) {
        this.tenantValidator = tenantValidator;
        this.userRepository = userRepository;
        this.dataExportService = dataExportService;
        this.userAnonymizationService = userAnonymizationService;
        this.profileFieldService = profileFieldService;
    }

    // -------------------------------------------------------------------------
    // Self-service endpoints (/me/*)
    // -------------------------------------------------------------------------

    @GetMapping("/me/profile-fields")
    @RequiresPermission("action.profile.edit")
    @Operation(summary = "Get profile fields with values",
        description = "Returns visible Partner User Data fields with the current user's values")
    public ResponseEntity<ApiResponse<List<ProfileFieldResponse>>> getProfileFields() {
        List<ProfileFieldResponse> fields = profileFieldService.getProfileFields();
        return ResponseEntity.ok(ApiResponse.success(fields, "Profile fields retrieved"));
    }

    @PatchMapping("/me/profile")
    @RequiresPermission("action.profile.edit")
    @Transactional
    @Operation(summary = "Update own profile",
        description = "Allows authenticated users to update their own profile fields (name, phone, avatar) "
                    + "and dynamic custom fields. Cannot modify email, roles, or status.")
    public ResponseEntity<ApiResponse<UserResponse>> updateSelfProfile(
            @Valid @RequestBody UpdateSelfProfileRequest request) {
        UUID currentUserId = tenantValidator.getCurrentUserId();
        UUID currentClientId = tenantValidator.getCurrentClientId();

        User user = userRepository.findByIdAndClientId(currentUserId, currentClientId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", currentUserId));

        // Only update non-null standard fields
        if (request.firstName() != null) {
            user.setFirstName(request.firstName());
        }
        if (request.lastName() != null) {
            user.setLastName(request.lastName());
        }
        if (request.phone() != null) {
            user.setPhone(request.phone());
        }
        if (request.avatar() != null) {
            user.setAvatar(request.avatar());
        }

        User saved = userRepository.save(user);

        // Handle dynamic custom fields via ProfileFieldService
        if (request.customFields() != null && !request.customFields().isEmpty()) {
            profileFieldService.updateProfileFields(request.customFields());
            saved = userRepository.findByIdAndClientId(currentUserId, currentClientId)
                    .orElse(saved);
        }

        log.info("User updated own profile userId={}", currentUserId);

        return ResponseEntity.ok(ApiResponse.success(UserResponse.from(saved), "Profile updated successfully"));
    }

    @GetMapping("/me/data-export")
    @RequiresPermission("action.profile.export_data")
    @Operation(summary = "Export own data",
        description = "Returns all personal data held for the authenticated user (GDPR Article 20 - data portability)")
    public ResponseEntity<ApiResponse<Map<String, Object>>> exportOwnData() {
        UUID currentUserId = tenantValidator.getCurrentUserId();
        UUID currentClientId = tenantValidator.getCurrentClientId();

        log.info("Self-service data export requested by userId={}", currentUserId);

        Map<String, Object> exportData = dataExportService.exportUserData(currentUserId, currentClientId);
        return ResponseEntity.ok(ApiResponse.success(exportData, "Data export generated successfully"));
    }

    // -------------------------------------------------------------------------
    // Admin endpoints (/users/{id}/*)
    // -------------------------------------------------------------------------

    @GetMapping("/users/{id}/data-export")
    @Operation(summary = "Export user data (admin)",
        description = "Returns all personal data held for a specified user. Requires CLIENT_ADMIN role.")
    @RequiresPermission("action.users.export_data")
    public ResponseEntity<ApiResponse<Map<String, Object>>> exportUserData(@PathVariable UUID id) {
        UUID currentClientId = tenantValidator.getCurrentClientId();

        // TENX_ADMIN can export across clients; CLIENT_ADMIN scoped to their own client
        UUID targetClientId = resolveTargetClientId(id, currentClientId);

        log.info("Admin data export for userId={} by adminId={}", id, tenantValidator.getCurrentUserId());

        Map<String, Object> exportData = dataExportService.exportUserData(id, targetClientId);
        return ResponseEntity.ok(ApiResponse.success(exportData, "Data export generated successfully"));
    }

    @PostMapping("/users/{id}/anonymize")
    @Operation(summary = "Anonymize user (admin)",
        description = "Anonymizes a user's personal data (GDPR right to erasure). "
                    + "Replaces PII with placeholder values. Cannot anonymize admin users. Requires CLIENT_ADMIN role.")
    @RequiresPermission("action.users.anonymize")
    public ResponseEntity<ApiResponse<Void>> anonymizeUser(@PathVariable UUID id) {
        UUID currentClientId = tenantValidator.getCurrentClientId();

        // TENX_ADMIN can anonymize across clients; CLIENT_ADMIN scoped to their own client
        UUID targetClientId = resolveTargetClientId(id, currentClientId);

        log.info("Anonymization requested for userId={} by adminId={}", id, tenantValidator.getCurrentUserId());

        userAnonymizationService.anonymizeUser(id, targetClientId);

        return ResponseEntity.ok(ApiResponse.success(null, "User anonymized successfully"));
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Resolves the target client ID for admin operations. TENX_ADMIN users can operate
     * across tenants, so we look up the user's actual client. CLIENT_ADMIN users are
     * scoped to their own client.
     */
    private UUID resolveTargetClientId(UUID targetUserId, UUID adminClientId) {
        if (tenantValidator.isTenxAdmin()) {
            // TENX_ADMIN: resolve the user's actual client
            return userRepository.findById(targetUserId)
                    .map(User::getClientId)
                    .orElseThrow(() -> new ResourceNotFoundException("User", "id", targetUserId));
        }
        return adminClientId;
    }
}
