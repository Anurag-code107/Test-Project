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
 * A company that has not finished XTRM setup cannot pay anyone.
 *
 * <p>That is a property of the company, not of any individual seller — but it is reported per-seller on the
 * listing, because that is where an admin sees it before building a distribution rather than after being
 * refused at submit.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DistributionRecipientCompanyConnectionTest {

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
        org.mockito.Mockito.lenient()
                .when(credentialsResolver.companyIssuerAccountNumber(org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any()))
                .thenReturn(java.util.Optional.of("SPN26241004"));

        // An enrolled seller, so nothing about the seller can explain a refusal — only the company can.
        PartnerRedemption profile = PartnerRedemption.builder()
                .clientId(CLIENT_ID).userId(SELLER_ID)
                .enrollmentStatus(XtrmEnrollmentStatus.ENROLLED)
                // Enrolled by this company, so these cases keep measuring what they were
                // written for rather than the issuer rule added alongside them.
                .enrolledIssuerAccountNumber("SPN26241004")
                .recipientUserId("PAT26240089")
                .build();
        when(profileRepository.findByUserIdAndClientId(any(), any())).thenReturn(Optional.of(profile));
    }

    private DistributionRecipientResponse only(DistributionRail rail) {
        List<DistributionRecipientResponse> out = service.listRecipients(CLIENT_ID, COMPANY_ID, rail);
        assertThat(out).hasSize(1);
        return out.get(0);
    }

    @Test
    void reportsTheCompanyBlockerOnTheListing() {
        when(credentialsResolver.canPayFromOwnWallet(CLIENT_ID, COMPANY_ID)).thenReturn(false);

        DistributionRecipientResponse row = only(DistributionRail.GIFT_CARD);

        assertThat(row.eligible()).isFalse();
        assertThat(row.ineligibleReason()).containsIgnoringCase("isn't connected");
    }

    @Test
    void appliesTheBlockerToBankTransferToo() {
        when(credentialsResolver.canPayFromOwnWallet(CLIENT_ID, COMPANY_ID)).thenReturn(false);

        assertThat(only(DistributionRail.BANK_TRANSFER).ineligibleReason()).containsIgnoringCase("isn't connected");
    }

    @Test
    void theWalletRailIsRetiredRegardlessOfCompanyConnection() {
        when(credentialsResolver.canPayFromOwnWallet(CLIENT_ID, COMPANY_ID)).thenReturn(false);

        // It used to be the rail that kept the store usable while XTRM setup was pending. Retired
        // 2026-08-26, so an unconnected company no longer has any rail at all.
        DistributionRecipientResponse row = only(DistributionRail.WALLET_CREDIT);

        assertThat(row.eligible()).isFalse();
        assertThat(row.ineligibleReason()).containsIgnoringCase("no longer available");
    }

    @Test
    void letsAnEnrolledSellerThroughOnceTheCompanyIsConnected() {
        when(credentialsResolver.canPayFromOwnWallet(CLIENT_ID, COMPANY_ID)).thenReturn(true);

        assertThat(only(DistributionRail.GIFT_CARD).eligible()).isTrue();
    }

    @Test
    void refusesSubmitWhileTheCompanyIsNotConnected() {
        when(credentialsResolver.canPayFromOwnWallet(CLIENT_ID, COMPANY_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.assertAllEligible(
                CLIENT_ID, COMPANY_ID, List.of(SELLER_ID), DistributionRail.GIFT_CARD))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void refusesSubmitOnWalletCreditWhileTheCompanyIsNotConnected() {
        when(credentialsResolver.canPayFromOwnWallet(CLIENT_ID, COMPANY_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.assertAllEligible(
                CLIENT_ID, COMPANY_ID, List.of(SELLER_ID), DistributionRail.WALLET_CREDIT))
                .isInstanceOf(BusinessRuleException.class);
    }
}
