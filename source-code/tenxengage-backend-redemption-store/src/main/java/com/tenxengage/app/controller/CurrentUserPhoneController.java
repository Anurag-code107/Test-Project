package com.tenxengage.app.controller;

import com.tenxengage.app.audit.Audited;
import com.tenxengage.app.dto.request.ConfirmPhoneUpdateRequest;
import com.tenxengage.app.dto.request.InitiatePhoneUpdateRequest;
import com.tenxengage.app.dto.response.PhoneUpdateResponse;
import com.tenxengage.app.security.RequiresPermission;
import com.tenxengage.app.security.TenantValidator;
import com.tenxengage.app.service.xtrm.XtrmProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Self-service mobile-number change for the current user (2-step OTP, synced to XTRM).
 *
 * <p>The general profile update ({@code PATCH /me/profile}) changes local fields only; the mobile lives here
 * because for an XTRM-enrolled payee it must be applied at XTRM (via {@code UpdateUser}) with an OTP, and our
 * copy is written only after XTRM confirms — so the two never drift. A not-yet-enrolled user skips the OTP and
 * the number is saved immediately (it flows to XTRM at enrollment).</p>
 */
@RestController
@RequestMapping("/api/v1/me/phone")
@Tag(name = "Profile", description = "Self-service mobile-number change (OTP-synced to the payout provider)")
public class CurrentUserPhoneController {

    private final XtrmProfileService phoneService;
    private final TenantValidator tenantValidator;

    public CurrentUserPhoneController(XtrmProfileService phoneService, TenantValidator tenantValidator) {
        this.phoneService = phoneService;
        this.tenantValidator = tenantValidator;
    }

    @PostMapping("/initiate")
    @RequiresPermission("action.profile.edit")
    @Operation(summary = "Start changing my mobile number (sends a one-time password if enrolled)")
    public ResponseEntity<PhoneUpdateResponse> initiate(@Valid @RequestBody InitiatePhoneUpdateRequest request) {
        UUID userId = tenantValidator.getCurrentUserId();
        return ResponseEntity.ok(phoneService.initiate(userId, request.phone(), request.phoneCountryIso2()));
    }

    @PostMapping("/confirm")
    @RequiresPermission("action.profile.edit")
    @Audited(action = "EDITED", resourceType = "USER", description = "Updated mobile number")
    @Operation(summary = "Confirm my mobile-number change with the one-time password")
    public ResponseEntity<PhoneUpdateResponse> confirm(@Valid @RequestBody ConfirmPhoneUpdateRequest request) {
        UUID userId = tenantValidator.getCurrentUserId();
        return ResponseEntity.ok(
                phoneService.confirm(userId, request.phone(), request.phoneCountryIso2(), request.otp()));
    }
}
