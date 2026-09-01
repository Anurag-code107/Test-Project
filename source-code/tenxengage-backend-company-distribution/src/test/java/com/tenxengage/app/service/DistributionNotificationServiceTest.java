package com.tenxengage.app.service;

import com.tenxengage.app.entity.CompanyDistribution;
import com.tenxengage.app.entity.CompanyDistributionItem;
import com.tenxengage.app.entity.enums.DistributionRail;
import com.tenxengage.app.event.NotificationEvent;
import com.tenxengage.app.event.NotificationEventProducer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DistributionNotificationServiceTest {

    @Mock private NotificationEventProducer producer;
    @InjectMocks private DistributionNotificationService service;

    private static final UUID CLIENT_ID = UUID.randomUUID();
    private static final UUID ADMIN_ID = UUID.randomUUID();
    private static final UUID RECIPIENT_ID = UUID.randomUUID();

    private CompanyDistribution header(DistributionRail rail, String note) {
        CompanyDistribution d = CompanyDistribution.builder()
                .clientId(CLIENT_ID).partnerCompanyId(UUID.randomUUID()).sourceWalletId(UUID.randomUUID())
                .rail(rail).currencyId("cash").initiatedByUserId(ADMIN_ID)
                .recipientCount(3).totalAmount(new BigDecimal("150.00")).note(note).build();
        d.setId(UUID.randomUUID());
        return d;
    }

    private CompanyDistributionItem item(String amount) {
        CompanyDistributionItem i = CompanyDistributionItem.builder()
                .clientId(CLIENT_ID).distributionId(UUID.randomUUID()).recipientUserId(RECIPIENT_ID)
                .amount(new BigDecimal(amount)).build();
        i.setId(UUID.randomUUID());
        return i;
    }

    private NotificationEvent captureAward(DistributionRail rail, String note) {
        service.notifyAwardSettled(header(rail, note), item("50.00"), "Amazon Gift Card");
        ArgumentCaptor<NotificationEvent> c = ArgumentCaptor.forClass(NotificationEvent.class);
        verify(producer).publish(c.capture());
        return c.getValue();
    }

    // ---------------------------------------------------------------- audience

    /** The recipient is the audience; the admin is the actor. Getting this backwards notifies the wrong person. */
    @Test
    void award_targetsOnlyTheRecipient_withTheAdminAsActor() {
        NotificationEvent e = captureAward(DistributionRail.WALLET_CREDIT, null);

        assertThat(e.targetUserIds()).containsExactly(RECIPIENT_ID);
        assertThat(e.actorUserId()).isEqualTo(ADMIN_ID);
        assertThat(e.notificationTypeKey()).isEqualTo("COMPANY_AWARD_RECEIVED");
    }

    // ---------------------------------------------------------------- copy per rail

    /** XTRM emails the card itself, so the seller must be pointed at their inbox, not told it is here. */
    @Test
    void award_giftCard_pointsAtTheEmail() {
        NotificationEvent e = captureAward(DistributionRail.GIFT_CARD, null);

        assertThat(e.message()).contains("Amazon Gift Card").contains("email");
        assertThat(e.message()).doesNotContain("available to redeem now");
    }

    /** Banks settle on their own schedule, so the copy must not promise a time we do not control. */
    @Test
    void award_bankTransfer_doesNotPromiseATime() {
        NotificationEvent e = captureAward(DistributionRail.BANK_TRANSFER, null);

        assertThat(e.message()).contains("bank account");
        assertThat(e.message().toLowerCase()).doesNotContain("instant").doesNotContain("immediately");
    }

    /** The wallet rail is the only one where the money is genuinely spendable at once, so it says so. */
    @Test
    void award_walletCredit_saysItIsSpendableNow() {
        NotificationEvent e = captureAward(DistributionRail.WALLET_CREDIT, null);

        assertThat(e.message()).contains("reward wallet").contains("available to redeem now");
    }

    /** The admin's message is the human part of the reward, so it must survive into what the seller reads. */
    @Test
    void award_includesTheAdminsNote() {
        NotificationEvent e = captureAward(DistributionRail.WALLET_CREDIT, "Great quarter");

        assertThat(e.message()).contains("Great quarter");
    }

    @Test
    void award_carriesAmountAndRailAsMetadata() {
        NotificationEvent e = captureAward(DistributionRail.WALLET_CREDIT, null);

        assertThat(e.metadata()).containsEntry("amount", "50.00")
                .containsEntry("rail", "WALLET_CREDIT")
                .containsEntry("currencyId", "cash");
    }

    // ---------------------------------------------------------------- admin summary

    /**
     * The case this notification exists for: money went back to the company wallet, and the admin should not
     * have to open the history screen to find out.
     */
    @Test
    void summary_partialFailure_namesTheReturnedShare() {
        service.notifyDistributionFinished(
                header(DistributionRail.BANK_TRANSFER, null), "PARTIALLY_COMPLETED",
                2, 1, new BigDecimal("100.00"));

        ArgumentCaptor<NotificationEvent> c = ArgumentCaptor.forClass(NotificationEvent.class);
        verify(producer).publish(c.capture());
        NotificationEvent e = c.getValue();

        assertThat(e.title()).isEqualTo("Distribution partly completed");
        assertThat(e.message()).contains("2 of 3").contains("100.00")
                .contains("returned to the company wallet");
        assertThat(e.targetUserIds()).containsExactly(ADMIN_ID);
        assertThat(e.notificationTypeKey()).isEqualTo("COMPANY_DISTRIBUTION_SUMMARY");
    }

    @Test
    void summary_fullSuccess_doesNotMentionFailures() {
        service.notifyDistributionFinished(
                header(DistributionRail.WALLET_CREDIT, null), "COMPLETED",
                3, 0, new BigDecimal("150.00"));

        ArgumentCaptor<NotificationEvent> c = ArgumentCaptor.forClass(NotificationEvent.class);
        verify(producer).publish(c.capture());

        assertThat(c.getValue().title()).isEqualTo("Distribution complete");
        assertThat(c.getValue().message()).doesNotContain("returned to the company wallet");
    }

    // ---------------------------------------------------------------- failure isolation

    /**
     * The payment already happened by the time we notify. A broken notification path must never propagate —
     * it would turn a successful payout into an apparent failure and could trigger a release.
     */
    @Test
    void notificationFailure_isSwallowed_becauseTheMoneyAlreadyMoved() {
        doThrow(new RuntimeException("kafka down")).when(producer).publish(any());

        assertThatCode(() -> service.notifyAwardSettled(
                header(DistributionRail.WALLET_CREDIT, null), item("50.00"), "x"))
                .doesNotThrowAnyException();

        assertThatCode(() -> service.notifyDistributionFinished(
                header(DistributionRail.WALLET_CREDIT, null), "COMPLETED", 1, 0, BigDecimal.ONE))
                .doesNotThrowAnyException();
    }
}
