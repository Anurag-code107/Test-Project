package com.tenxengage.app.service;

import com.tenxengage.app.entity.PartnerCompany;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Two different questions about the same eight fields.
 *
 * <p>{@code hasAdminIdentity} asks "can this person be given a login?" — answered at company creation.
 * {@code hasCompleteAdminDetails} asks "can XTRM create a beneficiary for them?" — answered only once the
 * admin has filled in their own address. Conflating the two is what made provisioning fire too early.</p>
 */
class PartnerCompanyAdminIdentityTest {

    private PartnerCompany.PartnerCompanyBuilder withIdentity() {
        return PartnerCompany.builder()
                .name("Acme Corp")
                .adminFirstName("TestP")
                .adminLastName("Singh")
                .adminEmail("admin@acme.test")
                .adminMobileNumber("4085556245")
                .adminCountryIso2("US");
    }

    @Test
    void identityIsCompleteWithTheFiveCreateTimeFields() {
        assertThat(withIdentity().build().hasAdminIdentity()).isTrue();
    }

    @Test
    void identityAloneIsNotEnoughForXtrm() {
        // The address is still missing, so provisioning must not fire yet.
        assertThat(withIdentity().build().hasCompleteAdminDetails()).isFalse();
    }

    @Test
    void bothAreCompleteOnceTheAddressArrives() {
        PartnerCompany full = withIdentity()
                .adminCity("San Francisco").adminRegion("CA").adminPostalCode("94105").build();

        assertThat(full.hasAdminIdentity()).isTrue();
        assertThat(full.hasCompleteAdminDetails()).isTrue();
    }

    @Test
    void identityIsIncompleteWithoutAnEmail() {
        // The email is the login and the XTRM identity — the one field that cannot be supplied later.
        assertThat(withIdentity().adminEmail(null).build().hasAdminIdentity()).isFalse();
    }

    @Test
    void identityIsIncompleteWithoutAMobile() {
        // CreateUserRequest requires phone + phoneCountryIso2, so a login cannot be made without it.
        assertThat(withIdentity().adminMobileNumber("  ").build().hasAdminIdentity()).isFalse();
    }
}
