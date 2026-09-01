package com.tenxengage.app.service;

import com.tenxengage.app.dto.request.CompleteCompanyAdminProfileRequest;
import com.tenxengage.app.dto.response.CompanyAdminProfileResponse;
import com.tenxengage.app.dto.response.PartnerCompanyXtrmAccountResponse;
import com.tenxengage.app.entity.PartnerCompany;
import com.tenxengage.app.exception.BusinessRuleException;
import com.tenxengage.app.exception.ResourceNotFoundException;
import com.tenxengage.app.repository.PartnerCompanyRepository;
import com.tenxengage.app.repository.PartnerCompanyXtrmAccountRepository;
import com.tenxengage.app.security.TenantValidator;
import com.tenxengage.app.service.xtrm.XtrmCompanyProvisioningService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * A company admin finishing their own payout setup.
 *
 * <p>Mirrors how a seller completes their redemption profile: identity comes from their user record, they
 * supply the address, and that is what triggers enrollment at the vendor. Doing it here rather than at
 * company creation means the person who owns the email is the one who types it — and XTRM refuses to reuse
 * an email, so a typo is permanent.</p>
 */
@Service
public class CompanyAdminProfileService {

    private final PartnerCompanyRepository companyRepository;
    private final PartnerCompanyXtrmAccountRepository xtrmAccountRepository;
    private final TenantValidator tenantValidator;
    private final XtrmCompanyProvisioningService provisioningService;

    /** Where the admin finishes identity verification. Blank when unset, so the UI simply omits the link. */
    @Value("${redemption.xtrm.portal-url:}")
    private String portalUrl;

    /** Self-proxy: the transactional save must be proxied when called from the non-transactional method. */
    private CompanyAdminProfileService self;

    public CompanyAdminProfileService(PartnerCompanyRepository companyRepository,
                                      PartnerCompanyXtrmAccountRepository xtrmAccountRepository,
                                      TenantValidator tenantValidator,
                                      XtrmCompanyProvisioningService provisioningService) {
        this.companyRepository = companyRepository;
        this.xtrmAccountRepository = xtrmAccountRepository;
        this.tenantValidator = tenantValidator;
        this.provisioningService = provisioningService;
    }

    @Autowired
    public void setSelf(@Lazy CompanyAdminProfileService self) {
        this.self = self;
    }

    @Transactional(readOnly = true)
    public CompanyAdminProfileResponse getProfile() {
        return toResponse(loadOwnCompany());
    }

    /**
     * Save the address, then provision.
     *
     * <p>Deliberately not {@code @Transactional}: provisioning makes three HTTP calls to XTRM, and holding
     * a database connection open for the vendor's latency is what the create path already avoids.</p>
     */
    public CompanyAdminProfileResponse completeProfile(CompleteCompanyAdminProfileRequest request) {
        UUID clientId = tenantValidator.getCurrentClientId();

        // Through `self`, not `this`: a @Transactional method invoked directly on the same bean bypasses
        // the proxy, and the address write below would run unprotected.
        UUID companyId = self.saveAddressAndClaim(request);

        provisioningService.provision(clientId, companyId);

        return toResponse(loadOwnCompany());
    }

    /** The transactional half: persist the address and reserve the provisioning slot. */
    @Transactional
    public UUID saveAddressAndClaim(CompleteCompanyAdminProfileRequest request) {
        UUID clientId = tenantValidator.getCurrentClientId();
        PartnerCompany company = loadOwnCompany();

        if (!company.hasAdminIdentity()) {
            throw new BusinessRuleException("ADMIN_IDENTITY_MISSING",
                    "This company has no admin identity on file. Ask your administrator to set one.");
        }

        company.setAdminCity(request.adminCity());
        company.setAdminRegion(request.adminRegion());
        company.setAdminPostalCode(request.adminPostalCode());
        companyRepository.save(company);

        // Claim only if nobody has: uq_xtrm_account_per_company would reject a second row, and a
        // resubmitted profile must retry rather than fail.
        if (xtrmAccountRepository.findByClientIdAndPartnerCompanyId(clientId, company.getId()).isEmpty()) {
            provisioningService.claim(clientId, company.getId());
        }
        return company.getId();
    }

    /**
     * The caller's own company, and only ever that one.
     *
     * <p>Read from the security context rather than a path variable, so this endpoint cannot be pointed at
     * another company by changing an id.</p>
     */
    private PartnerCompany loadOwnCompany() {
        UUID clientId = tenantValidator.getCurrentClientId();
        UUID companyId = tenantValidator.getCurrentPartnerCompanyId();
        if (companyId == null) {
            throw new BusinessRuleException("NOT_A_COMPANY_ADMIN",
                    "Only a partner company's admin can complete this profile.");
        }
        PartnerCompany company = companyRepository.findByIdAndClientId(companyId, clientId)
                .orElseThrow(() -> new ResourceNotFoundException("PartnerCompany", "id", companyId));
        requireDefaultAdmin(company);
        return company;
    }

    /**
     * Only the one admin whose email the company's beneficiary is bound to.
     *
     * <p>A permission cannot express this. Every company admin holds the same shared PARTNER_ADMIN role, so
     * {@code action.redemption.distribute} is identical for all of them — it says "this user may distribute
     * rewards", never "this user is the admin XTRM knows".</p>
     *
     * <p>The distinction is not cosmetic. There is one beneficiary per company, bound at XTRM to
     * {@code admin_email}, and XTRM will neither change that address nor reuse it. The address fields live on
     * the shared company row, so a second admin completing this form would overwrite the first one's address
     * while the email already spent at the vendor stayed the first one's — leaving a beneficiary whose
     * address belongs to one person and whose email belongs to another, with no way back.</p>
     */
    private void requireDefaultAdmin(PartnerCompany company) {
        String onFile = company.getAdminEmail();
        if (onFile == null || onFile.isBlank()) {
            throw new BusinessRuleException("ADMIN_IDENTITY_MISSING",
                    "This company has no admin identity on file. Ask your administrator to set one.");
        }
        String caller = tenantValidator.getCurrentUserDetails().getUsername();
        if (caller == null || !onFile.trim().equalsIgnoreCase(caller.trim())) {
            throw new AccessDeniedException(
                    "Only this company's default admin can view or complete its payout setup.");
        }
    }

    private CompanyAdminProfileResponse toResponse(PartnerCompany company) {
        UUID clientId = tenantValidator.getCurrentClientId();
        return new CompanyAdminProfileResponse(
                company.getName(),
                company.getAdminEmail(),
                company.getAdminCity(),
                company.getAdminRegion(),
                company.getAdminPostalCode(),
                company.hasCompleteAdminDetails(),
                PartnerCompanyXtrmAccountResponse.from(
                        xtrmAccountRepository.findByClientIdAndPartnerCompanyId(clientId, company.getId())
                                .orElse(null)),
                portalUrl == null || portalUrl.isBlank() ? null : portalUrl);
    }
}
