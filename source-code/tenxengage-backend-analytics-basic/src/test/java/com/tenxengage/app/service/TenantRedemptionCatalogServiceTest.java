package com.tenxengage.app.service;

import com.tenxengage.app.dto.request.UpdateTenantRedemptionSettingsRequest;
import com.tenxengage.app.dto.request.UpsertClientCatalogItemConfigRequest;
import com.tenxengage.app.dto.request.UpsertRegionConfigRequest;
import com.tenxengage.app.dto.response.ClientCatalogItemConfigResponse;
import com.tenxengage.app.dto.response.ClientCatalogRegionConfigResponse;
import com.tenxengage.app.dto.response.TenantCatalogItemResponse;
import com.tenxengage.app.dto.response.TenantRedemptionSettingsResponse;
import com.tenxengage.app.entity.ClientCatalogItemConfig;
import com.tenxengage.app.entity.ClientCatalogRegionConfig;
import com.tenxengage.app.entity.RedemptionCatalogItem;
import com.tenxengage.app.entity.TenantRedemptionSettings;
import com.tenxengage.app.entity.enums.BatchCadence;
import com.tenxengage.app.exception.BusinessRuleException;
import com.tenxengage.app.exception.ResourceNotFoundException;
import com.tenxengage.app.repository.ClientCatalogItemConfigRepository;
import com.tenxengage.app.repository.ClientCatalogRegionConfigRepository;
import com.tenxengage.app.repository.RedemptionCatalogItemRepository;
import com.tenxengage.app.repository.TenantRedemptionSettingsRepository;
import com.tenxengage.app.security.TenantValidator;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import com.tenxengage.app.testdata.ClientCatalogItemConfigFixtures;
import com.tenxengage.app.testdata.ClientCatalogRegionConfigFixtures;
import com.tenxengage.app.testdata.RedemptionCatalogItemFixtures;
import com.tenxengage.app.testdata.TenantRedemptionSettingsFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TenantRedemptionCatalogServiceTest {

    @Mock private TenantRedemptionSettingsRepository settingsRepository;
    @Mock private RedemptionCatalogItemRepository catalogItemRepository;
    @Mock private ClientCatalogItemConfigRepository configRepository;
    @Mock private ClientCatalogRegionConfigRepository regionConfigRepository;
    @Mock private TenantValidator tenantValidator;

    @InjectMocks private TenantRedemptionCatalogService service;

    private static final UUID CLIENT_ID = UUID.randomUUID();
    private static final UUID ITEM_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        when(tenantValidator.getCurrentClientId()).thenReturn(CLIENT_ID);
    }

    @Test
    void getTenantSettings_autoCreatesWithDailyDefault() {
        when(settingsRepository.findByClientIdWithLock(CLIENT_ID)).thenReturn(Optional.empty());
        TenantRedemptionSettings created = TenantRedemptionSettingsFixtures.defaultSettings(CLIENT_ID).build();
        when(settingsRepository.save(any())).thenReturn(created);

        TenantRedemptionSettingsResponse result = service.getTenantSettings();

        assertThat(result.batchCadence()).isEqualTo(BatchCadence.DAILY);
        verify(settingsRepository).save(any());
    }

    @Test
    void updateTenantSettings_changesBatchCadence() {
        TenantRedemptionSettings settings = TenantRedemptionSettingsFixtures.dailySettings(CLIENT_ID).build();
        when(settingsRepository.findByClientIdWithLock(CLIENT_ID)).thenReturn(Optional.of(settings));
        when(settingsRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        TenantRedemptionSettingsResponse result =
                service.updateTenantSettings(new UpdateTenantRedemptionSettingsRequest(BatchCadence.WEEKLY, null));

        assertThat(result.batchCadence()).isEqualTo(BatchCadence.WEEKLY);
        verify(settingsRepository).save(settings);
    }

    @Test
    void upsertItemConfig_enablesItem() {
        RedemptionCatalogItem item = RedemptionCatalogItemFixtures.activeNonCashItem().build();
        item.setId(ITEM_ID);
        when(catalogItemRepository.findById(ITEM_ID)).thenReturn(Optional.of(item));
        when(configRepository.findByClientIdAndRedemptionCatalogItemId(CLIENT_ID, ITEM_ID))
                .thenReturn(Optional.empty());
        ClientCatalogItemConfig saved =
                ClientCatalogItemConfigFixtures.enabledConfig(CLIENT_ID, ITEM_ID).build();
        when(configRepository.save(any())).thenReturn(saved);

        UpsertClientCatalogItemConfigRequest request =
                new UpsertClientCatalogItemConfigRequest(true, null, null, null, null);
        ClientCatalogItemConfigResponse result = service.upsertItemConfig(ITEM_ID, request);

        assertThat(result).isNotNull();
        assertThat(result.enabled()).isTrue();
        verify(configRepository).save(any());
    }

    @Test
    void upsertItemConfig_rejects_minTransactionAmountBelowFloor() {
        RedemptionCatalogItem item = RedemptionCatalogItemFixtures.activeNonCashItem()
                .defaultMinRedemptionAmount(new BigDecimal("10.00"))
                .build();
        item.setId(ITEM_ID);
        when(catalogItemRepository.findById(ITEM_ID)).thenReturn(Optional.of(item));

        UpsertClientCatalogItemConfigRequest request =
                new UpsertClientCatalogItemConfigRequest(true, null, new BigDecimal("5.00"), null, null);

        assertThatThrownBy(() -> service.upsertItemConfig(ITEM_ID, request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("platform minimum of 10.00");
    }

    @Test
    void upsertItemConfig_returns404_whenItemGloballyInactive() {
        RedemptionCatalogItem inactiveItem = RedemptionCatalogItemFixtures.inactiveNonCashItem().build();
        inactiveItem.setId(ITEM_ID);
        when(catalogItemRepository.findById(ITEM_ID)).thenReturn(Optional.of(inactiveItem));

        UpsertClientCatalogItemConfigRequest request =
                new UpsertClientCatalogItemConfigRequest(true, null, null, null, null);

        assertThatThrownBy(() -> service.upsertItemConfig(ITEM_ID, request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void upsertRegionConfig_persistsEnabledState() {
        RedemptionCatalogItem item = RedemptionCatalogItemFixtures.activeNonCashItem()
                .geographicScope(new String[]{"US", "IN"})
                .build();
        item.setId(ITEM_ID);
        when(catalogItemRepository.findById(ITEM_ID)).thenReturn(Optional.of(item));
        when(regionConfigRepository.findByClientIdAndRedemptionCatalogItemIdAndRegionCode(CLIENT_ID, ITEM_ID, "US"))
                .thenReturn(Optional.empty());
        ClientCatalogRegionConfig saved = ClientCatalogRegionConfigFixtures.usRegion(CLIENT_ID, ITEM_ID).build();
        when(regionConfigRepository.save(any())).thenReturn(saved);

        ClientCatalogRegionConfigResponse result =
                service.upsertRegionConfig(ITEM_ID, "US", new UpsertRegionConfigRequest(true));

        assertThat(result.regionCode()).isEqualTo("US");
        assertThat(result.enabled()).isTrue();
        verify(regionConfigRepository).save(any());
    }

    @Test
    void upsertRegionConfig_rejects_regionNotInGeographicScope() {
        RedemptionCatalogItem item = RedemptionCatalogItemFixtures.activeNonCashItem()
                .geographicScope(new String[]{"US"})
                .build();
        item.setId(ITEM_ID);
        when(catalogItemRepository.findById(ITEM_ID)).thenReturn(Optional.of(item));

        assertThatThrownBy(() -> service.upsertRegionConfig(ITEM_ID, "GB", new UpsertRegionConfigRequest(true)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("GB");
    }

    @Test
    void deleteRegionConfig_deletesWhenExists() {
        service.deleteRegionConfig(ITEM_ID, "US");
        verify(regionConfigRepository)
                .deleteByClientIdAndRedemptionCatalogItemIdAndRegionCode(CLIENT_ID, ITEM_ID, "US");
    }

    @Test
    void deleteRegionConfig_isIdempotent_whenRowAbsent() {
        // deleteBy... is a no-op when no matching row exists — verify no exception is thrown
        service.deleteRegionConfig(ITEM_ID, "US");
        verify(regionConfigRepository)
                .deleteByClientIdAndRedemptionCatalogItemIdAndRegionCode(CLIENT_ID, ITEM_ID, "US");
    }

    @Test
    void getRegionalConfigs_returnsAllRowsForItem() {
        ClientCatalogRegionConfig us = ClientCatalogRegionConfigFixtures.usRegion(CLIENT_ID, ITEM_ID).build();
        ClientCatalogRegionConfig in = ClientCatalogRegionConfigFixtures.inRegion(CLIENT_ID, ITEM_ID).build();
        when(regionConfigRepository.findByClientIdAndRedemptionCatalogItemId(CLIENT_ID, ITEM_ID))
                .thenReturn(List.of(us, in));

        List<ClientCatalogRegionConfigResponse> result = service.getRegionalConfigs(ITEM_ID);

        assertThat(result).hasSize(2);
    }

    @Test
    void getTenantCatalog_includesIsGloballyActiveField() {
        RedemptionCatalogItem activeItem = RedemptionCatalogItemFixtures.activeNonCashItem().build();
        activeItem.setId(ITEM_ID);
        RedemptionCatalogItem inactiveItem = RedemptionCatalogItemFixtures.inactiveNonCashItem().build();
        inactiveItem.setId(UUID.randomUUID());

        when(catalogItemRepository.findAllByOrderByNameAsc(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(activeItem, inactiveItem)));
        when(configRepository.findByClientIdAndRedemptionCatalogItemIdIn(any(), any()))
                .thenReturn(List.of());

        var page = service.getTenantCatalog(null, null, null, Pageable.ofSize(20));
        List<TenantCatalogItemResponse> items = page.getContent();

        assertThat(items).hasSize(2);
        assertThat(items.get(0).isGloballyActive()).isTrue();
        assertThat(items.get(1).isGloballyActive()).isFalse();
    }
}
