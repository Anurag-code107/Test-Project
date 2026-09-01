package com.tenxengage.app.dto.response;

/**
 * What the company admin sees on their own payout-setup screen.
 *
 * <p>{@code complete} is what the UI gates on: false means the address is still missing and the company
 * cannot be provisioned. Carries no credentials, and no other company's data.</p>
 */
public record CompanyAdminProfileResponse(
    String companyName,
    String adminEmail,
    String adminCity,
    String adminRegion,
    String adminPostalCode,
    boolean complete,
    PartnerCompanyXtrmAccountResponse xtrmAccount,
    /**
     * Where this admin completes identity verification at the provider.
     *
     * <p>Served by the server rather than held in the frontend so it can never disagree with the API host
     * about which XTRM this deployment talks to — a sandbox admin sent to the production portal would
     * verify an account that does not exist here.</p>
     *
     * <p>Opened in a new tab, never embedded: every xtrm.com host sends {@code X-Frame-Options: SAMEORIGIN}
     * (verified 2026-09-01), so a browser refuses to frame it.</p>
     */
    String portalUrl
) {}
