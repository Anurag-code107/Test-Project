package com.tenxengage.app.service;

import com.tenxengage.app.dto.response.CatalogBrowseItemResponse;
import com.tenxengage.app.entity.ClientCatalogItemConfig;
import com.tenxengage.app.entity.ClientCatalogRegionConfig;
import com.tenxengage.app.entity.RedemptionCatalogItem;
import com.tenxengage.app.entity.RewardBalance;
import com.tenxengage.app.entity.TenantRedemptionSettings;
import com.tenxengage.app.entity.enums.BatchCadence;
import com.tenxengage.app.entity.enums.RedemptionProcessingMode;
import com.tenxengage.app.exception.ResourceNotFoundException;
import com.tenxengage.app.repository.ClientCatalogItemConfigRepository;
import com.tenxengage.app.repository.ClientCatalogRegionConfigRepository;
import com.tenxengage.app.repository.RedemptionCatalogItemRepository;
import com.tenxengage.app.repository.RewardBalanceRepository;
import com.tenxengage.app.repository.TenantRedemptionSettingsRepository;
import com.tenxengage.app.security.TenantContext;
import com.tenxengage.app.security.TenantValidator;
import com.tenxengage.app.testdata.ClientCatalogItemConfigFixtures;
import com.tenxengage.app.testdata.ClientCatalogRegionConfigFixtures;
import com.tenxengage.app.testdata.RedemptionCatalogItemFixtures;
import org.junit.jupiter.api.AfterEach;
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
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedemptionCatalogBrowseServiceTest {

    @Mock private RedemptionCatalogItemRepository catalogItemRepository;
    @Mock private ClientCatalogItemConfigRepository configRepository;
    @Mock private ClientCatalogRegionConfigRepository regionConfigRepository;
    @Mock private TenantRedemptionSettingsRepository settingsRepository;
    @Mock private RewardBalanceRepository balanceRepository;
    @Mock private TenantValidator tenantValidator;

    @InjectMocks private RedemptionCatalogBrowseService service;

    private static final UUID CLIENT_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID ITEM_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        TenantContext.setClientId(CLIENT_ID);
        when(tenantValidator.getCurrentUserId()).thenReturn(USER_ID);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private RewardBalance usdBalance(BigDecimal amount) {
        return RewardBalance.builder()
                .clientId(CLIENT_ID)
                .userId(USER_ID)
                .currencyId("USD")
                .balance(amount)
                .build();
    }

    private void stubSettings(BatchCadence cadence) {
        TenantRedemptionSettings settings = TenantRedemptionSettings.builder()
                .clientId(CLIENT_ID)
                .batchCadence(cadence)
                .build();
        when(settingsRepository.findByClientId(CLIENT_ID)).thenReturn(Optional.of(settings));
    }

    @Test
    void browsePartnerCatalog_excludesInactiveItems() {
        when(balanceRepository.findByClientIdAndUserId(CLIENT_ID, USER_ID))
                .thenReturn(List.of(usdBalance(new BigDecimal("100.00"))));
        when(catalogItemRepository.findByCurrencyIdInAndIsActive(anyCollection(), eq(true), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        var result = service.browsePartnerCatalog(null, null, Pageable.ofSize(20));

        assertThat(result.getContent()).isEmpty();
    }

    @Test
    void browsePartnerCatalog_excludesDisabledTenantItems() {
        RedemptionCatalogItem item = RedemptionCatalogItemFixtures.activeNonCashItem().build();
        item.setId(ITEM_ID);

        when(balanceRepository.findByClientIdAndUserId(CLIENT_ID, USER_ID))
                .thenReturn(List.of(usdBalance(new BigDecimal("100.00"))));
        when(catalogItemRepository.findByCurrencyIdInAndIsActive(anyCollection(), eq(true), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(item)));
        // No enabled config returned → item excluded
        when(configRepository.findByClientIdAndEnabledAndRedemptionCatalogItemIdIn(eq(CLIENT_ID), eq(true), anyCollection()))
                .thenReturn(List.of());
        when(regionConfigRepository.findByClientIdAndRedemptionCatalogItemIdIn(eq(CLIENT_ID), anyCollection()))
                .thenReturn(List.of());
        stubSettings(BatchCadence.DAILY);

        var result = service.browsePartnerCatalog(null, null, Pageable.ofSize(20));

        assertThat(result.getContent()).isEmpty();
    }

    @Test
    void browsePartnerCatalog_appliesRegionalFilter() {
        RedemptionCatalogItem item = RedemptionCatalogItemFixtures.activeNonCashItem()
                .geographicScope(new String[]{"US", "IN"})
                .build();
        item.setId(ITEM_ID);

        ClientCatalogItemConfig config = ClientCatalogItemConfigFixtures.enabledConfig(CLIENT_ID, ITEM_ID).build();
        ClientCatalogRegionConfig usDisabled = ClientCatalogRegionConfigFixtures
                .disabledRegion(CLIENT_ID, ITEM_ID, "US").build();

        when(balanceRepository.findByClientIdAndUserId(CLIENT_ID, USER_ID))
                .thenReturn(List.of(usdBalance(new BigDecimal("100.00"))));
        when(catalogItemRepository.findByCurrencyIdInAndIsActive(anyCollection(), eq(true), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(item)));
        when(configRepository.findByClientIdAndEnabledAndRedemptionCatalogItemIdIn(eq(CLIENT_ID), eq(true), anyCollection()))
                .thenReturn(List.of(config));
        when(regionConfigRepository.findByClientIdAndRedemptionCatalogItemIdIn(eq(CLIENT_ID), anyCollection()))
                .thenReturn(List.of(usDisabled));
        stubSettings(BatchCadence.DAILY);

        var result = service.browsePartnerCatalog(null, "US", Pageable.ofSize(20));

        assertThat(result.getContent()).isEmpty();
    }

    @Test
    void browsePartnerCatalog_setsCanAffordFalse_whenBalanceBelowMinWallet() {
        RedemptionCatalogItem item = RedemptionCatalogItemFixtures.activeNonCashItem().build();
        item.setId(ITEM_ID);

        ClientCatalogItemConfig config = ClientCatalogItemConfigFixtures.enabledConfig(CLIENT_ID, ITEM_ID)
                .minWalletBalanceOverride(new BigDecimal("100.00"))
                .build();

        when(balanceRepository.findByClientIdAndUserId(CLIENT_ID, USER_ID))
                .thenReturn(List.of(usdBalance(new BigDecimal("50.00"))));
        when(catalogItemRepository.findByCurrencyIdInAndIsActive(anyCollection(), eq(true), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(item)));
        when(configRepository.findByClientIdAndEnabledAndRedemptionCatalogItemIdIn(eq(CLIENT_ID), eq(true), anyCollection()))
                .thenReturn(List.of(config));
        when(regionConfigRepository.findByClientIdAndRedemptionCatalogItemIdIn(eq(CLIENT_ID), anyCollection()))
                .thenReturn(List.of());
        stubSettings(BatchCadence.DAILY);

        var result = service.browsePartnerCatalog(null, null, Pageable.ofSize(20));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).canAfford()).isFalse();
    }

    @Test
    void browsePartnerCatalog_populatesShortfallAmount() {
        RedemptionCatalogItem item = RedemptionCatalogItemFixtures.activeNonCashItem().build();
        item.setId(ITEM_ID);

        ClientCatalogItemConfig config = ClientCatalogItemConfigFixtures.enabledConfig(CLIENT_ID, ITEM_ID)
                .minWalletBalanceOverride(new BigDecimal("100.00"))
                .build();

        when(balanceRepository.findByClientIdAndUserId(CLIENT_ID, USER_ID))
                .thenReturn(List.of(usdBalance(new BigDecimal("40.00"))));
        when(catalogItemRepository.findByCurrencyIdInAndIsActive(anyCollection(), eq(true), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(item)));
        when(configRepository.findByClientIdAndEnabledAndRedemptionCatalogItemIdIn(eq(CLIENT_ID), eq(true), anyCollection()))
                .thenReturn(List.of(config));
        when(regionConfigRepository.findByClientIdAndRedemptionCatalogItemIdIn(eq(CLIENT_ID), anyCollection()))
                .thenReturn(List.of());
        stubSettings(BatchCadence.DAILY);

        CatalogBrowseItemResponse result = service.browsePartnerCatalog(null, null, Pageable.ofSize(20))
                .getContent().get(0);

        assertThat(result.shortfallAmount()).isEqualByComparingTo("60.00");
    }

    @Test
    void browsePartnerCatalog_buildsPayoutTimeline_instant() {
        RedemptionCatalogItem item = RedemptionCatalogItemFixtures.activeNonCashItem()
                .defaultProcessingMode(RedemptionProcessingMode.INSTANT)
                .build();
        item.setId(ITEM_ID);
        ClientCatalogItemConfig config = ClientCatalogItemConfigFixtures.enabledConfig(CLIENT_ID, ITEM_ID).build();

        when(balanceRepository.findByClientIdAndUserId(CLIENT_ID, USER_ID))
                .thenReturn(List.of(usdBalance(new BigDecimal("100.00"))));
        when(catalogItemRepository.findByCurrencyIdInAndIsActive(anyCollection(), anyBoolean(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(item)));
        when(configRepository.findByClientIdAndEnabledAndRedemptionCatalogItemIdIn(eq(CLIENT_ID), eq(true), anyCollection()))
                .thenReturn(List.of(config));
        when(regionConfigRepository.findByClientIdAndRedemptionCatalogItemIdIn(eq(CLIENT_ID), anyCollection()))
                .thenReturn(List.of());
        stubSettings(BatchCadence.DAILY);

        CatalogBrowseItemResponse result = service.browsePartnerCatalog(null, null, Pageable.ofSize(20))
                .getContent().get(0);

        assertThat(result.estimatedPayoutTimeline()).contains("minutes");
    }

    @Test
    void browsePartnerCatalog_buildsPayoutTimeline_batch() {
        RedemptionCatalogItem item = RedemptionCatalogItemFixtures.activeCashItem()
                .defaultProcessingMode(RedemptionProcessingMode.BATCH)
                .build();
        item.setId(ITEM_ID);
        ClientCatalogItemConfig config = ClientCatalogItemConfigFixtures.enabledConfig(CLIENT_ID, ITEM_ID).build();

        when(balanceRepository.findByClientIdAndUserId(CLIENT_ID, USER_ID))
                .thenReturn(List.of(usdBalance(new BigDecimal("100.00"))));
        when(catalogItemRepository.findByCurrencyIdInAndIsActive(anyCollection(), anyBoolean(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(item)));
        when(configRepository.findByClientIdAndEnabledAndRedemptionCatalogItemIdIn(eq(CLIENT_ID), eq(true), anyCollection()))
                .thenReturn(List.of(config));
        when(regionConfigRepository.findByClientIdAndRedemptionCatalogItemIdIn(eq(CLIENT_ID), anyCollection()))
                .thenReturn(List.of());
        stubSettings(BatchCadence.DAILY);

        CatalogBrowseItemResponse result = service.browsePartnerCatalog(null, null, Pageable.ofSize(20))
                .getContent().get(0);

        assertThat(result.estimatedPayoutTimeline()).contains("batch");
    }

    @Test
    void browsePartnerCatalog_buildsPayoutTimeline_approvalRequired() {
        RedemptionCatalogItem item = RedemptionCatalogItemFixtures.activeNonCashItem()
                .defaultProcessingMode(RedemptionProcessingMode.APPROVAL_REQUIRED)
                .build();
        item.setId(ITEM_ID);
        ClientCatalogItemConfig config = ClientCatalogItemConfigFixtures.enabledConfig(CLIENT_ID, ITEM_ID).build();

        when(balanceRepository.findByClientIdAndUserId(CLIENT_ID, USER_ID))
                .thenReturn(List.of(usdBalance(new BigDecimal("100.00"))));
        when(catalogItemRepository.findByCurrencyIdInAndIsActive(anyCollection(), anyBoolean(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(item)));
        when(configRepository.findByClientIdAndEnabledAndRedemptionCatalogItemIdIn(eq(CLIENT_ID), eq(true), anyCollection()))
                .thenReturn(List.of(config));
        when(regionConfigRepository.findByClientIdAndRedemptionCatalogItemIdIn(eq(CLIENT_ID), anyCollection()))
                .thenReturn(List.of());
        stubSettings(BatchCadence.DAILY);

        CatalogBrowseItemResponse result = service.browsePartnerCatalog(null, null, Pageable.ofSize(20))
                .getContent().get(0);

        assertThat(result.estimatedPayoutTimeline()).containsIgnoringCase("approval");
    }

    @Test
    void browsePartnerCatalog_neverIncludesSensitiveFields() {
        RedemptionCatalogItem item = RedemptionCatalogItemFixtures.activeNonCashItemWithXoxoday().build();
        item.setId(ITEM_ID);
        ClientCatalogItemConfig config = ClientCatalogItemConfigFixtures.enabledConfig(CLIENT_ID, ITEM_ID).build();

        when(balanceRepository.findByClientIdAndUserId(CLIENT_ID, USER_ID))
                .thenReturn(List.of(usdBalance(new BigDecimal("100.00"))));
        when(catalogItemRepository.findByCurrencyIdInAndIsActive(anyCollection(), anyBoolean(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(item)));
        when(configRepository.findByClientIdAndEnabledAndRedemptionCatalogItemIdIn(eq(CLIENT_ID), eq(true), anyCollection()))
                .thenReturn(List.of(config));
        when(regionConfigRepository.findByClientIdAndRedemptionCatalogItemIdIn(eq(CLIENT_ID), anyCollection()))
                .thenReturn(List.of());
        stubSettings(BatchCadence.DAILY);

        CatalogBrowseItemResponse result = service.browsePartnerCatalog(null, null, Pageable.ofSize(20))
                .getContent().get(0);

        // CatalogBrowseItemResponse is an explicit record — it has no providerItemId, syncMetadata,
        // xoxodayLastSyncedAt, minWalletBalance, or clientId fields
        var fields = java.util.Arrays.stream(CatalogBrowseItemResponse.class.getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName)
                .toList();
        assertThat(fields).doesNotContain("providerItemId", "syncMetadata", "xoxodayLastSyncedAt",
                "minWalletBalance", "clientId");
        assertThat(result).isNotNull();
    }

    @Test
    void getPartnerCatalogItem_returns404_whenRegionExcluded() {
        RedemptionCatalogItem item = RedemptionCatalogItemFixtures.activeNonCashItem()
                .geographicScope(new String[]{"US", "IN"})
                .build();
        item.setId(ITEM_ID);

        ClientCatalogItemConfig config = ClientCatalogItemConfigFixtures.enabledConfig(CLIENT_ID, ITEM_ID).build();
        ClientCatalogRegionConfig usDisabled = ClientCatalogRegionConfigFixtures
                .disabledRegion(CLIENT_ID, ITEM_ID, "US").build();

        when(catalogItemRepository.findById(ITEM_ID)).thenReturn(Optional.of(item));
        when(configRepository.findByClientIdAndRedemptionCatalogItemId(CLIENT_ID, ITEM_ID))
                .thenReturn(Optional.of(config));
        when(regionConfigRepository.findByClientIdAndRedemptionCatalogItemId(CLIENT_ID, ITEM_ID))
                .thenReturn(List.of(usDisabled));

        assertThatThrownBy(() -> service.getPartnerCatalogItem(ITEM_ID, "US"))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
