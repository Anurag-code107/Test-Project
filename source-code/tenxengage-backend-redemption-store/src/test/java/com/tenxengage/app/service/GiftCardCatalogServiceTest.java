package com.tenxengage.app.service;

import com.tenxengage.app.dto.response.GiftCardSkuResponse;
import com.tenxengage.app.exception.ExternalServiceException;
import com.tenxengage.app.service.xtrm.XtrmApiClient;
import com.tenxengage.app.service.xtrm.XtrmApiClient.GetDigitalGiftCardsCommand;
import com.tenxengage.app.service.xtrm.XtrmApiClient.GetDigitalGiftCardsResult;
import com.tenxengage.app.service.xtrm.XtrmApiClient.GiftCardCatalogItem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the XTRM gift-card catalog projection: keep only Active USD gift cards, de-dupe by
 * SKU (first brand wins), normalize the value type, and surface an XTRM failure as a 503-style error.
 */
@ExtendWith(MockitoExtension.class)
class GiftCardCatalogServiceTest {

    @Mock private XtrmApiClient xtrmApiClient;
    @InjectMocks private GiftCardCatalogService service;

    private static GiftCardCatalogItem item(String sku, String rewardType, String status, String currency,
                                            String valueType, BigDecimal face, BigDecimal min, BigDecimal max,
                                            String brand) {
        return new GiftCardCatalogItem(sku, sku + " Reward", brand, null, currency,
                valueType, rewardType, status, face, min, max);
    }

    @Test
    void listGiftCardSkus_keepsActiveUsdGiftCards_dedupesBySku_normalizesValueType() {
        List<GiftCardCatalogItem> raw = List.of(
                item("U-FIX", "gift card", "Active", "USD", "FIXED_VALUE",
                        new BigDecimal("10"), BigDecimal.ZERO, BigDecimal.ZERO, "Acme"),
                // same SKU under a second brand — must be de-duped (first brand wins)
                item("U-FIX", "gift card", "Active", "USD", "FIXED_VALUE",
                        new BigDecimal("10"), BigDecimal.ZERO, BigDecimal.ZERO, "Globex"),
                item("U-VAR", "gift card", "Active", "USD", "VARIABLE_VALUE",
                        BigDecimal.ZERO, new BigDecimal("5"), new BigDecimal("500"), "Acme"),
                // filtered out: donation, inactive, non-USD, blank SKU
                item("U-DON", "donation", "Active", "USD", "FIXED_VALUE",
                        new BigDecimal("25"), BigDecimal.ZERO, BigDecimal.ZERO, "RedCross"),
                item("U-OFF", "gift card", "Inactive", "USD", "FIXED_VALUE",
                        new BigDecimal("10"), BigDecimal.ZERO, BigDecimal.ZERO, "Acme"),
                item("U-EUR", "gift card", "Active", "EUR", "FIXED_VALUE",
                        new BigDecimal("10"), BigDecimal.ZERO, BigDecimal.ZERO, "Acme"),
                item("  ", "gift card", "Active", "USD", "FIXED_VALUE",
                        new BigDecimal("10"), BigDecimal.ZERO, BigDecimal.ZERO, "Acme"));
        when(xtrmApiClient.getDigitalGiftCards(any(GetDigitalGiftCardsCommand.class)))
                .thenReturn(GetDigitalGiftCardsResult.ok(raw));

        List<GiftCardSkuResponse> result = service.listGiftCardSkus();

        assertThat(result).extracting(GiftCardSkuResponse::sku).containsExactly("U-FIX", "U-VAR");

        GiftCardSkuResponse fixed = result.get(0);
        assertThat(fixed.valueType()).isEqualTo("FIXED");
        assertThat(fixed.brandName()).isEqualTo("Acme"); // first brand wins on dedupe
        assertThat(fixed.faceValue()).isEqualByComparingTo("10");

        GiftCardSkuResponse variable = result.get(1);
        assertThat(variable.valueType()).isEqualTo("VARIABLE");
        assertThat(variable.minValue()).isEqualByComparingTo("5");
        assertThat(variable.maxValue()).isEqualByComparingTo("500");
    }

    @Test
    void listGiftCardSkus_throwsExternalServiceException_whenXtrmUnavailable() {
        when(xtrmApiClient.getDigitalGiftCards(any(GetDigitalGiftCardsCommand.class)))
                .thenReturn(GetDigitalGiftCardsResult.failed(List.of("timeout"), true));

        assertThatThrownBy(() -> service.listGiftCardSkus())
                .isInstanceOf(ExternalServiceException.class);
    }

    @Test
    void findBySku_returnsMatch_orEmpty_andShortCircuitsOnBlank() {
        when(xtrmApiClient.getDigitalGiftCards(any(GetDigitalGiftCardsCommand.class)))
                .thenReturn(GetDigitalGiftCardsResult.ok(List.of(
                        item("U-FIX", "gift card", "Active", "USD", "FIXED_VALUE",
                                new BigDecimal("10"), BigDecimal.ZERO, BigDecimal.ZERO, "Acme"))));

        assertThat(service.findBySku("U-FIX")).isPresent();
        assertThat(service.findBySku("NOPE")).isEmpty();
        assertThat(service.findBySku(null)).isEmpty();
    }
}
