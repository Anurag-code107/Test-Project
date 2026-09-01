package com.tenxengage.app.service;

import com.tenxengage.app.dto.response.DistributionRecipientResponse;
import com.tenxengage.app.entity.User;
import com.tenxengage.app.entity.enums.DistributionRail;
import com.tenxengage.app.entity.enums.xtrm.XtrmEnrollmentStatus;
import com.tenxengage.app.entity.xtrm.PartnerRedemption;
import com.tenxengage.app.exception.BusinessRuleException;
import com.tenxengage.app.repository.UserRepository;
import com.tenxengage.app.repository.xtrm.PartnerLinkedBankRepository;
import com.tenxengage.app.repository.xtrm.PartnerRedemptionRepository;
import com.tenxengage.app.service.xtrm.XtrmCredentialsResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * A seller enrolled under the platform cannot be paid by their company.
 *
 * <p>XTRM binds a user to whoever created them, and refuses to create them again under another account — so
 * this is permanent for everyone enrolled before company-scoped enrollment existed. Refusing on the listing
 * is the only honest option: the alternative reserves the seller's share and fails at the vendor.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DistributionRecipientIssuerMismatchTest {

    @Mock private UserRepository userRepository;
    @Mock private PartnerRedemptionRepository profileRepository;
    @Mock private PartnerLinkedBankRepository linkedBankRepository;
    @Mock private XtrmCredentialsResolver credentialsResolver;

    private DistributionRecipientService service;

    private static final UUID CLIENT_ID = UUID.randomUUID();
    private static final UUID COMPANY_ID = UUID.randomUUID();
    private static final UUID SELLER_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new DistributionRecipientService(userRepository, profileRepository, linkedBankRepository,
                credentialsResolver, true);

        User seller = new User();
        seller.setId(SELLER_ID);
        seller.setEmail("seller@acme.test");
        when(userRepository.findActiveSellersOfCompany(CLIENT_ID, COMPANY_ID)).thenReturn(List.of(seller));

        when(credentialsResolver.canPayFromOwnWallet(CLIENT_ID, COMPANY_ID)).thenReturn(true);
        when(credentialsResolver.companyIssuerAccountNumber(CLIENT_ID, COMPANY_ID))
                .thenReturn(Optional.of("SPN26241004"));
    }

    private void sellerEnrolledUnder(String issuer) {
        PartnerRedemption profile = PartnerRedemption.builder()
                .clientId(CLIENT_ID).userId(SELLER_ID)
                .enrollmentStatus(XtrmEnrollmentStatus.ENROLLED)
                .recipientUserId("PAT26240089")
                .enrolledIssuerAccountNumber(issuer)
                .build();
        when(profileRepository.findByUserIdAndClientId(any(), any())).thenReturn(Optional.of(profile));
    }

    private DistributionRecipientResponse only(DistributionRail rail) {
        List<DistributionRecipientResponse> out = service.listRecipients(CLIENT_ID, COMPANY_ID, rail);
        assertThat(out).hasSize(1);
        return out.get(0);
    }

    @Test
    void refusesASellerEnrolledUnderThePlatform() {
        sellerEnrolledUnder("SPN26237883");

        DistributionRecipientResponse row = only(DistributionRail.GIFT_CARD);

        assertThat(row.eligible()).isFalse();
        assertThat(row.ineligibleReason()).containsIgnoringCase("cannot receive");
    }

    @Test
    void acceptsASellerEnrolledUnderThisCompany() {
        sellerEnrolledUnder("SPN26241004");

        assertThat(only(DistributionRail.GIFT_CARD).eligible()).isTrue();
    }

    @Test
    void appliesTheSameRuleToBankTransfer() {
        sellerEnrolledUnder("SPN26237883");

        assertThat(only(DistributionRail.BANK_TRANSFER).ineligibleReason())
                .containsIgnoringCase("cannot receive");
    }

    @Test
    void aPlatformBoundSellerNowHasNoReachableRailAtAll() {
        sellerEnrolledUnder("SPN26237883");

        // WALLET_CREDIT used to reach them, since it never touched XTRM. Retired 2026-08-26, so a legacy
        // platform-bound seller cannot receive a distribution on any rail. Permanent, and accepted.
        assertThat(only(DistributionRail.WALLET_CREDIT).eligible()).isFalse();
        assertThat(only(DistributionRail.GIFT_CARD).eligible()).isFalse();
        assertThat(only(DistributionRail.BANK_TRANSFER).eligible()).isFalse();
    }

    @Test
    void refusesAnEnrolledSellerWhoseIssuerWasNeverRecorded() {
        // The legacy shape exactly: enrolled long ago, issuer unknown because the column did not exist.
        // Unknown is not the same as ours — guessing would reserve money for a payout XTRM rejects.
        sellerEnrolledUnder(null);

        DistributionRecipientResponse row = only(DistributionRail.GIFT_CARD);

        assertThat(row.eligible()).isFalse();
        assertThat(row.ineligibleReason()).containsIgnoringCase("cannot receive");
    }

    @Test
    void anUnenrolledSellerGetsTheSetupReasonNotThePermanentOne() {
        PartnerRedemption notEnrolled = PartnerRedemption.builder()
                .clientId(CLIENT_ID).userId(SELLER_ID)
                .enrollmentStatus(XtrmEnrollmentStatus.NOT_ENROLLED)
                .build();
        when(profileRepository.findByUserIdAndClientId(any(), any())).thenReturn(Optional.of(notEnrolled));

        // "Cannot receive" would imply permanence. This seller resolves the moment they enrol under their
        // company, so the actionable setup message is the correct one.
        assertThat(only(DistributionRail.GIFT_CARD).ineligibleReason())
                .containsIgnoringCase("no payout profile");
    }

    @Test
    void refusesSubmitForAPlatformBoundSeller() {
        sellerEnrolledUnder("SPN26237883");

        assertThatThrownBy(() -> service.assertAllEligible(
                CLIENT_ID, COMPANY_ID, List.of(SELLER_ID), DistributionRail.GIFT_CARD))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void refusesSubmitOnWalletCreditForAPlatformBoundSeller() {
        sellerEnrolledUnder("SPN26237883");

        assertThatThrownBy(() -> service.assertAllEligible(
                CLIENT_ID, COMPANY_ID, List.of(SELLER_ID), DistributionRail.WALLET_CREDIT))
                .isInstanceOf(BusinessRuleException.class);
    }
}
