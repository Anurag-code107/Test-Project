package com.tenxengage.app.service;

import com.tenxengage.app.dto.request.CreatePartnerCompanyRequest;
import com.tenxengage.app.entity.enums.PartnerCompanyStatus;
import com.tenxengage.app.exception.BusinessRuleException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The company admin block is all-or-nothing.
 *
 * <p>A half-filled block is guaranteed to fail at XTRM, and that failure arrives on a background thread
 * minutes after the request returned 201, where nobody is looking. Rejecting it here names the missing
 * field while the caller is still holding the response.</p>
 */
class PartnerCompanyAdminDetailsTest {

    // validateAdminDetails reads no collaborator, so nulls are safe here and keep this test about the one
    // rule it exists to pin.
    private final PartnerCompanyService service =
            new PartnerCompanyService(null, null, null, null, null, null, null, null, null);

    private CreatePartnerCompanyRequest request(String firstName, String countryIso2) {
        return new CreatePartnerCompanyRequest(
                "Acme Corp", "EXT-1", List.of(UUID.randomUUID()), "RESELLER",
                PartnerCompanyStatus.ACTIVE, "https://acme.test", "contact@acme.test", "1234567890", "{}",
                firstName, "Singh", "admin@acme.test", "4085556245", countryIso2);
    }

    @Test
    void acceptsACompleteAdminBlock() {
        assertThatCode(() -> service.validateAdminDetails(request("TestP", "US")))
                .doesNotThrowAnyException();
    }

    @Test
    void acceptsNoAdminBlockAtAll() {
        CreatePartnerCompanyRequest none = new CreatePartnerCompanyRequest(
                "Acme Corp", "EXT-1", List.of(UUID.randomUUID()), "RESELLER",
                PartnerCompanyStatus.ACTIVE, null, null, null, "{}",
                null, null, null, null, null);

        // A company with no payout intent is legitimate, and every company that predates this feature
        // looks exactly like this.
        assertThatCode(() -> service.validateAdminDetails(none)).doesNotThrowAnyException();
    }

    @Test
    void refusesAHalfFilledAdminBlockAndNamesTheMissingField() {
        assertThatThrownBy(() -> service.validateAdminDetails(request(null, "US")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("adminFirstName");
    }

    @Test
    void treatsBlankAsMissingRatherThanSupplied() {
        assertThatThrownBy(() -> service.validateAdminDetails(request("   ", "US")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("adminFirstName");
    }

    @Test
    void refusesACountryXtrmDoesNotSupport() {
        assertThatThrownBy(() -> service.validateAdminDetails(request("TestP", "ZZ")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("ZZ");
    }

    @Test
    void acceptsASupportedNonUsCountry() {
        assertThatCode(() -> service.validateAdminDetails(request("TestP", "GB")))
                .doesNotThrowAnyException();
    }
}
