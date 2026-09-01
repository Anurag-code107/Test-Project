package com.tenxengage.app.testdata;

import com.tenxengage.app.entity.RedemptionCatalogItem;
import com.tenxengage.app.entity.enums.RedemptionCategory;
import com.tenxengage.app.entity.enums.RedemptionProcessingMode;

import java.math.BigDecimal;

public final class RedemptionCatalogItemFixtures {

    private RedemptionCatalogItemFixtures() {
    }

    public static RedemptionCatalogItem.RedemptionCatalogItemBuilder activeCashItem() {
        return RedemptionCatalogItem.builder()
                .name("Bank Transfer")
                .description("Direct bank transfer redemption")
                .category(RedemptionCategory.CASH)
                .currencyId("USD")
                .defaultMinRedemptionAmount(new BigDecimal("10.00"))
                .defaultProcessingMode(RedemptionProcessingMode.BATCH)
                .geographicScope(new String[]{"US", "CA"})
                .isReturnable(false)
                .defaultReturnWindowDays(0)
                .isActive(true);
    }

    public static RedemptionCatalogItem.RedemptionCatalogItemBuilder activeNonCashItem() {
        return RedemptionCatalogItem.builder()
                .name("Amazon Gift Card")
                .description("Amazon e-gift card")
                .category(RedemptionCategory.NON_CASH)
                .currencyId("USD")
                .defaultMinRedemptionAmount(new BigDecimal("5.00"))
                .defaultProcessingMode(RedemptionProcessingMode.INSTANT)
                .geographicScope(new String[]{"US"})
                .providerItemId("AMZN-001")
                .isReturnable(true)
                .defaultReturnWindowDays(30)
                .isActive(true);
    }

    public static RedemptionCatalogItem.RedemptionCatalogItemBuilder inactiveCashItem() {
        return activeCashItem()
                .name("Deprecated Transfer")
                .isActive(false);
    }

    public static RedemptionCatalogItem.RedemptionCatalogItemBuilder inactiveNonCashItem() {
        return activeNonCashItem()
                .name("Discontinued Gift Card")
                .isActive(false);
    }

    public static RedemptionCatalogItem.RedemptionCatalogItemBuilder activeNonCashItemWithXoxoday() {
        return activeNonCashItem()
                .providerItemId("XOX-" + System.nanoTime())
                .syncMetadata("{\"source\":\"xoxoday\",\"version\":1}");
    }

    public static RedemptionCatalogItem.RedemptionCatalogItemBuilder globalScopeItem() {
        return activeNonCashItem()
                .name("Global Gift Card")
                .geographicScope(new String[0]);
    }
}
