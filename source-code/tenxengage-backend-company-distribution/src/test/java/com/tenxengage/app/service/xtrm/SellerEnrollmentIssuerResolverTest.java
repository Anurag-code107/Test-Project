package com.tenxengage.app.service.xtrm;

import com.tenxengage.app.service.xtrm.SellerEnrollmentIssuerResolver.EnrollmentIssuer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Which XTRM account should create this seller.
 *
 * <p>The decision is irreversible: XTRM will not create a second user with the same email, so enrolling
 * under the wrong account excludes that seller from their company's distributions permanently. Deferring
 * costs a delay; guessing costs the seller.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SellerEnrollmentIssuerResolverTest {

    @Mock private XtrmCredentialsResolver credentialsResolver;
    @InjectMocks private SellerEnrollmentIssuerResolver resolver;

    private static final UUID CLIENT_ID = UUID.randomUUID();
    private static final UUID COMPANY_ID = UUID.randomUUID();

    private final XtrmCredentials platform =
            new XtrmCredentials("platform-id", "s", "SPN26237883", "203871", "2314");
    private final XtrmCredentials company =
            new XtrmCredentials("company-id", "s", "SPN26241004", "206415", "2314");

    @Test
    void usesTheCompanyWhenItIsConnected() {
        when(credentialsResolver.canPayFromOwnWallet(CLIENT_ID, COMPANY_ID)).thenReturn(true);
        when(credentialsResolver.forCompany(CLIENT_ID, COMPANY_ID)).thenReturn(company);

        EnrollmentIssuer result = resolver.resolve(CLIENT_ID, COMPANY_ID);

        assertThat(result).isInstanceOf(EnrollmentIssuer.UseAccount.class);
        assertThat(((EnrollmentIssuer.UseAccount) result).credentials()).isEqualTo(company);
    }

    @Test
    void defersWhenTheCompanyIsNotConnectedYet() {
        when(credentialsResolver.canPayFromOwnWallet(CLIENT_ID, COMPANY_ID)).thenReturn(false);

        EnrollmentIssuer result = resolver.resolve(CLIENT_ID, COMPANY_ID);

        // Falling back to the platform here is the one thing that must never happen: it would look like
        // success and permanently exclude this seller from their company's distributions.
        assertThat(result).isInstanceOf(EnrollmentIssuer.Defer.class);
        verify(credentialsResolver, never()).platform();
    }

    @Test
    void explainsWhyItDeferred() {
        when(credentialsResolver.canPayFromOwnWallet(CLIENT_ID, COMPANY_ID)).thenReturn(false);

        EnrollmentIssuer result = resolver.resolve(CLIENT_ID, COMPANY_ID);

        assertThat(((EnrollmentIssuer.Defer) result).reason()).containsIgnoringCase("not connected");
    }

    @Test
    void neverAsksForCompanyCredentialsItCannotUse() {
        when(credentialsResolver.canPayFromOwnWallet(CLIENT_ID, COMPANY_ID)).thenReturn(false);

        resolver.resolve(CLIENT_ID, COMPANY_ID);

        // forCompany throws for an unconnected company; asking anyway would turn a clean defer into an
        // exception on the enrollment path.
        verify(credentialsResolver, never()).forCompany(CLIENT_ID, COMPANY_ID);
    }

    @Test
    void usesThePlatformForASellerWithNoCompany() {
        when(credentialsResolver.platform()).thenReturn(platform);

        EnrollmentIssuer result = resolver.resolve(CLIENT_ID, null);

        // A user with no partner company can never be a distribution recipient, so there is nothing to
        // lose by enrolling them under the platform — and personal redemption needs them enrolled.
        assertThat(((EnrollmentIssuer.UseAccount) result).credentials()).isEqualTo(platform);
    }
}
