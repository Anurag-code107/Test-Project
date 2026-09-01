package com.tenxengage.app.service.xtrm;

import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Which XTRM account should create a given seller.
 *
 * <p>XTRM binds a user to whoever creates them and refuses a second user with the same email, so this
 * decision cannot be revisited. Enrolling a partner company's seller under the platform would look like
 * success and exclude that seller from their company's distributions forever — which is why there is no
 * fallback here, only {@link EnrollmentIssuer.Defer}.</p>
 */
@Service
public class SellerEnrollmentIssuerResolver {

    /**
     * Either an account to enrol as, or a reason to wait.
     *
     * <p>Deliberately only two cases. A third — "use the platform instead" — is exactly the mistake this
     * class exists to prevent, and leaving it out of the type makes it unwriteable rather than merely
     * discouraged.</p>
     */
    public sealed interface EnrollmentIssuer {

        /** Enrol as this account. */
        record UseAccount(XtrmCredentials credentials) implements EnrollmentIssuer { }

        /** Do not enrol at all yet, and say why. */
        record Defer(String reason) implements EnrollmentIssuer { }
    }

    private final XtrmCredentialsResolver credentialsResolver;

    public SellerEnrollmentIssuerResolver(XtrmCredentialsResolver credentialsResolver) {
        this.credentialsResolver = credentialsResolver;
    }

    public EnrollmentIssuer resolve(UUID clientId, UUID partnerCompanyId) {
        if (partnerCompanyId == null) {
            // No company means this person can never be a distribution recipient, so nothing is lost by
            // enrolling them under the platform — and personal redemption needs them enrolled at all.
            return new EnrollmentIssuer.UseAccount(credentialsResolver.platform());
        }
        if (!credentialsResolver.canPayFromOwnWallet(clientId, partnerCompanyId)) {
            // Waiting costs this seller a delay. Enrolling under the platform instead would cost them
            // company distributions permanently — the two are not comparable.
            return new EnrollmentIssuer.Defer("This seller's company is not connected to XTRM yet.");
        }
        return new EnrollmentIssuer.UseAccount(
                credentialsResolver.forCompany(clientId, partnerCompanyId));
    }
}
