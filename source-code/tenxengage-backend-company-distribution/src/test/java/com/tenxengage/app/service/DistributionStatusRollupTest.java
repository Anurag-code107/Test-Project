package com.tenxengage.app.service;

import com.tenxengage.app.entity.CompanyDistributionItem;
import com.tenxengage.app.entity.RedemptionRequest;
import com.tenxengage.app.entity.enums.DistributionItemStatus;
import com.tenxengage.app.entity.enums.RedemptionStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DistributionStatusRollupTest {

    /**
     * Guards a static-initialiser trap: RedemptionStatus.RESERVED and DistributionItemStatus.RESERVED are
     * both the string "RESERVED", so building the in-flight set with Set.of threw duplicate-element from the
     * initialiser — surfacing only as NoClassDefFoundError at the first call site. Touching the class at all
     * is the test.
     */
    @Test
    void classInitialises_despiteTwoEnumsSharingReserved() {
        assertThat(DistributionStatusRollup.isInFlight("RESERVED")).isTrue();
        assertThat(DistributionStatusRollup.isInFlight("PROCESSING")).isTrue();
        assertThat(DistributionStatusRollup.isInFlight("PENDING_APPROVAL")).isTrue();
        assertThat(DistributionStatusRollup.isInFlight("COMPLETED")).isFalse();
        assertThat(DistributionStatusRollup.isInFlight("FAILED")).isFalse();
    }

    private CompanyDistributionItem walletItem(DistributionItemStatus status) {
        return CompanyDistributionItem.builder()
                .clientId(UUID.randomUUID()).distributionId(UUID.randomUUID())
                .recipientUserId(UUID.randomUUID()).amount(BigDecimal.ONE).status(status).build();
    }

    private CompanyDistributionItem payoutItem(UUID legId) {
        return CompanyDistributionItem.builder()
                .clientId(UUID.randomUUID()).distributionId(UUID.randomUUID())
                .recipientUserId(UUID.randomUUID()).amount(BigDecimal.ONE)
                .redemptionRequestId(legId).build();
    }

    @Test
    void walletItem_reportsItsOwnStatus() {
        assertThat(DistributionStatusRollup.itemStatus(walletItem(DistributionItemStatus.COMPLETED), Map.of()))
                .isEqualTo("COMPLETED");
    }

    @Test
    void payoutItem_defersToItsLeg() {
        UUID legId = UUID.randomUUID();
        RedemptionRequest leg = RedemptionRequest.builder().status(RedemptionStatus.FAILED).build();
        leg.setId(legId);

        assertThat(DistributionStatusRollup.itemStatus(payoutItem(legId), Map.of(legId, leg)))
                .isEqualTo("FAILED");
    }

    /** An unreadable leg is an UNKNOWN outcome, not a failed one — calling it failed invites a wrong release. */
    @Test
    void payoutItem_withMissingLeg_readsAsProcessingNotFailed() {
        assertThat(DistributionStatusRollup.itemStatus(payoutItem(UUID.randomUUID()), Map.of()))
                .isEqualTo("PROCESSING");
    }

    @Test
    void rollup_allCompleted_isCompleted() {
        assertThat(DistributionStatusRollup.rollup(List.of("COMPLETED", "COMPLETED"))).isEqualTo("COMPLETED");
    }

    @Test
    void rollup_anyInFlight_isProcessing() {
        assertThat(DistributionStatusRollup.rollup(List.of("COMPLETED", "RESERVED"))).isEqualTo("PROCESSING");
        assertThat(DistributionStatusRollup.rollup(List.of("FAILED", "PROCESSING"))).isEqualTo("PROCESSING");
    }

    /** The case the admin summary exists for: some paid, some money returned. */
    @Test
    void rollup_mixedTerminal_isPartiallyCompleted() {
        assertThat(DistributionStatusRollup.rollup(List.of("COMPLETED", "FAILED")))
                .isEqualTo("PARTIALLY_COMPLETED");
        assertThat(DistributionStatusRollup.rollup(List.of("COMPLETED", "CANCELLED")))
                .isEqualTo("PARTIALLY_COMPLETED");
    }

    @Test
    void rollup_allFailed_isFailed() {
        assertThat(DistributionStatusRollup.rollup(List.of("FAILED", "FAILED"))).isEqualTo("FAILED");
    }

    /** No items yet means nothing has settled, not that everything succeeded. */
    @Test
    void rollup_empty_isProcessing() {
        assertThat(DistributionStatusRollup.rollup(List.of())).isEqualTo("PROCESSING");
    }

    @Test
    void isTerminal_onlyProcessingIsNot() {
        assertThat(DistributionStatusRollup.isTerminal("PROCESSING")).isFalse();
        assertThat(DistributionStatusRollup.isTerminal("COMPLETED")).isTrue();
        assertThat(DistributionStatusRollup.isTerminal("PARTIALLY_COMPLETED")).isTrue();
        assertThat(DistributionStatusRollup.isTerminal("FAILED")).isTrue();
    }
}
