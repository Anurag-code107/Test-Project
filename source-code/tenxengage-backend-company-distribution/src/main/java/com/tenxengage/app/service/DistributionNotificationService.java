package com.tenxengage.app.service;

import com.tenxengage.app.entity.CompanyDistribution;
import com.tenxengage.app.entity.CompanyDistributionItem;
import com.tenxengage.app.entity.enums.DistributionRail;
import com.tenxengage.app.event.NotificationEvent;
import com.tenxengage.app.event.NotificationEventProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Telling people what happened to a distribution.
 *
 * <p><b>Notifications fire on item settle, never on submit.</b> At submit the money is only reserved — it
 * has not reached anyone and an individual recipient can still fail. Announcing it then would tell a seller
 * that funds had arrived while their balance still showed nothing.</p>
 *
 * <p>How much this matters varies by rail, which is why the copy differs:</p>
 * <ul>
 *   <li>{@code GIFT_CARD} — XTRM emails the card itself, so ours is the heads-up that it is coming.</li>
 *   <li>{@code BANK_TRANSFER} — the only signal the seller gets before money appears in their bank.</li>
 *   <li>{@code WALLET_CREDIT} — the <b>only</b> signal at all. Nothing external happens: no vendor email,
 *       no bank line. Without this the balance would simply change one day.</li>
 * </ul>
 *
 * <p>Uses {@link NotificationEventProducer}, the path that actually persists and dispatches — not
 * {@code NotificationService.sendRedemption*}, which are log-only stubs that were never wired up.</p>
 */
@Service
public class DistributionNotificationService {

    private static final Logger log = LoggerFactory.getLogger(DistributionNotificationService.class);

    static final String AWARD_RECEIVED = "COMPANY_AWARD_RECEIVED";
    static final String DISTRIBUTION_SUMMARY = "COMPANY_DISTRIBUTION_SUMMARY";

    private final NotificationEventProducer notificationEventProducer;

    public DistributionNotificationService(NotificationEventProducer notificationEventProducer) {
        this.notificationEventProducer = notificationEventProducer;
    }

    /**
     * Tell one recipient their share landed. Never throws — a notification failure must not roll back or
     * obscure a payment that already succeeded.
     */
    public void notifyAwardSettled(CompanyDistribution header, CompanyDistributionItem item, String rewardName) {
        try {
            notificationEventProducer.publish(new NotificationEvent(
                    AWARD_RECEIVED,
                    header.getClientId(),
                    title(header.getRail(), rewardName),
                    body(header, item, rewardName),
                    "COMPANY_DISTRIBUTION_ITEM",
                    item.getId(),
                    header.getInitiatedByUserId(),          // the admin is the actor, not the recipient
                    List.of(item.getRecipientUserId()),     // and the recipient is the only audience
                    Map.of(
                            "rail", header.getRail().name(),
                            "amount", item.getAmount().toPlainString(),
                            "currencyId", header.getCurrencyId(),
                            "distributionId", header.getId().toString())));
        } catch (Exception e) {
            log.error("[step=award_notification_failed] itemId={} — payment already succeeded, not retried",
                    item.getId(), e);
        }
    }

    /**
     * Tell the admin a distribution finished. Worth sending even on full success, but the case it exists for
     * is a partial failure: money went back to the company wallet and the admin needs to know without
     * opening the history screen.
     */
    public void notifyDistributionFinished(CompanyDistribution header, String rollupStatus,
                                            int completed, int failed, BigDecimal settledTotal) {
        try {
            boolean partial = failed > 0 && completed > 0;
            String title = partial
                    ? "Distribution partly completed"
                    : failed > 0 ? "Distribution failed" : "Distribution complete";

            StringBuilder message = new StringBuilder();
            message.append(completed).append(" of ").append(header.getRecipientCount())
                    .append(" recipient").append(header.getRecipientCount() == 1 ? "" : "s")
                    .append(" paid (").append(settledTotal.toPlainString()).append(' ')
                    .append(header.getCurrencyId()).append(").");
            if (failed > 0) {
                // Name the consequence, not just the count — the admin's next question is where the money went.
                message.append(' ').append(failed).append(failed == 1 ? " recipient" : " recipients")
                        .append(" could not be paid; their share was returned to the company wallet.");
            }

            notificationEventProducer.publish(new NotificationEvent(
                    DISTRIBUTION_SUMMARY,
                    header.getClientId(),
                    title,
                    message.toString(),
                    "COMPANY_DISTRIBUTION",
                    header.getId(),
                    null,
                    List.of(header.getInitiatedByUserId()),
                    Map.of(
                            "rail", header.getRail().name(),
                            "status", rollupStatus,
                            "completed", String.valueOf(completed),
                            "failed", String.valueOf(failed),
                            "settledTotal", settledTotal.toPlainString())));
        } catch (Exception e) {
            log.error("[step=distribution_summary_notification_failed] distributionId={}", header.getId(), e);
        }
    }

    private String title(DistributionRail rail, String rewardName) {
        return switch (rail) {
            case GIFT_CARD -> "You received a gift card";
            case BANK_TRANSFER -> "A reward is on its way to your bank";
            case WALLET_CREDIT -> "Reward added to your wallet";
        };
    }

    private String body(CompanyDistribution header, CompanyDistributionItem item, String rewardName) {
        String amount = item.getAmount().toPlainString() + " " + header.getCurrencyId();
        String from = header.getNote() == null || header.getNote().isBlank()
                ? ""
                : " — \"" + header.getNote().trim() + "\"";

        return switch (header.getRail()) {
            // XTRM sends the card itself, so point the seller at their inbox rather than implying it is here.
            case GIFT_CARD -> "Your company sent you a " + amount + " "
                    + (rewardName == null ? "gift card" : rewardName)
                    + ". Check your email for the card details." + from;
            // Banks settle on their own schedule, so do not promise a time we do not control.
            case BANK_TRANSFER -> "Your company sent " + amount
                    + " to your linked bank account. It should appear shortly." + from;
            // The money is genuinely spendable right now, so say so.
            case WALLET_CREDIT -> "Your company added " + amount
                    + " to your reward wallet. It is available to redeem now." + from;
        };
    }
}
