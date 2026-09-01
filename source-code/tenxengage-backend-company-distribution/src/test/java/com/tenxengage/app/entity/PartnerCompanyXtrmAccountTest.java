package com.tenxengage.app.entity;

import com.tenxengage.app.entity.enums.XtrmAccountStatus;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code isPayoutReady} is the last gate before a company's money moves.
 *
 * <p>V58 makes three of the columns it depends on nullable, so a {@code PENDING} row can record partial
 * progress — credentials held, wallet not yet discovered. Nullable columns with an unchanged readiness
 * check would mean a {@code CONNECTED} row with no wallet reads as payable and fails at dispatch, after
 * funds are reserved and a seller has been promised an award.</p>
 */
class PartnerCompanyXtrmAccountTest {

    private PartnerCompanyXtrmAccount.PartnerCompanyXtrmAccountBuilder connected() {
        return PartnerCompanyXtrmAccount.builder()
                .clientId(UUID.randomUUID())
                .partnerCompanyId(UUID.randomUUID())
                .status(XtrmAccountStatus.CONNECTED)
                .xtrmAccountNumber("SPN26241004")
                .xtrmWalletId("206415")
                .encryptedCredentials("blob");
    }

    @Test
    void isPayoutReadyWhenEverythingIsPresent() {
        assertThat(connected().build().isPayoutReady()).isTrue();
    }

    @Test
    void isNotPayoutReadyWithoutAWallet() {
        assertThat(connected().xtrmWalletId(null).build().isPayoutReady()).isFalse();
    }

    @Test
    void isNotPayoutReadyWithoutAnAccountNumber() {
        assertThat(connected().xtrmAccountNumber(null).build().isPayoutReady()).isFalse();
    }

    @Test
    void isNotPayoutReadyWithoutCredentials() {
        assertThat(connected().encryptedCredentials(null).build().isPayoutReady()).isFalse();
    }

    @Test
    void isNotPayoutReadyOnBlankRatherThanNullIdentifiers() {
        assertThat(connected().xtrmWalletId("  ").build().isPayoutReady()).isFalse();
        assertThat(connected().xtrmAccountNumber("").build().isPayoutReady()).isFalse();
    }

    @Test
    void isNotPayoutReadyWhilePending() {
        assertThat(connected().status(XtrmAccountStatus.PENDING).build().isPayoutReady()).isFalse();
    }

    @Test
    void isNotPayoutReadyWhenDisabled() {
        assertThat(connected().status(XtrmAccountStatus.DISABLED).build().isPayoutReady()).isFalse();
    }

    /**
     * The claim row: written inside the company-create transaction so the unique constraint, not the
     * vendor, settles concurrent provisioning. It knows nothing yet, and must be a legal row anyway.
     */
    @Test
    void aClaimRowIsValidAndNotPayable() {
        PartnerCompanyXtrmAccount claim = PartnerCompanyXtrmAccount.builder()
                .clientId(UUID.randomUUID())
                .partnerCompanyId(UUID.randomUUID())
                .status(XtrmAccountStatus.PENDING)
                .build();

        assertThat(claim.getXtrmAccountNumber()).isNull();
        assertThat(claim.getXtrmWalletId()).isNull();
        assertThat(claim.getEncryptedCredentials()).isNull();
        assertThat(claim.isPayoutReady()).isFalse();
    }

    /**
     * The state between CreateBeneficiary and wallet discovery: credentials are held — the thing that
     * cannot be re-obtained — but the company still cannot pay.
     */
    @Test
    void credentialsHeldWithoutAWalletIsStillNotPayable() {
        PartnerCompanyXtrmAccount partial = PartnerCompanyXtrmAccount.builder()
                .clientId(UUID.randomUUID())
                .partnerCompanyId(UUID.randomUUID())
                .status(XtrmAccountStatus.PENDING)
                .xtrmAccountNumber("SPN26241004")
                .encryptedCredentials("blob")
                .accountIdentityLevel("Basic")
                .xtrmBeneficiaryName("Acme Corp (Apple)")
                .build();

        assertThat(partial.getEncryptedCredentials()).isEqualTo("blob");
        assertThat(partial.getAccountIdentityLevel()).isEqualTo("Basic");
        assertThat(partial.getXtrmBeneficiaryName()).isEqualTo("Acme Corp (Apple)");
        assertThat(partial.isPayoutReady()).isFalse();
    }
}
