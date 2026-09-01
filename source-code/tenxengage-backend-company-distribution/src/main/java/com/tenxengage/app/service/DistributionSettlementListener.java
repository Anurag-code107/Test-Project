package com.tenxengage.app.service;

import com.tenxengage.app.entity.CompanyDistribution;
import com.tenxengage.app.entity.CompanyDistributionItem;
import com.tenxengage.app.entity.RedemptionRequest;
import com.tenxengage.app.entity.enums.DistributionRail;
import com.tenxengage.app.entity.enums.RedemptionOrigin;
import com.tenxengage.app.event.RedemptionCompletedEvent;
import com.tenxengage.app.event.RedemptionFailedEvent;
import com.tenxengage.app.repository.CompanyDistributionItemRepository;
import com.tenxengage.app.repository.CompanyDistributionRepository;
import com.tenxengage.app.repository.RedemptionCatalogItemRepository;
import com.tenxengage.app.repository.RedemptionRequestRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Notifies people when a distribution's payout legs settle.
 *
 * <p><b>Additive by design.</b> The gift-card and bank rails complete inside {@code settle()} — shared money
 * code also used by personal redemptions — so rather than editing it, this listens to the
 * {@link RedemptionCompletedEvent} / {@link RedemptionFailedEvent} it already publishes and ignores anything
 * that is not a distribution leg. No change to a settlement path that live seller redemptions depend on.</p>
 *
 * <p>The {@code WALLET_CREDIT} rail has no redemption row and therefore no such event; the dispatcher notifies
 * that rail directly after its settle succeeds.</p>
 */
@Service
public class DistributionSettlementListener {

    private static final Logger log = LoggerFactory.getLogger(DistributionSettlementListener.class);

    private final CompanyDistributionItemRepository itemRepository;
    private final CompanyDistributionRepository distributionRepository;
    private final RedemptionRequestRepository redemptionRequestRepository;
    private final RedemptionCatalogItemRepository catalogItemRepository;
    private final DistributionNotificationService notifications;

    public DistributionSettlementListener(CompanyDistributionItemRepository itemRepository,
                                           CompanyDistributionRepository distributionRepository,
                                           RedemptionRequestRepository redemptionRequestRepository,
                                           RedemptionCatalogItemRepository catalogItemRepository,
                                           DistributionNotificationService notifications) {
        this.itemRepository = itemRepository;
        this.distributionRepository = distributionRepository;
        this.redemptionRequestRepository = redemptionRequestRepository;
        this.catalogItemRepository = catalogItemRepository;
        this.notifications = notifications;
    }

    /** A payout leg completed — tell the recipient, then check whether the distribution is now finished. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void onRedemptionCompleted(RedemptionCompletedEvent event) {
        handle(event.getRequest(), true);
    }

    /**
     * A payout leg failed. The recipient is not told — they were never promised anything, and announcing a
     * failed reward they did not ask for is noise. The admin's summary reports it instead.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void onRedemptionFailed(RedemptionFailedEvent event) {
        handle(event.getRequest(), false);
    }

    private void handle(RedemptionRequest request, boolean completed) {
        if (request == null || request.getOrigin() != RedemptionOrigin.COMPANY_DISTRIBUTION) {
            return; // a personal redemption — not ours
        }
        try {
            Optional<CompanyDistributionItem> maybeItem =
                    itemRepository.findByRedemptionRequestId(request.getId());
            if (maybeItem.isEmpty()) {
                log.warn("[step=distribution_settle_no_item] redemptionId={}", request.getId());
                return;
            }
            CompanyDistributionItem item = maybeItem.get();
            CompanyDistribution header = distributionRepository.findById(item.getDistributionId()).orElse(null);
            if (header == null) {
                return;
            }

            if (completed) {
                notifications.notifyAwardSettled(header, item, rewardName(header));
            }
            maybeNotifyFinished(header);
        } catch (Exception e) {
            // Never let a notification failure surface as a settlement failure — the money already moved.
            log.error("[step=distribution_settle_notify_failed] redemptionId={}", request.getId(), e);
        }
    }

    /**
     * Sends the admin summary once no recipient can still change state.
     *
     * <p>Evaluated after each item settles rather than tracked with a flag column. Two items settling at the
     * same instant could both observe a terminal rollup and each send a summary, so a duplicate is possible.
     * That is an accepted trade: a duplicate admin notification is harmless, whereas a missing one means a
     * partial failure goes unnoticed — and the alternative (a {@code summary_notified_at} column) adds a
     * write to the settle path purely for cosmetics.</p>
     */
    public void maybeNotifyFinished(CompanyDistribution header) {
        List<CompanyDistributionItem> items =
                itemRepository.findByDistributionIdOrderByCreatedAtAsc(header.getId());
        if (items.isEmpty()) {
            return;
        }
        Map<UUID, RedemptionRequest> legs = legsFor(items);
        List<String> statuses = items.stream()
                .map(i -> DistributionStatusRollup.itemStatus(i, legs))
                .toList();

        String rollup = DistributionStatusRollup.rollup(statuses);
        if (!DistributionStatusRollup.isTerminal(rollup)) {
            return; // still paying recipients — a summary now would be wrong
        }

        int completed = (int) statuses.stream().filter(DistributionStatusRollup.COMPLETED::equals).count();
        int failed = statuses.size() - completed;
        BigDecimal settled = BigDecimal.ZERO;
        for (int i = 0; i < items.size(); i++) {
            if (DistributionStatusRollup.COMPLETED.equals(statuses.get(i))) {
                settled = settled.add(items.get(i).getAmount());
            }
        }

        notifications.notifyDistributionFinished(header, rollup, completed, failed, settled);
    }

    private String rewardName(CompanyDistribution header) {
        if (header.getRail() != DistributionRail.GIFT_CARD || header.getCatalogItemId() == null) {
            return header.getRail().getDisplayName();
        }
        return catalogItemRepository.findById(header.getCatalogItemId())
                .map(c -> c.getName())
                .orElse("gift card");
    }

    private Map<UUID, RedemptionRequest> legsFor(List<CompanyDistributionItem> items) {
        List<UUID> ids = items.stream()
                .map(CompanyDistributionItem::getRedemptionRequestId)
                .filter(java.util.Objects::nonNull)
                .distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return redemptionRequestRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(RedemptionRequest::getId, Function.identity(), (a, b) -> a));
    }
}
