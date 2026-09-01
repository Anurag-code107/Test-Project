package com.tenxengage.app.controller;

import com.tenxengage.app.audit.Audited;
import com.tenxengage.app.dto.request.CompleteCompanyAdminProfileRequest;
import com.tenxengage.app.dto.response.CompanyAdminProfileResponse;
import com.tenxengage.app.security.RequiresPermission;
import com.tenxengage.app.service.CompanyAdminProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * A company admin's own payout setup.
 *
 * <p>Scoped to the caller's company by the security context — there is no company id in the path, so it
 * cannot be aimed at anyone else's.</p>
 *
 * <p>Gated twice, because one gate cannot say this.</p>
 *
 * <p>The permission {@code action.redemption.distribute} narrows callers to company admins. It cannot narrow
 * further: every company admin holds the same shared PARTNER_ADMIN role, so the permission is identical for
 * all of them. (A dedicated permission would not help — it would be granted per role, not per person — and
 * would need seeding in both {@code client_role_permissions} and {@code client_permission_grants}, or
 * Layer-0 strips it and every call returns 403.)</p>
 *
 * <p>The service then narrows to the one admin the company's payout account belongs to, by matching the
 * caller against {@code admin_email}, and answers 403 to the rest. That second gate is the one that matters:
 * a company has a single beneficiary bound at XTRM to that address, and the address fields it is completed
 * with live on the shared company row.</p>
 */
@RestController
@RequestMapping("/api/v1/company-admin/profile")
@Tag(name = "Company Admin Profile", description = "A partner company admin completing their payout setup")
public class CompanyAdminProfileController {

    private final CompanyAdminProfileService service;

    public CompanyAdminProfileController(CompanyAdminProfileService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "My company's payout setup",
               description = "What is stored, what is still missing, and the current payout-account status.")
    @RequiresPermission("action.redemption.distribute")
    public ResponseEntity<CompanyAdminProfileResponse> getProfile() {
        return ResponseEntity.ok(service.getProfile());
    }

    @PutMapping
    @Operation(summary = "Complete my company's payout setup",
               description = "Saves the admin address and provisions the company's payout account.")
    @RequiresPermission("action.redemption.distribute")
    @Audited(action = "Completed", resourceType = "PARTNER_COMPANY",
             description = "Company admin completed their payout setup",
             resourceName = "#result.body.companyName")
    public ResponseEntity<CompanyAdminProfileResponse> completeProfile(
            @Valid @RequestBody CompleteCompanyAdminProfileRequest request) {
        return ResponseEntity.ok(service.completeProfile(request));
    }
}
