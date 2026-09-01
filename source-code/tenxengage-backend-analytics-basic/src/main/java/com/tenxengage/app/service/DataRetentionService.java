package com.tenxengage.app.service;

import com.tenxengage.app.entity.RetentionPolicy;
import com.tenxengage.app.entity.RetentionPolicyBound;
import com.tenxengage.app.entity.enums.DataCategory;
import com.tenxengage.app.exception.BusinessRuleException;
import com.tenxengage.app.repository.RetentionPolicyBoundRepository;
import com.tenxengage.app.repository.RetentionPolicyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class DataRetentionService {

    private static final Logger log = LoggerFactory.getLogger(DataRetentionService.class);

    private final RetentionPolicyRepository retentionPolicyRepository;
    private final RetentionPolicyBoundRepository retentionPolicyBoundRepository;

    public DataRetentionService(RetentionPolicyRepository retentionPolicyRepository,
                                RetentionPolicyBoundRepository retentionPolicyBoundRepository) {
        this.retentionPolicyRepository = retentionPolicyRepository;
        this.retentionPolicyBoundRepository = retentionPolicyBoundRepository;
    }

    /**
     * Returns the effective retention policies for a client. Client-specific overrides
     * take precedence; system defaults fill in for any categories not overridden.
     */
    @Transactional(readOnly = true)
    public List<RetentionPolicy> getRetentionPolicies(UUID clientId) {
        List<RetentionPolicy> systemDefaults = retentionPolicyRepository.findByClientIdIsNull();
        List<RetentionPolicy> clientPolicies = retentionPolicyRepository.findByClientId(clientId);

        // Index client overrides by category for fast lookup
        Map<DataCategory, RetentionPolicy> clientOverrides = clientPolicies.stream()
                .collect(Collectors.toMap(RetentionPolicy::getDataCategory, Function.identity()));

        // Merge: client override wins, else system default
        return systemDefaults.stream()
                .map(defaultPolicy -> clientOverrides.getOrDefault(
                        defaultPolicy.getDataCategory(), defaultPolicy))
                .collect(Collectors.toList());
    }

    /**
     * Updates (or creates) a client-specific retention policy for a given data category.
     * Validates that the requested retention days fall within the configured bounds.
     */
    @Transactional
    public RetentionPolicy updateRetentionPolicy(UUID clientId, String dataCategory, int retentionDays) {
        DataCategory category = parseDataCategory(dataCategory);
        validateRetentionBounds(category, retentionDays);

        RetentionPolicy policy = retentionPolicyRepository
                .findByClientIdAndDataCategory(clientId, category)
                .orElseGet(() -> {
                    RetentionPolicy newPolicy = new RetentionPolicy();
                    newPolicy.setClientId(clientId);
                    newPolicy.setDataCategory(category);
                    // Copy action type from system default
                    retentionPolicyRepository.findByClientIdIsNull().stream()
                            .filter(d -> d.getDataCategory() == category)
                            .findFirst()
                            .ifPresent(d -> newPolicy.setActionType(d.getActionType()));
                    return newPolicy;
                });

        policy.setRetentionDays(retentionDays);
        RetentionPolicy saved = retentionPolicyRepository.save(policy);
        log.info("Retention policy updated: clientId={}, category={}, retentionDays={}",
                clientId, category, retentionDays);
        return saved;
    }

    /**
     * Returns the system-wide default retention policies (client_id IS NULL).
     */
    @Transactional(readOnly = true)
    public List<RetentionPolicy> getSystemDefaults() {
        return retentionPolicyRepository.findByClientIdIsNull();
    }

    /**
     * Returns the min/max bounds for each data category as a map of category name to [min, max].
     */
    @Transactional(readOnly = true)
    public Map<String, int[]> getRetentionBounds() {
        List<RetentionPolicyBound> bounds = retentionPolicyBoundRepository.findAll();
        Map<String, int[]> result = new HashMap<>();
        for (RetentionPolicyBound bound : bounds) {
            result.put(bound.getDataCategory().name(), new int[]{bound.getMinDays(), bound.getMaxDays()});
        }
        return result;
    }

    /**
     * Scheduled data retention job. Runs daily at 2 AM.
     * Currently operates in dry-run mode -- logs what it would process
     * but does not perform actual cleanup. The real cleanup calls to
     * UserAnonymizationService, notification cleanup, etc. will be wired in later.
     */
    @Scheduled(cron = "0 0 2 * * *")
    @Transactional(readOnly = true)
    public void executeRetentionPolicies() {
        log.info("Data retention job started");

        List<RetentionPolicy> systemDefaults = retentionPolicyRepository.findByClientIdIsNull();
        log.info("Loaded {} system default retention policies", systemDefaults.size());

        for (RetentionPolicy policy : systemDefaults) {
            log.info("DRY-RUN: Would process category={}, retentionDays={}, action={}",
                    policy.getDataCategory(), policy.getRetentionDays(), policy.getActionType());
        }

        log.info("Data retention job completed (dry-run mode)");
    }

    private DataCategory parseDataCategory(String dataCategory) {
        try {
            return DataCategory.valueOf(dataCategory.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessRuleException("Invalid data category: " + dataCategory
                    + ". Valid categories: " + List.of(DataCategory.values()));
        }
    }

    private void validateRetentionBounds(DataCategory category, int retentionDays) {
        RetentionPolicyBound bound = retentionPolicyBoundRepository.findByDataCategory(category)
                .orElseThrow(() -> new BusinessRuleException(
                        "No retention bounds configured for category: " + category));

        if (retentionDays < bound.getMinDays() || retentionDays > bound.getMaxDays()) {
            throw new BusinessRuleException(String.format(
                    "Retention days %d for %s must be between %d and %d",
                    retentionDays, category, bound.getMinDays(), bound.getMaxDays()));
        }
    }
}
