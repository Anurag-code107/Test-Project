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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class TenantRedemptionCatalogService {

    private static final Logger log = LoggerFactory.getLogger(TenantRedemptionCatalogService.class);

    private final TenantRedemptionSettingsRepository settingsRepository;
    private final RedemptionCatalogItemRepository catalogItemRepository;
    private final ClientCatalogItemConfigRepository configRepository;
    private final ClientCatalogRegionConfigRepository regionConfigRepository;
    private final TenantValidator tenantValidator;

    public TenantRedemptionCatalogService(
            TenantRedemptionSettingsRepository settingsRepository,
            RedemptionCatalogItemRepository catalogItemRepository,
            ClientCatalogItemConfigRepository configRepository,
            ClientCatalogRegionConfigRepository regionConfigRepository,
            TenantValidator tenantValidator) {
        this.settingsRepository = settingsRepository;
        this.catalogItemRepository = catalogItemRepository;
        this.configRepository = configRepository;
        this.regionConfigRepository = regionConfigRepository;
        this.tenantValidator = tenantValidator;
    }

    @Transactional
    public TenantRedemptionSettingsResponse getTenantSettings() {
        UUID clientId = tenantValidator.getCurrentClientId();
        TenantRedemptionSettings settings = findOrCreateSettings(clientId);
        return TenantRedemptionSettingsResponse.from(settings);
    }

    @Transactional
    public TenantRedemptionSettingsResponse updateTenantSettings(UpdateTenantRedemptionSettingsRequest request) {
        UUID clientId = tenantValidator.getCurrentClientId();
        TenantRedemptionSettings settings = findOrCreateSettings(clientId);
        settings.setBatchCadence(request.batchCadence());
        if (request.maxInFlightRedemptions() != null) {
            settings.setMaxInFlightRedemptions(request.maxInFlightRedemptions());
        }
        log.info("featureArea=redemption-catalog step=tenant_settings_updated tenantId={} batchCadence={} maxInFlightRedemptions={}",
                clientId, request.batchCadence(), settings.getMaxInFlightRedemptions());
        return TenantRedemptionSettingsResponse.from(settingsRepository.save(settings));
    }

    @Transactional(readOnly = true)
    public Page<TenantCatalogItemResponse> getTenantCatalog(Boolean enabled, String category, String search, Pageable pageable) {
        UUID clientId = tenantValidator.getCurrentClientId();
        Page<RedemptionCatalogItem> items = (search != null && !search.isBlank())
                ? catalogItemRepository.searchByName(search, pageable)
                : catalogItemRepository.findAllByOrderByNameAsc(pageable);

        Set<UUID> pageItemIds = items.stream().map(RedemptionCatalogItem::getId).collect(Collectors.toSet());
        Map<UUID, ClientCatalogItemConfig> configsByItemId = configRepository
                .findByClientIdAndRedemptionCatalogItemIdIn(clientId, pageItemIds)
                .stream()
                .collect(Collectors.toMap(ClientCatalogItemConfig::getRedemptionCatalogItemId, Function.identity()));

        return items.map(item -> TenantCatalogItemResponse.from(item, configsByItemId.get(item.getId())));
    }

    @Transactional
    public ClientCatalogItemConfigResponse upsertItemConfig(UUID catalogItemId,
                                                             UpsertClientCatalogItemConfigRequest request) {
        UUID clientId = tenantValidator.getCurrentClientId();

        RedemptionCatalogItem item = catalogItemRepository.findById(catalogItemId)
                .filter(RedemptionCatalogItem::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("RedemptionCatalogItem", "id", catalogItemId));

        if (request.minTransactionAmountOverride() != null
                && request.minTransactionAmountOverride()
                        .compareTo(item.getDefaultMinRedemptionAmount()) < 0) {
            throw new BusinessRuleException(
                    "Minimum transaction amount cannot be set below the catalog item's platform minimum of "
                            + item.getDefaultMinRedemptionAmount());
        }

        Optional<ClientCatalogItemConfig> existing =
                configRepository.findByClientIdAndRedemptionCatalogItemId(clientId, catalogItemId);

        ClientCatalogItemConfig config;
        if (existing.isPresent()) {
            config = existing.get();
            config.setEnabled(request.enabled());
            config.setProcessingModeOverride(request.processingModeOverride());
            config.setMinTransactionAmountOverride(request.minTransactionAmountOverride());
            config.setMinWalletBalanceOverride(request.minWalletBalanceOverride());
            config.setReturnWindowDaysOverride(request.returnWindowDaysOverride());
        } else {
            config = ClientCatalogItemConfig.builder()
                    .clientId(clientId)
                    .redemptionCatalogItemId(catalogItemId)
                    .enabled(request.enabled())
                    .processingModeOverride(request.processingModeOverride())
                    .minTransactionAmountOverride(request.minTransactionAmountOverride())
                    .minWalletBalanceOverride(request.minWalletBalanceOverride())
                    .returnWindowDaysOverride(request.returnWindowDaysOverride())
                    .build();
        }

        log.info("featureArea=redemption-catalog step=tenant_catalog_config_updated catalogItemId={} enabled={} tenantId={}",
                catalogItemId, request.enabled(), clientId);

        return ClientCatalogItemConfigResponse.from(configRepository.save(config));
    }

    @Transactional(readOnly = true)
    public List<ClientCatalogRegionConfigResponse> getRegionalConfigs(UUID catalogItemId) {
        UUID clientId = tenantValidator.getCurrentClientId();
        return regionConfigRepository.findByClientIdAndRedemptionCatalogItemId(clientId, catalogItemId)
                .stream()
                .map(ClientCatalogRegionConfigResponse::from)
                .toList();
    }

    @Transactional
    public ClientCatalogRegionConfigResponse upsertRegionConfig(UUID catalogItemId, String regionCode,
                                                                 UpsertRegionConfigRequest request) {
        UUID clientId = tenantValidator.getCurrentClientId();

        RedemptionCatalogItem item = catalogItemRepository.findById(catalogItemId)
                .orElseThrow(() -> new ResourceNotFoundException("RedemptionCatalogItem", "id", catalogItemId));

        if (!Set.of(item.getGeographicScope()).contains(regionCode)) {
            throw new BusinessRuleException(
                    "Region " + regionCode + " is not supported by this catalog item's vendor");
        }

        Optional<ClientCatalogRegionConfig> existing = regionConfigRepository
                .findByClientIdAndRedemptionCatalogItemIdAndRegionCode(clientId, catalogItemId, regionCode);

        ClientCatalogRegionConfig config;
        if (existing.isPresent()) {
            config = existing.get();
            config.setEnabled(request.enabled());
        } else {
            config = ClientCatalogRegionConfig.builder()
                    .clientId(clientId)
                    .redemptionCatalogItemId(catalogItemId)
                    .regionCode(regionCode)
                    .enabled(request.enabled())
                    .build();
        }

        log.info("featureArea=redemption-catalog step=region_config_updated catalogItemId={} regionCode={} enabled={} tenantId={}",
                catalogItemId, regionCode, request.enabled(), clientId);

        return ClientCatalogRegionConfigResponse.from(regionConfigRepository.save(config));
    }

    @Transactional
    public void deleteRegionConfig(UUID catalogItemId, String regionCode) {
        UUID clientId = tenantValidator.getCurrentClientId();
        regionConfigRepository.deleteByClientIdAndRedemptionCatalogItemIdAndRegionCode(
                clientId, catalogItemId, regionCode);
        log.info("featureArea=redemption-catalog step=region_config_deleted catalogItemId={} regionCode={} tenantId={}",
                catalogItemId, regionCode, clientId);
    }

    private TenantRedemptionSettings findOrCreateSettings(UUID clientId) {
        Optional<TenantRedemptionSettings> locked = settingsRepository.findByClientIdWithLock(clientId);
        if (locked.isPresent()) {
            return locked.get();
        }
        TenantRedemptionSettings created = TenantRedemptionSettings.builder()
                .clientId(clientId)
                .batchCadence(BatchCadence.DAILY)
                .build();
        return settingsRepository.save(created);
    }
}
