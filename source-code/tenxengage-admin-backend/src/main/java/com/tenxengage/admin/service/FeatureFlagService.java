package com.tenxengage.admin.service;

import com.tenxengage.admin.config.CacheInvalidationPublisher;
import com.tenxengage.admin.dto.request.CreateFeatureFlagRequest;
import com.tenxengage.admin.dto.request.SetFeatureOverrideRequest;
import com.tenxengage.admin.dto.request.UpdateFeatureFlagRequest;
import com.tenxengage.admin.dto.response.ClientFeatureOverrideResponse;
import com.tenxengage.admin.dto.response.FeatureFlagResponse;
import com.tenxengage.admin.entity.Client;
import com.tenxengage.admin.entity.ClientFeatureOverride;
import com.tenxengage.admin.entity.FeatureFlag;
import com.tenxengage.admin.entity.enums.SubscriptionTier;
import com.tenxengage.admin.exception.BusinessRuleException;
import com.tenxengage.admin.exception.ResourceNotFoundException;
import com.tenxengage.admin.repository.ClientFeatureOverrideRepository;
import com.tenxengage.admin.repository.ClientRepository;
import com.tenxengage.admin.repository.FeatureFlagRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class FeatureFlagService {

    private static final Logger log = LoggerFactory.getLogger(FeatureFlagService.class);

    private final FeatureFlagRepository featureFlagRepository;
    private final ClientFeatureOverrideRepository overrideRepository;
    private final ClientRepository clientRepository;
    private final CacheInvalidationPublisher cacheInvalidationPublisher;

    public FeatureFlagService(FeatureFlagRepository featureFlagRepository,
                              ClientFeatureOverrideRepository overrideRepository,
                              ClientRepository clientRepository,
                              CacheInvalidationPublisher cacheInvalidationPublisher) {
        this.featureFlagRepository = featureFlagRepository;
        this.overrideRepository = overrideRepository;
        this.clientRepository = clientRepository;
        this.cacheInvalidationPublisher = cacheInvalidationPublisher;
    }

    @Transactional(readOnly = true)
    public List<FeatureFlagResponse> getAllFeatureFlags() {
        return featureFlagRepository.findAll().stream()
            .map(FeatureFlagResponse::from)
            .toList();
    }

    @Cacheable(value = "enabledFeatures", key = "#clientId", condition = "#clientId != null")
    @Transactional(readOnly = true)
    public List<String> getEnabledFeatures(UUID clientId) {
        if (clientId == null) {
            return List.of();
        }

        Client client = clientRepository.findById(clientId).orElse(null);
        if (client == null) {
            return List.of();
        }

        List<FeatureFlag> allFlags = featureFlagRepository.findAll();
        Map<UUID, Boolean> overrides = overrideRepository.findByClientId(clientId).stream()
            .collect(Collectors.toMap(ClientFeatureOverride::getFeatureFlagId,
                                     ClientFeatureOverride::isEnabled));

        List<String> enabled = new ArrayList<>();
        for (FeatureFlag flag : allFlags) {
            Boolean override = overrides.get(flag.getId());
            if (override != null) {
                if (override) {
                    enabled.add(flag.getFeatureKey());
                }
            } else if (isEnabledForTier(flag, client.getSubscriptionTier())) {
                enabled.add(flag.getFeatureKey());
            }
        }
        return enabled;
    }

    @Transactional
    public FeatureFlagResponse createFeatureFlag(CreateFeatureFlagRequest request) {
        featureFlagRepository.findByFeatureKey(request.featureKey()).ifPresent(existing -> {
            throw new BusinessRuleException(
                "A feature flag with key '" + request.featureKey() + "' already exists");
        });

        FeatureFlag flag = FeatureFlag.builder()
            .featureKey(request.featureKey())
            .description(request.description())
            .starterEnabled(request.starterEnabled() != null ? request.starterEnabled() : false)
            .professionalEnabled(request.professionalEnabled() != null ? request.professionalEnabled() : false)
            .enterpriseEnabled(request.enterpriseEnabled() != null ? request.enterpriseEnabled() : true)
            .build();

        FeatureFlag saved = featureFlagRepository.save(flag);
        return FeatureFlagResponse.from(saved);
    }

    @CacheEvict(value = "enabledFeatures", allEntries = true)
    @Transactional
    public FeatureFlagResponse updateFeatureFlag(UUID id, UpdateFeatureFlagRequest request) {
        FeatureFlag flag = featureFlagRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("FeatureFlag", "id", id));

        if (request.description() != null) {
            flag.setDescription(request.description());
        }
        if (request.starterEnabled() != null) {
            flag.setStarterEnabled(request.starterEnabled());
        }
        if (request.professionalEnabled() != null) {
            flag.setProfessionalEnabled(request.professionalEnabled());
        }
        if (request.enterpriseEnabled() != null) {
            flag.setEnterpriseEnabled(request.enterpriseEnabled());
        }

        FeatureFlag saved = featureFlagRepository.save(flag);
        log.info("Updated feature flag '{}' (id={})", flag.getFeatureKey(), id);
        cacheInvalidationPublisher.evictAll("enabledFeatures");
        return FeatureFlagResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<ClientFeatureOverrideResponse> getOverridesForClient(UUID clientId) {
        List<ClientFeatureOverride> overrides = overrideRepository.findByClientId(clientId);
        Map<UUID, FeatureFlag> flagMap = featureFlagRepository.findAll().stream()
            .collect(Collectors.toMap(FeatureFlag::getId, f -> f));

        return overrides.stream()
            .map(o -> {
                FeatureFlag flag = flagMap.get(o.getFeatureFlagId());
                String featureKey = flag != null ? flag.getFeatureKey() : "UNKNOWN";
                return new ClientFeatureOverrideResponse(o.getFeatureFlagId(), featureKey, o.isEnabled());
            })
            .toList();
    }

    @CacheEvict(value = "enabledFeatures", key = "#clientId")
    @Transactional
    public List<ClientFeatureOverrideResponse> setOverridesForClient(UUID clientId,
                                                                      List<SetFeatureOverrideRequest> requests) {
        overrideRepository.deleteByClientId(clientId);

        List<ClientFeatureOverride> newOverrides = new ArrayList<>();
        for (SetFeatureOverrideRequest req : requests) {
            featureFlagRepository.findById(req.featureFlagId())
                .orElseThrow(() -> new ResourceNotFoundException("FeatureFlag", "id", req.featureFlagId()));

            ClientFeatureOverride override = ClientFeatureOverride.builder()
                .clientId(clientId)
                .featureFlagId(req.featureFlagId())
                .enabled(req.enabled())
                .build();
            newOverrides.add(override);
        }

        overrideRepository.saveAll(newOverrides);
        cacheInvalidationPublisher.evict("enabledFeatures", clientId.toString());
        return getOverridesForClient(clientId);
    }

    private boolean isEnabledForTier(FeatureFlag flag, SubscriptionTier tier) {
        return switch (tier) {
            case STARTER -> flag.isStarterEnabled();
            case PROFESSIONAL -> flag.isProfessionalEnabled();
            case ENTERPRISE -> flag.isEnterpriseEnabled();
        };
    }
}
