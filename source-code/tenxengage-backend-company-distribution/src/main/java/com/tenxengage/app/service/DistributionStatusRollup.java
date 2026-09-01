package com.tenxengage.app.service;

import com.tenxengage.app.entity.CompanyDistributionItem;
import com.tenxengage.app.entity.RedemptionRequest;
import com.tenxengage.app.entity.enums.DistributionItemStatus;
import com.tenxengage.app.entity.enums.RedemptionStatus;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The one definition of a distribution's status.
 *
 * <p>Extracted deliberately: the read model and the admin summary notification both need it, and if they
 * computed it separately the notification could say "complete" while the screen still says "processing".
 * One rule, two callers.</p>
 */
public final class DistributionStatusRollup {

    public static final String COMPLETED = "COMPLETED";
    public static final String PROCESSING = "PROCESSING";
    public static final String FAILED = "FAILED";
    public static final String PARTIALLY_COMPLETED = "PARTIALLY_COMPLETED";

    /**
     * Statuses meaning "this recipient can still change state".
     *
     * <p>Built with {@link Set#copyOf} over a list, not {@code Set.of}: both {@link RedemptionStatus} and
     * {@link DistributionItemStatus} define {@code RESERVED}, so the two {@code .name()} values collide and
     * {@code Set.of} throws {@code IllegalArgumentException: duplicate element} — from a static initialiser,
     * which fails the whole class with {@code NoClassDefFoundError} rather than anything legible.</p>
     */
    private static final Set<String> IN_FLIGHT = Set.copyOf(List.of(
            RedemptionStatus.PENDING_APPROVAL.name(),
            RedemptionStatus.RESERVED.name(),          // same string as DistributionItemStatus.RESERVED
            RedemptionStatus.PROCESSING.name()));

    private DistributionStatusRollup() {
    }

    /**
     * One item's status. A payout item defers to its redemption leg; a wallet-transfer item carries its own.
     * Never both — {@code chk_distribution_item_leg} guarantees exactly one owner.
     *
     * <p>A payout item whose leg cannot be found reads as {@code PROCESSING} rather than failed: an
     * unreadable leg is an unknown outcome, and calling it failed would invite releasing money that may have
     * been paid.</p>
     */
    public static String itemStatus(CompanyDistributionItem item, Map<UUID, RedemptionRequest> legs) {
        UUID legId = item.getRedemptionRequestId();
        if (legId != null) {
            RedemptionRequest leg = legs == null ? null : legs.get(legId);
            return leg == null ? PROCESSING : leg.getStatus().name();
        }
        DistributionItemStatus own = item.getStatus();
        return own == null ? PROCESSING : own.name();
    }

    /** Anything unfinished means the whole distribution is still in progress. */
    public static String rollup(List<String> itemStatuses) {
        if (itemStatuses.isEmpty()) {
            return PROCESSING;
        }
        if (itemStatuses.stream().anyMatch(DistributionStatusRollup::isInFlight)) {
            return PROCESSING;
        }
        boolean anyCompleted = itemStatuses.contains(COMPLETED);
        boolean anyFailed = itemStatuses.stream()
                .anyMatch(s -> FAILED.equals(s) || RedemptionStatus.CANCELLED.name().equals(s));
        if (anyCompleted && anyFailed) {
            return PARTIALLY_COMPLETED;
        }
        return anyCompleted ? COMPLETED : FAILED;
    }

    public static boolean isInFlight(String itemStatus) {
        return IN_FLIGHT.contains(itemStatus);
    }

    /** True once no recipient can still change state — the point at which the admin summary is truthful. */
    public static boolean isTerminal(String rollupStatus) {
        return !PROCESSING.equals(rollupStatus);
    }
}
