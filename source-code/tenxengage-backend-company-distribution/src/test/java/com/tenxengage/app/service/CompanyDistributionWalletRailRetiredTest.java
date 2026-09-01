package com.tenxengage.app.service;

import com.tenxengage.app.entity.enums.DistributionRail;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The wallet rail is retired from distribution: no longer offered, no longer accepted.
 *
 * <p>The enum constant stays. Three WALLET_CREDIT distributions already exist in the dev database, and
 * {@code company_distributions.rail} stores the constant's name — deleting it would make that history
 * unreadable and break the settlement path for any item still in flight.</p>
 */
class CompanyDistributionWalletRailRetiredTest {

    @Test
    void theConstantStillExistsSoOldRowsRemainReadable() {
        // Reading a stored 'WALLET_CREDIT' must keep working. This is the guard against someone tidying
        // the enum and silently breaking distribution history.
        assertThat(DistributionRail.valueOf("WALLET_CREDIT")).isNotNull();
    }

    @Test
    void bothRemainingRailsGoThroughTheVendor() {
        // Once the wallet rail is not offered, every rail a caller can pick is an XTRM payout. Anything
        // relying on a non-vendor rail existing is now relying on history only.
        assertThat(DistributionRail.GIFT_CARD.isVendorPayout()).isTrue();
        assertThat(DistributionRail.BANK_TRANSFER.isVendorPayout()).isTrue();
    }

    @Test
    void theWalletRailIsStillMarkedNonVendorForOldRows() {
        assertThat(DistributionRail.WALLET_CREDIT.isVendorPayout()).isFalse();
    }
}
