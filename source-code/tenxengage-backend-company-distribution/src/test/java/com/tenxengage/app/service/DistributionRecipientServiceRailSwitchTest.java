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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * The XTRM rail kill switch.
 *
 * <p>XTRM has no company-to-user transfer API, so {@code GIFT_CARD} and {@code BANK_TRANSFER} cannot be
 * <b>sent</b> — but they remain fully browsable. These tests pin the three properties that make that safe:
 * the listing still reports each seller's real readiness (so an admin sees who would be reachable once XTRM
 * ships), sending is refused server-side no matter what the client posts, and {@code WALLET_CREDIT} keeps
 * working throughout.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DistributionRecipientServiceRailSwitchTest {

    @Mock private UserRepository userRepository;
    @Mock private PartnerRedemptionRepository profileRepository;
    @Mock private PartnerLinkedBankRepository linkedBankRepository;
    @Mock private com.tenxengage.app.service.xtrm.XtrmCredentialsResolver credentialsResolver;

    private static final UUID CLIENT_ID = UUID.randomUUID();
    private static final UUID COMPANY_ID = UUID.randomUUID();
    private static final UUID SELLER_ID = UUID.randomUUID();

    private User seller;

    @BeforeEach
    void setUp() {
        seller = new User();
        seller.setId(SELLER_ID);
        seller.setFirstName("Carol");
        seller.setLastName("Seller");
        seller.setEmail("carol@example.com");
        when(userRepository.findActiveSellersOfCompany(CLIENT_ID, COMPANY_ID)).thenReturn(List.of(seller));
        org.mockito.Mockito.lenient()
                .when(credentialsResolver.companyIssuerAccountNumber(org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any()))
                .thenReturn(java.util.Optional.of("SPN26241004"));
        // Connected, so this test still measures the rail switch rather than the company gate added
        // alongside it.
        org.mockito.Mockito.lenient()
                .when(credentialsResolver.canPayFromOwnWallet(org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any()))
                .thenReturn(true);

    }

    /** A fully enrolled seller — so any ineligibility can only come from the switch, never from setup. */
    private void sellerIsFullyEnrolled() {
        PartnerRedemption profile = PartnerRedemption.builder()
                .clientId(CLIENT_ID)
                .userId(SELLER_ID)
                .recipientUserId("PAT123456")
                .enrollmentStatus(XtrmEnrollmentStatus.ENROLLED)
                // Enrolled by this company, so these cases keep measuring what they were
                // written for rather than the issuer rule added alongside them.
                .enrolledIssuerAccountNumber("SPN26241004")
                .build();
        when(profileRepository.findByUserIdAndClientId(SELLER_ID, CLIENT_ID)).thenReturn(Optional.of(profile));
    }

    private DistributionRecipientService service(boolean xtrmRailsEnabled) {
        return new DistributionRecipientService(
                userRepository, profileRepository, linkedBankRepository, credentialsResolver, xtrmRailsEnabled);
    }

    private DistributionRecipientResponse only(DistributionRecipientService s, DistributionRail rail) {
        List<DistributionRecipientResponse> out = s.listRecipients(CLIENT_ID, COMPANY_ID, rail);
        assertThat(out).hasSize(1);
        return out.get(0);
    }

    // ────────────────────────────────────────────────────── switch OFF

    /**
     * The listing must keep telling the truth while the rail is off. An admin browsing Gift Card should see
     * that this seller IS set up — that is real information about their own team, and blanking every row to
     * "temporarily unavailable" would tell them nothing they cannot already read at the top of the page.
     */
    @Test
    void railsOff_listingStillReportsRealReadiness_forAnEnrolledSeller() {
        sellerIsFullyEnrolled();

        DistributionRecipientResponse r = only(service(false), DistributionRail.GIFT_CARD);

        assertThat(r.eligible()).isTrue();
        assertThat(r.destination()).isEqualTo("carol@example.com");
    }

    /** And an unprepared seller still gets the reason that is actually actionable for them. */
    @Test
    void railsOff_listingStillReportsTheSetupReason_forAnUnenrolledSeller() {
        when(profileRepository.findByUserIdAndClientId(SELLER_ID, CLIENT_ID)).thenReturn(Optional.empty());

        DistributionRecipientResponse r = only(service(false), DistributionRail.BANK_TRANSFER);

        assertThat(r.eligible()).isFalse();
        assertThat(r.ineligibleReason()).contains("No payout profile");
    }

    /**
     * The whole point of switching off rather than stubbing: the feature still works. WALLET_CREDIT calls no
     * vendor, so it must stay reachable for a seller who has set up nothing at all.
     */
    @Test
    void railsOff_walletCredit_isRetiredNotAFallback() {
        when(profileRepository.findByUserIdAndClientId(SELLER_ID, CLIENT_ID)).thenReturn(Optional.empty());

        // Wallet rail retired 2026-08-26 — it is no longer a fallback for anything.
        // With both remaining rails needing XTRM, "rails off" now means distribution is off entirely.
        DistributionRecipientResponse r = only(service(false), DistributionRail.WALLET_CREDIT);

        assertThat(r.eligible()).isFalse();
        assertThat(r.ineligibleReason()).containsIgnoringCase("no longer available");
    }

    /**
     * Sending is the thing that must be refused, and it is refused for a seller who is otherwise perfectly
     * eligible — so the block cannot be mistaken for a setup problem. The greyed-out button is UX; this is
     * the gate that actually holds against a stale or hand-rolled client.
     */
    @Test
    void railsOff_submitIsRefused_evenForAFullyEligibleSeller() {
        sellerIsFullyEnrolled();

        assertThatThrownBy(() -> service(false)
                .assertAllEligible(CLIENT_ID, COMPANY_ID, List.of(SELLER_ID), DistributionRail.GIFT_CARD))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining(DistributionRecipientService.RAIL_UNAVAILABLE);
    }

    @Test
    void railsOff_submitIsRefusedForBankTransferToo() {
        sellerIsFullyEnrolled();

        assertThatThrownBy(() -> service(false)
                .assertAllEligible(CLIENT_ID, COMPANY_ID, List.of(SELLER_ID), DistributionRail.BANK_TRANSFER))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining(DistributionRecipientService.RAIL_UNAVAILABLE);
    }

    @Test
    void railsOff_submitRefusesWalletCreditToo() {
        when(profileRepository.findByUserIdAndClientId(SELLER_ID, CLIENT_ID)).thenReturn(Optional.empty());

        // Wallet rail retired 2026-08-26 — it is no longer a fallback for anything.
        assertThatThrownBy(() -> service(false).assertAllEligible(
                CLIENT_ID, COMPANY_ID, List.of(SELLER_ID), DistributionRail.WALLET_CREDIT))
                .isInstanceOf(BusinessRuleException.class);
    }

    // ────────────────────────────────────────────────────── switch ON

    /**
     * Flipping the flag restores the rail with no code change — that is the promise made in the config
     * comment, and it is only true if the switch is the sole thing standing in the way.
     */
    @Test
    void railsOn_giftCard_isEligibleAgain_forAnEnrolledSeller() {
        sellerIsFullyEnrolled();

        DistributionRecipientResponse r = only(service(true), DistributionRail.GIFT_CARD);

        assertThat(r.eligible()).isTrue();
        assertThat(r.destination()).isEqualTo("carol@example.com");
    }

    /** With the switch on, submit goes through for an eligible seller — the block was the only thing stopping it. */
    @Test
    void railsOn_submitIsAllowed() {
        sellerIsFullyEnrolled();

        service(true).assertAllEligible(
                CLIENT_ID, COMPANY_ID, List.of(SELLER_ID), DistributionRail.GIFT_CARD);
    }

    /** With the switch on, the ordinary per-recipient reasons still apply — they were not deleted. */
    @Test
    void railsOn_giftCard_unenrolledSellerGetsTheProfileReason() {
        when(profileRepository.findByUserIdAndClientId(SELLER_ID, CLIENT_ID)).thenReturn(Optional.empty());
        when(linkedBankRepository.findByUserIdAndClientIdOrderByCreatedAtAsc(any(), any()))
                .thenReturn(List.of());

        DistributionRecipientResponse r = only(service(true), DistributionRail.GIFT_CARD);

        assertThat(r.eligible()).isFalse();
        assertThat(r.ineligibleReason()).contains("No payout profile");
    }
}
