package com.tenxengage.app.service;

import com.tenxengage.app.client.XoxodayApiClient;
import com.tenxengage.app.client.XoxodayProductResponse;
import com.tenxengage.app.entity.RedemptionCatalogItem;
import com.tenxengage.app.entity.enums.RedemptionCategory;
import com.tenxengage.app.repository.RedemptionCatalogItemRepository;
import com.tenxengage.app.testdata.RedemptionCatalogItemFixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class XoxodaySyncJobServiceTest {

    @Mock private RedemptionCatalogItemRepository catalogItemRepository;
    @Mock private XoxodayApiClient xoxodayApiClient;

    @InjectMocks private XoxodaySyncJobService service;

    @Test
    void runSync_throwsOnEmptyXoxodayResponse() {
        when(xoxodayApiClient.fetchAllProducts()).thenReturn(List.of());

        assertThatThrownBy(() -> service.runSync())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("empty product catalog");

        verify(catalogItemRepository, never()).saveAll(any());
    }

    @Test
    void runSync_deactivatesItemsAbsentFromXoxodayResponse() {
        RedemptionCatalogItem item = RedemptionCatalogItemFixtures.activeNonCashItem()
                .providerItemId("AMZN-001")
                .build();
        item.setId(UUID.randomUUID());

        when(catalogItemRepository.countByCategoryAndIsActive(RedemptionCategory.NON_CASH, true)).thenReturn(1L);
        when(xoxodayApiClient.fetchAllProducts()).thenReturn(List.of(new XoxodayProductResponse("OTHER-001")));
        when(catalogItemRepository.findAllByCategory(RedemptionCategory.NON_CASH)).thenReturn(List.of(item));
        when(catalogItemRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        service.runSync();

        assertThat(item.isActive()).isFalse();
        assertThat(item.getXoxodayLastSyncedAt()).isNotNull();
    }

    @Test
    void runSync_updatesXoxodayLastSyncedAtOnSuccess() {
        RedemptionCatalogItem item = RedemptionCatalogItemFixtures.activeNonCashItem()
                .providerItemId("AMZN-001")
                .build();
        item.setId(UUID.randomUUID());

        when(catalogItemRepository.countByCategoryAndIsActive(RedemptionCategory.NON_CASH, true)).thenReturn(1L);
        when(xoxodayApiClient.fetchAllProducts()).thenReturn(List.of(new XoxodayProductResponse("AMZN-001")));
        when(catalogItemRepository.findAllByCategory(RedemptionCategory.NON_CASH)).thenReturn(List.of(item));
        when(catalogItemRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        service.runSync();

        assertThat(item.getXoxodayLastSyncedAt()).isNotNull();
        assertThat(service.getSyncStatus()).isEqualTo("SUCCESS");
        assertThat(service.getLastSyncAt()).isNotNull();
    }

    @Test
    void runSync_doesNotDeactivateItemsPresentInXoxodayResponse() {
        RedemptionCatalogItem item = RedemptionCatalogItemFixtures.activeNonCashItem()
                .providerItemId("AMZN-001")
                .build();
        item.setId(UUID.randomUUID());

        when(catalogItemRepository.countByCategoryAndIsActive(RedemptionCategory.NON_CASH, true)).thenReturn(1L);
        when(xoxodayApiClient.fetchAllProducts()).thenReturn(List.of(new XoxodayProductResponse("AMZN-001")));
        when(catalogItemRepository.findAllByCategory(RedemptionCategory.NON_CASH)).thenReturn(List.of(item));
        when(catalogItemRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        service.runSync();

        assertThat(item.isActive()).isTrue();
    }

    @Test
    void handleSyncFailure_doesNotDeactivateItems() {
        service.handleSyncFailure(new RuntimeException("API timeout"));

        verify(catalogItemRepository, never()).saveAll(any());
        verify(catalogItemRepository, never()).save(any());
        assertThat(service.getSyncStatus()).isEqualTo("FAILED");
        assertThat(service.getFailedSyncCount()).isEqualTo(1);
    }
}
