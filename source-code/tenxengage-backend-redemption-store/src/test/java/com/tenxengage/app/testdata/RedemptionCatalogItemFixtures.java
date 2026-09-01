package com.tenxengage.app.testdata;

import com.tenxengage.app.entity.RedemptionCatalogItem;
import com.tenxengage.app.entity.enums.RedemptionCategory;
import com.tenxengage.app.entity.enums.RedemptionProcessingMode;

import java.math.BigDecimal;
import java.util.UUID;

public final class RedemptionCatalogItemFixtures {

    /** Default owning client (Model 2) — matches the demo/seed client used across tests. */
    public static final UUID DEFAULT_OWNER_CLIENT_ID =
            UUID.fromString("a0000000-0000-0000-0000-000000000001");

    private RedemptionCatalogItemFixtures() {
    }

    public static RedemptionCatalogItem.RedemptionCatalogItemBuilder activeCashItem() {
        return RedemptionCatalogItem.builder()
                .ownerClientId(DEFAULT_OWNER_CLIENT_ID)
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
                .ownerClientId(DEFAULT_OWNER_CLIENT_ID)
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
