package com.tenxengage.app.service.xtrm;

import com.tenxengage.app.entity.PartnerCompanyXtrmAccount;
import com.tenxengage.app.entity.enums.XtrmAccountStatus;
import com.tenxengage.app.exception.BusinessRuleException;
import com.tenxengage.app.repository.PartnerCompanyXtrmAccountRepository;
import com.tenxengage.app.service.ConnectorEncryptionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Choosing which XTRM account a payout authenticates as.
 *
 * <p>The property worth protecting here is that a company payout never silently becomes a platform payout.
 * XTRM would happily accept the platform's credentials and move the money — out of the client's wallet
 * instead of the company's. That is a real transfer from the wrong pocket, and nothing downstream would
 * notice, so "not connected" has to fail loudly rather than fall back.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class XtrmCredentialsResolverTest {

    @Mock private PartnerCompanyXtrmAccountRepository accountRepository;
    @Mock private ConnectorEncryptionService encryptionService;
    @InjectMocks private XtrmCredentialsResolver resolver;

    private static final UUID CLIENT_ID = UUID.randomUUID();
    private static final UUID COMPANY_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(resolver, "platformClientId", "platform-client");
        ReflectionTestUtils.setField(resolver, "platformClientSecret", "platform-secret");
        ReflectionTestUtils.setField(resolver, "platformIssuerAccountNumber", "SPN26237883");
        ReflectionTestUtils.setField(resolver, "platformWalletId", "203871");
        ReflectionTestUtils.setField(resolver, "programId", "2314");
        // Empty = accept any tier, which is how it ships. Set explicitly so the cases below keep
        // meaning what they meant before the gate existed.
        ReflectionTestUtils.setField(resolver, "acceptableIdentityLevels", "");
    }

    private PartnerCompanyXtrmAccount account(XtrmAccountStatus status, String blob) {
        return PartnerCompanyXtrmAccount.builder()
                .clientId(CLIENT_ID)
                .partnerCompanyId(COMPANY_ID)
                .xtrmAccountNumber("SPN26240019")
                .xtrmWalletId("206415")
                .encryptedCredentials(blob)
                .status(status)
                .build();
    }

    private void stored(XtrmAccountStatus status, String blob) {
        when(accountRepository.findByClientIdAndPartnerCompanyId(CLIENT_ID, COMPANY_ID))
                .thenReturn(Optional.of(account(status, blob)));
    }

    // ─────────────────────────────────────────────── platform

    @Test
    void platform_usesTheConfiguredClientAccount() {
        XtrmCredentials c = resolver.platform();

        assertThat(c.clientId()).isEqualTo("platform-client");
        assertThat(c.issuerAccountNumber()).isEqualTo("SPN26237883");
        assertThat(c.walletId()).isEqualTo("203871");
    }

    // ─────────────────────────────────────────────── company

    @Test
    void forCompany_returnsTheCompanysOwnAccountAndWallet() {
        stored(XtrmAccountStatus.CONNECTED, "blob");
        when(encryptionService.decrypt("blob"))
                .thenReturn(Map.of("clientId", "pushpa-client", "clientSecret", "pushpa-secret"));

        XtrmCredentials c = resolver.forCompany(CLIENT_ID, COMPANY_ID);

        assertThat(c.clientId()).isEqualTo("pushpa-client");
        assertThat(c.clientSecret()).isEqualTo("pushpa-secret");
        assertThat(c.issuerAccountNumber()).isEqualTo("SPN26240019");
        assertThat(c.walletId()).isEqualTo("206415");
        // programId is platform-wide, not per company
        assertThat(c.programId()).isEqualTo("2314");
    }

    /**
     * The important one. Falling back to platform credentials here would pay the seller out of the client's
     * money instead of the company's — a real transfer from the wrong account that no downstream check
     * would catch, because XTRM would accept it.
     */
    @Test
    void forCompany_noAccount_throwsRatherThanFallingBackToPlatform() {
        when(accountRepository.findByClientIdAndPartnerCompanyId(any(), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> resolver.forCompany(CLIENT_ID, COMPANY_ID))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("not set up to pay from its own wallet");
    }

    @Test
    void forCompany_pendingAccount_throws() {
        stored(XtrmAccountStatus.PENDING, null);

        assertThatThrownBy(() -> resolver.forCompany(CLIENT_ID, COMPANY_ID))
                .isInstanceOf(BusinessRuleException.class);
    }

    /** Revoked or suspended must stop paying immediately, even though the blob is still on the row. */
    @Test
    void forCompany_disabledAccount_throwsEvenWithCredentialsStillStored() {
        stored(XtrmAccountStatus.DISABLED, "blob");

        assertThatThrownBy(() -> resolver.forCompany(CLIENT_ID, COMPANY_ID))
                .isInstanceOf(BusinessRuleException.class);
    }

    /** A corrupt or unreadable blob must not surface the decryption error, which could echo key material. */
    @Test
    void forCompany_undecryptableBlob_failsWithoutLeakingTheCause() {
        stored(XtrmAccountStatus.CONNECTED, "corrupt");
        when(encryptionService.decrypt("corrupt")).thenThrow(new RuntimeException("bad tag: 0xDEADBEEF"));

        assertThatThrownBy(() -> resolver.forCompany(CLIENT_ID, COMPANY_ID))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageNotContaining("DEADBEEF");
    }

    @Test
    void forCompany_blobMissingTheSecret_throws() {
        stored(XtrmAccountStatus.CONNECTED, "blob");
        when(encryptionService.decrypt("blob")).thenReturn(Map.of("clientId", "pushpa-client"));

        assertThatThrownBy(() -> resolver.forCompany(CLIENT_ID, COMPANY_ID))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("incomplete");
    }

    // ─────────────────────────────────────────────── readiness

    @Test
    void canPayFromOwnWallet_trueOnlyWhenConnectedWithCredentials() {
        stored(XtrmAccountStatus.CONNECTED, "blob");
        assertThat(resolver.canPayFromOwnWallet(CLIENT_ID, COMPANY_ID)).isTrue();

        stored(XtrmAccountStatus.PENDING, null);
        assertThat(resolver.canPayFromOwnWallet(CLIENT_ID, COMPANY_ID)).isFalse();

        when(accountRepository.findByClientIdAndPartnerCompanyId(any(), any())).thenReturn(Optional.empty());
        assertThat(resolver.canPayFromOwnWallet(CLIENT_ID, COMPANY_ID)).isFalse();
    }

    // ─────────────────────────────────────────────── secret handling

    /**
     * A record's generated toString prints every component. These objects are handled on the payout path,
     * where exceptions and debug lines get logged, so the secret must not be one of them.
     */
    @Test
    void toString_redactsTheClientSecret() {
        String s = new XtrmCredentials("cid", "super-secret-value", "SPN1", "W1", "P1").toString();

        assertThat(s).doesNotContain("super-secret-value");
        assertThat(s).contains("clientSecret=***");
        // The non-secret identifiers stay readable — they are what makes a log line diagnosable.
        assertThat(s).contains("cid").contains("SPN1").contains("W1");
    }

    @Test
    void acceptsAnyIdentityLevelWhenTheGateIsUnset() {
        PartnerCompanyXtrmAccount account = account(XtrmAccountStatus.CONNECTED, "blob");
        account.setAccountIdentityLevel("Basic");

        assertThat(resolver.hasAcceptableIdentityLevel(account)).isTrue();
    }

    @Test
    void refusesALevelOutsideTheConfiguredSet() {
        ReflectionTestUtils.setField(resolver, "acceptableIdentityLevels", "Verified,Advanced");
        PartnerCompanyXtrmAccount account = account(XtrmAccountStatus.CONNECTED, "blob");
        account.setAccountIdentityLevel("Basic");

        assertThat(resolver.hasAcceptableIdentityLevel(account)).isFalse();
    }

    @Test
    void acceptsAConfiguredLevelIgnoringCaseAndSpacing() {
        ReflectionTestUtils.setField(resolver, "acceptableIdentityLevels", "Verified, Advanced");
        PartnerCompanyXtrmAccount account = account(XtrmAccountStatus.CONNECTED, "blob");
        account.setAccountIdentityLevel("advanced");

        assertThat(resolver.hasAcceptableIdentityLevel(account)).isTrue();
    }

    @Test
    void refusesAnAccountWithNoRecordedLevelOnceTheGateIsSet() {
        ReflectionTestUtils.setField(resolver, "acceptableIdentityLevels", "Verified");
        PartnerCompanyXtrmAccount account = account(XtrmAccountStatus.CONNECTED, "blob");

        assertThat(resolver.hasAcceptableIdentityLevel(account)).isFalse();
    }
}
