package com.tenxengage.app.dto.response;

import com.tenxengage.app.entity.PartnerCompanyXtrmAccount;

/**
 * A partner company's connection to XTRM, as an admin sees it.
 *
 * <p>Carries identifiers and a failure reason only. <b>It must never gain a credentials field, in any shape
 * or under any name</b> — this record is serialized straight to a browser, and the credentials it would be
 * exposing are the authority to move that company's money.</p>
 *
 * @param status       PENDING, CONNECTED or DISABLED
 * @param accountNumber the company's SPN at XTRM; null until CreateBeneficiary has succeeded
 * @param identityLevel XTRM's KYC tier for the account, e.g. {@code Basic}
 * @param lastError     why the last connection attempt failed; present only while PENDING
 */
public record PartnerCompanyXtrmAccountResponse(
    String status,
    String accountNumber,
    String identityLevel,
    String lastError
) {

    /** Null in, null out — a company that has never been connected has no block to show. */
    public static PartnerCompanyXtrmAccountResponse from(PartnerCompanyXtrmAccount a) {
        if (a == null) {
            return null;
        }
        return new PartnerCompanyXtrmAccountResponse(
                a.getStatus() == null ? null : a.getStatus().name(),
                a.getXtrmAccountNumber(),
                a.getAccountIdentityLevel(),
                a.getLastError());
    }
}
