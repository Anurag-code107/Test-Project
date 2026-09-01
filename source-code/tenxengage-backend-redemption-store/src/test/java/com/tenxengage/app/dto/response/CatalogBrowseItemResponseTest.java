package com.tenxengage.app.dto.response;

import com.tenxengage.app.entity.ClientCatalogItemConfig;
import com.tenxengage.app.entity.RedemptionCatalogItem;
import com.tenxengage.app.entity.enums.BatchCadence;
import com.tenxengage.app.testdata.ClientCatalogItemConfigFixtures;
import com.tenxengage.app.testdata.RedemptionCatalogItemFixtures;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Effective-value resolution surfaced to partner browse. Both transaction-amount bounds resolve the
 * same way: COALESCE(client override, item default). The min/max symmetry is the point of these tests.
 */
class CatalogBrowseItemResponseTest {

    private static final UUID CLIENT_ID = UUID.randomUUID();
    private static final UUID ITEM_ID = UUID.randomUUID();

    private static RedemptionCatalogItem variableItem(String min, String max) {
        RedemptionCatalogItem item = RedemptionCatalogItemFixtures.activeNonCashItem()
                .defaultMinRedemptionAmount(new BigDecimal(min))
                .defaultMaxRedemptionAmount(max == null ? null : new BigDecimal(max))
                .build();
        item.setId(ITEM_ID);
        return item;
    }

    private static ClientCatalogItemConfig config(String minOverride, String maxOverride) {
        return ClientCatalogItemConfigFixtures.enabledConfig(CLIENT_ID, ITEM_ID)
                .minTransactionAmountOverride(minOverride == null ? null : new BigDecimal(minOverride))
                .maxTransactionAmountOverride(maxOverride == null ? null : new BigDecimal(maxOverride))
                .build();
    }

    private static CatalogBrowseItemResponse resolve(RedemptionCatalogItem item, ClientCatalogItemConfig config) {
        return CatalogBrowseItemResponse.from(item, config, new BigDecimal("10000.00"), BatchCadence.DAILY);
    }

    @Test
    void inheritsBothItemDefaults_whenNoConfigRowExists() {
        var result = resolve(variableItem("20.00", "2000.00"), null);

        assertThat(result.effectiveMinTransactionAmount()).isEqualByComparingTo("20.00");
        assertThat(result.effectiveMaxTransactionAmount()).isEqualByComparingTo("2000.00");
    }

    @Test
    void inheritsBothItemDefaults_whenConfigLeavesBoundsUnset() {
        var result = resolve(variableItem("20.00", "2000.00"), config(null, null));

        assertThat(result.effectiveMinTransactionAmount()).isEqualByComparingTo("20.00");
        assertThat(result.effectiveMaxTransactionAmount()).isEqualByComparingTo("2000.00");
    }

    @Test
    void appliesClientMaxOverride_narrowingTheCeiling() {
        var result = resolve(variableItem("20.00", "2000.00"), config(null, "500.00"));

        assertThat(result.effectiveMinTransactionAmount()).isEqualByComparingTo("20.00");
        assertThat(result.effectiveMaxTransactionAmount()).isEqualByComparingTo("500.00");
    }

    @Test
    void appliesBothOverrides_narrowingTheRangeFromEachEnd() {
        var result = resolve(variableItem("20.00", "2000.00"), config("100.00", "500.00"));

        assertThat(result.effectiveMinTransactionAmount()).isEqualByComparingTo("100.00");
        assertThat(result.effectiveMaxTransactionAmount()).isEqualByComparingTo("500.00");
    }

    @Test
    void reportsNoCeiling_whenNeitherItemNorConfigSetsOne() {
        var result = resolve(variableItem("20.00", null), config("50.00", null));

        assertThat(result.effectiveMinTransactionAmount()).isEqualByComparingTo("50.00");
        assertThat(result.effectiveMaxTransactionAmount()).isNull();
    }

    @Test
    void appliesClientCeiling_toAnOtherwiseOpenValueItem() {
        var result = resolve(variableItem("20.00", null), config(null, "300.00"));

        assertThat(result.effectiveMaxTransactionAmount()).isEqualByComparingTo("300.00");
    }
}
