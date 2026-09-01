package com.tenxengage.app.entity.xtrm;

import com.tenxengage.app.entity.enums.xtrm.XtrmEnrollmentStatus;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Which XTRM account enrolled this seller.
 *
 * <p>XTRM binds a user to whoever created them and refuses a second user with the same email, so this is a
 * permanent fact about the row rather than something that can be corrected later. Everything that decides
 * whether a company can pay a seller reads it.</p>
 */
class PartnerRedemptionIssuerTest {

    private PartnerRedemption enrolledUnder(String issuer) {
        return PartnerRedemption.builder()
                .clientId(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .enrollmentStatus(XtrmEnrollmentStatus.ENROLLED)
                .recipientUserId("PAT26241022")
                .enrolledIssuerAccountNumber(issuer)
                .build();
    }

    @Test
    void matchesTheAccountThatEnrolledIt() {
        assertThat(enrolledUnder("SPN26241004").isEnrolledUnder("SPN26241004")).isTrue();
    }

    @Test
    void doesNotMatchAnotherAccount() {
        // A platform-enrolled seller against a company: this is the case that must be refused.
        assertThat(enrolledUnder("SPN26237883").isEnrolledUnder("SPN26241004")).isFalse();
    }

    @Test
    void isNotEnrolledUnderAnythingWhenTheIssuerIsUnknown() {
        // NULL means enrolled before company-scoped enrollment existed. Refusing is the same answer a
        // backfill would have produced, without depending on an environment-specific literal.
        assertThat(enrolledUnder(null).isEnrolledUnder("SPN26241004")).isFalse();
    }

    @Test
    void ignoresSurroundingWhitespaceAndCase() {
        assertThat(enrolledUnder("spn26241004 ").isEnrolledUnder("SPN26241004")).isTrue();
    }

    @Test
    void isNotEnrolledUnderABlankAccount() {
        assertThat(enrolledUnder("SPN26241004").isEnrolledUnder("  ")).isFalse();
    }
}
