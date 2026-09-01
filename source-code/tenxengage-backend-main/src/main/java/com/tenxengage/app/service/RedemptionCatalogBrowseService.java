package com.tenxengage.app.service;

import com.tenxengage.app.dto.response.CatalogBrowseItemResponse;
import com.tenxengage.app.entity.ClientCatalogItemConfig;
import com.tenxengage.app.entity.ClientCatalogRegionConfig;
import com.tenxengage.app.entity.RedemptionCatalogItem;
import com.tenxengage.app.entity.TenantRedemptionSettings;
import com.tenxengage.app.entity.enums.BatchCadence;
import com.tenxengage.app.entity.enums.RedemptionCategory;
import com.tenxengage.app.exception.ResourceNotFoundException;
import com.tenxengage.app.repository.ClientCatalogItemConfigRepository;
import com.tenxengage.app.repository.ClientCatalogRegionConfigRepository;
import com.tenxengage.app.repository.RedemptionCatalogItemRepository;
import com.tenxengage.app.repository.RewardBalanceRepository;
import com.tenxengage.app.repository.TenantRedemptionSettingsRepository;
import com.tenxengage.app.security.TenantContext;
import com.tenxengage.app.security.TenantValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class RedemptionCatalogBrowseService {

    private static final Logger log = LoggerFactory.getLogger(RedemptionCatalogBrowseService.class);

    private final RedemptionCatalogItemRepository catalogItemRepository;
    private final ClientCatalogItemConfigRepository configRepository;
    private final ClientCatalogRegionConfigRepository regionConfigRepository;
    private final TenantRedemptionSettingsRepository settingsRepository;
    private final RewardBalanceRepository balanceRepository;
    private final TenantValidator tenantValidator;

    public RedemptionCatalogBrowseService(
            RedemptionCatalogItemRepository catalogItemRepository,
            ClientCatalogItemConfigRepository configRepository,
            ClientCatalogRegionConfigRepository regionConfigRepository,
            TenantRedemptionSettingsRepository settingsRepository,
            RewardBalanceRepository balanceRepository,
            TenantValidator tenantValidator) {
        this.catalogItemRepository = catalogItemRepository;
        this.configRepository = configRepository;
        this.regionConfigRepository = regionConfigRepository;
        this.settingsRepository = settingsRepository;
        this.balanceRepository = balanceRepository;
        this.tenantValidator = tenantValidator;
    }

    @Transactional(readOnly = true)
    public Page<CatalogBrowseItemResponse> browsePartnerCatalog(String currencyId, String region, Pageable pageable) {
        UUID clientId = TenantContext.getClientId();
        UUID userId = tenantValidator.getCurrentUserId();

        Map<String, BigDecimal> balanceByCurrency = balanceRepository
                .findByClientIdAndUserId(clientId, userId)
                .stream()
                .collect(Collectors.toMap(b -> b.getCurrencyId(), b -> b.getBalance()));

        Collection<String> currencyFilter = currencyId != null
                ? List.of(currencyId)
                : balanceByCurrency.keySet();

        if (currencyFilter.isEmpty()) {
            return Page.empty(pageable);
        }

        List<RedemptionCatalogItem> items = catalogItemRepository
                .findByCurrencyIdInAndIsActive(currencyFilter, true, Pageable.unpaged())
                .getContent();

        if (items.isEmpty()) {
            return Page.empty(pageable);
        }

        List<UUID> itemIds = items.stream().map(RedemptionCatalogItem::getId).toList();

        // Single query — no N+1
        Map<UUID, ClientCatalogItemConfig> configsByItemId = configRepository
                .findByClientIdAndEnabledAndRedemptionCatalogItemIdIn(clientId, true, itemIds)
                .stream()
                .collect(Collectors.toMap(ClientCatalogItemConfig::getRedemptionCatalogItemId, Function.identity()));

        // Single query — no N+1
        Map<UUID, List<ClientCatalogRegionConfig>> regionConfigsByItemId = regionConfigRepository
                .findByClientIdAndRedemptionCatalogItemIdIn(clientId, itemIds)
                .stream()
                .collect(Collectors.groupingBy(ClientCatalogRegionConfig::getRedemptionCatalogItemId));

        BatchCadence batchCadence = settingsRepository.findByClientId(clientId)
                .map(TenantRedemptionSettings::getBatchCadence)
                .orElse(BatchCadence.DAILY);

        List<CatalogBrowseItemResponse> filtered = items.stream()
                .filter(item -> configsByItemId.containsKey(item.getId()))
                .filter(item -> region == null || isAvailableInRegion(
                        configsByItemId.get(item.getId()),
                        regionConfigsByItemId.get(item.getId()),
                        region))
                .map(item -> {
                    ClientCatalogItemConfig config = configsByItemId.get(item.getId());
                    BigDecimal balance = balanceByCurrency.getOrDefault(item.getCurrencyId(), BigDecimal.ZERO);
                    return CatalogBrowseItemResponse.from(item, config, balance, batchCadence);
                })
                .sorted(catalogOrder())
                .toList();

        log.info("featureArea=redemption-catalog step=partner_catalog_browse tenantId={} userId={} resultCount={}",
                clientId, userId, filtered.size());

        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), filtered.size());
        List<CatalogBrowseItemResponse> page = start >= filtered.size() ? List.of() : filtered.subList(start, end);
        return new PageImpl<>(page, pageable, filtered.size());
    }

    @Transactional(readOnly = true)
    public CatalogBrowseItemResponse getPartnerCatalogItem(UUID catalogItemId, String region) {
        UUID clientId = TenantContext.getClientId();
        UUID userId = tenantValidator.getCurrentUserId();

        RedemptionCatalogItem item = catalogItemRepository.findById(catalogItemId)
                .filter(RedemptionCatalogItem::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("RedemptionCatalogItem", "id", catalogItemId));

        ClientCatalogItemConfig config = configRepository
                .findByClientIdAndRedemptionCatalogItemId(clientId, catalogItemId)
                .filter(ClientCatalogItemConfig::isEnabled)
                .orElseThrow(() -> new ResourceNotFoundException("RedemptionCatalogItem", "id", catalogItemId));

        if (region != null) {
            List<ClientCatalogRegionConfig> regionConfigs =
                    regionConfigRepository.findByClientIdAndRedemptionCatalogItemId(clientId, catalogItemId);
            if (!isAvailableInRegion(config, regionConfigs, region)) {
                throw new ResourceNotFoundException("RedemptionCatalogItem", "id", catalogItemId);
            }
        }

        BigDecimal balance = balanceRepository
                .findByClientIdAndUserIdAndCurrencyId(clientId, userId, item.getCurrencyId())
                .map(b -> b.getBalance())
                .orElse(BigDecimal.ZERO);

        BatchCadence batchCadence = settingsRepository.findByClientId(clientId)
                .map(TenantRedemptionSettings::getBatchCadence)
                .orElse(BatchCadence.DAILY);

        return CatalogBrowseItemResponse.from(item, config, balance, batchCadence);
    }

    private boolean isAvailableInRegion(ClientCatalogItemConfig config,
                                        List<ClientCatalogRegionConfig> regionConfigs,
                                        String region) {
        if (regionConfigs == null || regionConfigs.isEmpty()) {
            return config.isEnabled();
        }
        return regionConfigs.stream()
                .filter(rc -> rc.getRegionCode().equals(region))
                .findFirst()
                .map(ClientCatalogRegionConfig::isEnabled)
                .orElse(config.isEnabled());
    }

    private Comparator<CatalogBrowseItemResponse> catalogOrder() {
        return (a, b) -> {
            int byCurrency = a.currencyId().compareTo(b.currencyId());
            if (byCurrency != 0) return byCurrency;
            if (a.category() == b.category()) {
                if (a.category() == RedemptionCategory.CASH) {
                    return a.effectiveMinTransactionAmount().compareTo(b.effectiveMinTransactionAmount());
                }
                return a.name().compareTo(b.name());
            }
            return a.category() == RedemptionCategory.NON_CASH ? -1 : 1;
        };
    }
}
