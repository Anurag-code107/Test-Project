package com.tenxengage.app.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

/**
 * Body for {@code POST /partner-companies/{id}/xtrm/connect}.
 *
 * <p>Every field is optional, and the body may be omitted entirely. What the call does is decided by the
 * account row's state, not by which fields the caller filled in — so there is no mode flag to get wrong:</p>
 *
 * <ul>
 *   <li>a company that predates this feature supplies its admin details, because it has none stored;</li>
 *   <li>a retry sends nothing and resumes from whatever the {@code PENDING} row already holds;</li>
 *   <li>a wallet id supplied by hand finishes a row whose wallet could not be discovered.</li>
 * </ul>
 */
public record ConnectXtrmAccountRequest(
    @Size(max = 100) String adminFirstName,
    @Size(max = 100) String adminLastName,
    @Email(message = "Admin email must be valid") @Size(max = 255) String adminEmail,
    @Size(max = 20) String adminMobileNumber,
    @Size(max = 100) String adminCity,
    @Size(max = 100) String adminRegion,
    @Size(max = 20) String adminPostalCode,
    @Size(min = 2, max = 2, message = "Admin country must be a 2-letter ISO code") String adminCountryIso2,
    @Size(max = 50) String xtrmWalletId
) {

    /** An empty body — the retry case, and the default when no body is posted at all. */
    public static ConnectXtrmAccountRequest empty() {
        return new ConnectXtrmAccountRequest(null, null, null, null, null, null, null, null, null);
    }
}
